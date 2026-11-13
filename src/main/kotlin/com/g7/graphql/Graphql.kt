@file:Suppress("TooManyFunctions")

package com.g7.graphql

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
import graphql.ErrorClassification
import graphql.ErrorType
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.language.SourceLocation
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeRuntimeWiring
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
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.jooq.DSLContext
import org.jooq.EnumType
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CompletableFuture

private const val MAX_LIMIT = 100
private const val USERS = "users"
private const val LISTINGS = "listings"

private val log = LoggerFactory.getLogger("graphql")

private val mapper: ObjectMapper =
    JsonMapper()
        .registerModule(
            KotlinModule
                .Builder()
                .configure(KotlinFeature.NullIsSameAsDefault, true)
                .build(),
        ).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private data class GraphQlRequest(
    val query: String,
    val variables: Map<String, Any?>? = null,
    val operationName: String? = null,
)

fun Application.graphQl(db: DSLContext) {
    val engine =
        GraphQL
            .newGraphQL(schema(db))
            .defaultDataFetcherExceptionHandler(errors)
            .build()
    routing {
        post("/graphql") {
            val request =
                parse(call.receiveText())
                    ?: return@post call.respond(HttpStatusCode.BadRequest)
            val result =
                withContext(Dispatchers.IO) {
                    engine.execute(request.toInput(db))
                }
            call.respondText(
                mapper.writeValueAsString(result.toSpecification()),
                ContentType.Application.Json,
            )
        }
    }
}

private fun parse(body: String): GraphQlRequest? =
    runCatching { mapper.readValue(body, GraphQlRequest::class.java) }
        .getOrNull()

private fun GraphQlRequest.toInput(db: DSLContext): ExecutionInput =
    ExecutionInput
        .newExecutionInput()
        .query(query)
        .operationName(operationName)
        .variables(variables.orEmpty())
        .dataLoaderRegistry(loaders(db))
        .build()

private fun loaders(db: DSLContext): DataLoaderRegistry {
    val users =
        DataLoaderFactory.newMappedDataLoader<UUID, UserProfile> {
            val found =
                db.findUsers(it.toList()).associateBy { user ->
                    user.id
                }
            CompletableFuture.completedFuture(found)
        }
    val listings =
        DataLoaderFactory.newMappedDataLoader<UUID, Listing> {
            val found = db.findListings(it.toList()).associateBy { l -> l.id }
            CompletableFuture.completedFuture(found)
        }
    return DataLoaderRegistry
        .newRegistry()
        .register(USERS, users)
        .register(LISTINGS, listings)
        .build()
}

private fun schema(db: DSLContext): GraphQLSchema {
    val sdl =
        checkNotNull(
            GraphQlRequest::class.java.getResource("/schema.graphqls"),
        ).readText()
    return SchemaGenerator()
        .makeExecutableSchema(SchemaParser().parse(sdl), wiring(db))
}

private fun wiring(db: DSLContext): RuntimeWiring =
    RuntimeWiring
        .newRuntimeWiring()
        .type(byLiteral<ListingKind>())
        .type(byLiteral<ListingStatus>())
        .type(byLiteral<ListingCondition>())
        .type("Query") { queries(db, it) }
        .type("Mutation") { mutations(db, it) }
        .type("Listing") { nestedSeller(it) }
        .type("ListingHit") { nestedListing(it) }
        .build()

private inline fun <reified E> byLiteral(): TypeRuntimeWiring.Builder
        where E : Enum<E>, E : EnumType =
    newTypeWiring(E::class.java.simpleName).enumValues { literal ->
        enumValues<E>().first { it.literal == literal }
    }

private fun queries(
    db: DSLContext,
    type: TypeRuntimeWiring.Builder,
): TypeRuntimeWiring.Builder =
    type
        .dataFetcher("user") { env -> env.user(env.id()) }
        .dataFetcher("listing") { env -> env.listing(env.id()) }
        .dataFetcher("searchListings") { env ->
            val filter = env.input(SearchFilter::class.java, "filter")
            db.searchListings(filter.capped())
        }.dataFetcher("listingHistory") { env -> db.listingHistory(env.id()) }

private fun mutations(
    db: DSLContext,
    type: TypeRuntimeWiring.Builder,
): TypeRuntimeWiring.Builder =
    type
        .dataFetcher("createUser") { env ->
            db.createUser(env.input(NewUser::class.java))
        }.dataFetcher("editProfile") { env ->
            db.editProfile(env.input(ProfileEdit::class.java))
        }.dataFetcher("createListing") { env ->
            db.createListing(env.input(NewListing::class.java))
        }.dataFetcher("editListing") { env ->
            db.editListing(env.input(ListingEdit::class.java))
        }

private fun nestedSeller(
    type: TypeRuntimeWiring.Builder,
): TypeRuntimeWiring.Builder =
    type.dataFetcher("seller") { env ->
        val listing = checkNotNull(env.getSource<Listing>())
        env.user(listing.sellerId)
    }

private fun nestedListing(
    type: TypeRuntimeWiring.Builder,
): TypeRuntimeWiring.Builder =
    type.dataFetcher("listing") { env ->
        val hit = checkNotNull(env.getSource<ListingHit>())
        env.listing(hit.id)
    }

private fun DataFetchingEnvironment.user(
    id: UUID,
): CompletableFuture<UserProfile> =
    checkNotNull(getDataLoader<UUID, UserProfile>(USERS)).load(id)

private fun DataFetchingEnvironment.listing(
    id: UUID,
): CompletableFuture<Listing> =
    checkNotNull(getDataLoader<UUID, Listing>(LISTINGS)).load(id)

private fun DataFetchingEnvironment.id(): UUID =
    UUID.fromString(checkNotNull(getArgument<String>("id")))

private fun <T> DataFetchingEnvironment.input(
    type: Class<T>,
    name: String = "input",
): T = mapper.convertValue(getArgument<Any>(name), type)

private fun SearchFilter.capped() =
    copy(
        limit = limit.coerceIn(1, MAX_LIMIT),
        offset = offset.coerceAtLeast(0),
    )

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

private class ClientError(
    private val text: String,
    private val where: List<Any>,
) : GraphQLError {
    override fun getMessage(): String = text

    override fun getLocations(): List<SourceLocation>? = null

    override fun getErrorType(): ErrorClassification =
        ErrorType.DataFetchingException

    override fun getPath(): List<Any> = where
}

private val errors =
    DataFetcherExceptionHandler { params ->
        val cause = params.exception
        val text =
            (cause as? StaleVersionException)?.message
                ?: "Internal error".also { log.error("Fetch failed", cause) }

        val error = ClientError(text, params.path.toList())

        CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult(error).build(),
        )
    }
