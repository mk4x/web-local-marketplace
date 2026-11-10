@file:Suppress("TooManyFunctions")

package com.g7.graphql

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.g7.database.jooq.enums.ListingCondition
import com.g7.database.jooq.enums.ListingKind
import com.g7.database.jooq.enums.ListingStatus
import com.g7.db.StaleVersionException
import com.g7.listing.Listing
import com.g7.listing.ListingEdit
import com.g7.listing.ListingHit
import com.g7.listing.NewListing
import com.g7.listing.SearchFilter
import com.g7.listing.createListings
import com.g7.listing.editListings
import com.g7.listing.findListings
import com.g7.listing.listingHistory
import com.g7.listing.searchListings
import com.g7.user.NewUser
import com.g7.user.ProfileEdit
import com.g7.user.UserProfile
import com.g7.user.createUsers
import com.g7.user.editProfiles
import com.g7.user.findUsers
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring.newTypeWiring
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture

private const val MAX_LIMIT = 100
private const val USERS = "users"
private const val LISTINGS = "listings"

private val log = LoggerFactory.getLogger("graphql")

private val mapper = JsonMapper()
    .registerModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private data class GraphQlRequest(
    val query: String,
    val variables: Map<String, Any?>? = null,
    val operationName: String? = null,
)

fun Application.graphQl(db: DSLContext) {
    val engine = GraphQL.newGraphQL(schema(db))
        .defaultDataFetcherExceptionHandler(errors)
        .build()
    routing {
        post("/graphql") {
            val request = parse(call.receiveText())
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val result = withContext(Dispatchers.IO) {
                engine.execute(request.toInput(db))
            }
            call.respondText(
                mapper.writeValueAsString(result.toSpecification()),
                ContentType.Application.Json,
            )
        }
    }
}

private fun parse(body: String) =
    runCatching { mapper.readValue<GraphQlRequest>(body) }.getOrNull()

private fun GraphQlRequest.toInput(db: DSLContext): ExecutionInput =
    ExecutionInput.newExecutionInput()
        .query(query)
        .operationName(operationName)
        .variables(variables.orEmpty())
        .dataLoaderRegistry(loaders(db))
        .build()

private fun loaders(db: DSLContext): DataLoaderRegistry =
    DataLoaderRegistry.newRegistry()
        .register(USERS, loader({ db.findUsers(it) }) { it.id })
        .register(LISTINGS, loader({ db.findListings(it) }) { it.id })
        .build()

private fun <V : Any> loader(
    find: (List<UUID>) -> List<V>,
    idOf: (V) -> UUID,
): DataLoader<UUID, V> {
    val batchLoader = org.dataloader.MappedBatchLoader<UUID, V> { ids ->
        val values = find(ids.toList())
        CompletableFuture.completedFuture(values.associateBy(idOf))
    }

    return DataLoaderFactory.newMappedDataLoader(batchLoader)
}

private fun schema(db: DSLContext): GraphQLSchema {
    val sdl = checkNotNull(
        GraphQlRequest::class.java.getResource("/schema.graphqls"),
    ).readText()
    return SchemaGenerator()
        .makeExecutableSchema(SchemaParser().parse(sdl), wiring(db))
}

private fun wiring(db: DSLContext): RuntimeWiring =
    RuntimeWiring.newRuntimeWiring()
        .type(
            newTypeWiring("ListingKind")
                .enumValues { ListingKind.valueOf(it) },
        )
        .type(
            newTypeWiring("ListingStatus")
                .enumValues { ListingStatus.valueOf(it) },
        )
        .type(
            newTypeWiring("ListingCondition")
                .enumValues { ListingCondition.valueOf(it) },
        )
        .type("Query") {
            it.dataFetcher("user") { env ->
                env.load<UserProfile>(USERS, env.id())
            }.dataFetcher("listing") { env ->
                env.load<Listing>(LISTINGS, env.id())
            }.dataFetcher("searchListings") { env ->
                db.searchListings(env.input<SearchFilter>("filter").capped())
            }.dataFetcher("listingHistory") { env ->
                db.listingHistory(env.id())
            }
        }
        .type("Listing") {
            it.dataFetcher("seller") { env ->
                val listing = checkNotNull(env.getSource<Listing>())
                env.load<UserProfile>(USERS, listing.sellerId)
            }
        }
        .type("ListingHit") {
            it.dataFetcher("listing") { env ->
                val hit = checkNotNull(env.getSource<ListingHit>())
                env.load<Listing>(LISTINGS, hit.id)
            }
        }
        .type("Mutation") {
            it.dataFetcher("createUser") { env ->
                db.createUser(env.input())
            }.dataFetcher("editProfile") { env ->
                db.editProfile(env.input())
            }.dataFetcher("createListing") { env ->
                db.createListing(env.input())
            }.dataFetcher("editListing") { env ->
                db.editListing(env.input())
            }
        }
        .build()

private fun <V> DataFetchingEnvironment.load(
    name: String,
    id: UUID,
): CompletableFuture<V> =
    checkNotNull(getDataLoader<UUID, V>(name)).load(id)

private fun DataFetchingEnvironment.id(): UUID =
    UUID.fromString(checkNotNull(getArgument<String>("id")))

private inline fun <reified T> DataFetchingEnvironment.input(
    name: String = "input",
): T {
    val value: Any = checkNotNull(getArgument(name))
    return mapper.convertValue(value, T::class.java)
}

private fun SearchFilter.capped() =
    copy(limit = limit.coerceIn(1, MAX_LIMIT), offset = offset.coerceAtLeast(0))

private fun DSLContext.createUser(user: NewUser): UserProfile {
    createUsers(listOf(user))
    return findUsers(listOf(user.id)).single()
}

private fun DSLContext.editProfile(edit: ProfileEdit): UserProfile {
    editProfiles(listOf(edit))
    return findUsers(listOf(edit.userId)).single()
}

private fun DSLContext.createListing(listing: NewListing): Listing {
    createListings(listOf(listing))
    return findListings(listOf(listing.id)).single()
}

private fun DSLContext.editListing(edit: ListingEdit): Listing? {
    editListings(listOf(edit))
    return findListings(listOf(edit.listingId)).firstOrNull()
}

private val errors = DataFetcherExceptionHandler { params ->
    val cause = params.exception
    val message = (cause as? StaleVersionException)?.message
        ?: "Internal error".also { log.error("Data fetching failed", cause) }
    val error = GraphqlErrorBuilder
        .newError(params.dataFetchingEnvironment)
        .message(message)
        .build()
    CompletableFuture.completedFuture(
        DataFetcherExceptionHandlerResult.newResult(error).build(),
    )
}
