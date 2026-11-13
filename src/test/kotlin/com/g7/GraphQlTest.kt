package com.g7

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.g7.TestDb
import com.g7.graphql.graphQl
import com.g7.listing.NewListing
import com.g7.listing.createListings
import com.g7.user.NewUser
import com.g7.user.createUsers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jooq.DSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val CREATE_USER = $$"""
mutation($input: NewUserInput!) {
  createUser(input: $input) { id email displayName }
}
"""

private const val CREATE_LISTING = $$"""
mutation($input: NewListingInput!) {
  createListing(input: $input) {
    id title status kind price currency negotiable tags delivery version
  }
}
"""

private const val EDIT_LISTING = $$"""
mutation($input: ListingEditInput!) {
  editListing(input: $input) { version title }
}
"""

private const val LISTING = $$"""
query($id: ID!) { listing(id: $id) { id title sellerName version } }
"""

private const val ALIASED = $$"""
query($a: ID!, $b: ID!) {
  a: listing(id: $a) { title }
  b: listing(id: $b) { title }
}
"""

private const val NESTED = $$"""
query($filter: SearchFilterInput!) {
  searchListings(filter: $filter) {
    listing { title seller { displayName } }
  }
}
"""

private const val SEARCH = $$"""
query($filter: SearchFilterInput!) {
  searchListings(filter: $filter) { id title price distanceM }
}
"""

class GraphQlTest : DbTest() {
    private val mapper = jacksonObjectMapper()

    @Suppress("MemberNameEqualsClassName")
    private fun graphQlTest(
        dsl: DSLContext = db,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        environment { config = MapApplicationConfig() }
        application { graphQl(dsl) }
        block()
    }

    private suspend fun ApplicationTestBuilder.raw(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
    ): String =
        client
            .post("/graphql") {
                contentType(ContentType.Application.Json)
                setBody(
                    mapper.writeValueAsString(
                        mapOf("query" to query, "variables" to variables),
                    ),
                )
            }.bodyAsText()

    private suspend fun ApplicationTestBuilder.gql(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
    ): JsonNode = mapper.readTree(raw(query, variables))

    private fun strings(node: JsonNode) = node.map { it.asText() }

    private suspend fun ApplicationTestBuilder.seller(): String =
        gql(
            CREATE_USER,
            mapOf("input" to newUser("s@example.com", "Sara")),
        ).at("/data/createUser/id").asText()

    private fun newUser(
        email: String,
        name: String,
    ) = mapOf("email" to email, "displayName" to name)

    private fun search(vararg filter: Pair<String, Any?>) =
        mapOf("filter" to mapOf("lon" to 10.39, "lat" to 55.40) + filter)

    private fun listingInput(
        sellerId: String,
        title: String = "Red bicycle",
    ) = mapOf(
        "sellerId" to sellerId,
        "title" to title,
        "category" to "vehicles.bikes",
        "lon" to 10.40,
        "lat" to 55.39,
        "city" to "Odense",
        "price" to "1500.00",
    )

    @Test
    fun `listings round trip through mutations and queries`() =
        graphQlTest {
            val sellerId = seller()
            val input =
                listingInput(sellerId) +
                    mapOf(
                        "tags" to listOf("vintage"),
                        "delivery" to listOf("pickup", "shipping"),
                        "negotiable" to null,
                    )

            val created = gql(CREATE_LISTING, mapOf("input" to input))

            val listing = created.at("/data/createListing")
            assertEquals("Red bicycle", listing.path("title").asText())
            assertEquals("active", listing.path("status").asText())
            assertEquals("sale", listing.path("kind").asText())
            assertEquals("1500.00", listing.path("price").asText())
            assertEquals("dkk", listing.path("currency").asText())
            assertFalse(listing.path("negotiable").asBoolean())
            assertEquals(listOf("vintage"), strings(listing.path("tags")))
            assertEquals(
                listOf("pickup", "shipping"),
                strings(listing.path("delivery")),
            )

            val id = listing.path("id").asText()
            val fetched = gql(LISTING, mapOf("id" to id)).at("/data/listing")
            assertEquals("Sara", fetched.path("sellerName").asText())

            val hits =
                gql(SEARCH, search("text" to "bicycle"))
                    .at("/data/searchListings")
            assertEquals(listOf(id), hits.map { it.path("id").asText() })
            assertTrue(hits[0].path("distanceM").asDouble() > 0)
        }

    @Test
    fun `a stale edit is a graphql error and changes nothing`() =
        graphQlTest {
            val sellerId = seller()
            val id =
                gql(CREATE_LISTING, mapOf("input" to listingInput(sellerId)))
                    .at("/data/createListing/id")
                    .asText()

            fun edit(
                version: Int,
                title: String,
            ) = mapOf(
                "input" to
                    mapOf(
                        "listingId" to id,
                        "sellerId" to sellerId,
                        "expectedVersion" to version,
                        "reason" to "retitle",
                        "title" to title,
                    ),
            )

            val first = gql(EDIT_LISTING, edit(1, "Blue bicycle"))
            val stale = gql(EDIT_LISTING, edit(1, "Green bicycle"))

            assertEquals(2, first.at("/data/editListing/version").asInt())
            assertTrue(stale.at("/data/editListing").isNull)
            val message = stale.at("/errors/0/message").asText()
            assertTrue(message.contains("No row matched"))
            val current = gql(LISTING, mapOf("id" to id)).at("/data/listing")
            assertEquals("Blue bicycle", current.path("title").asText())
        }

    @Test
    fun `database errors never reach the client`() =
        graphQlTest {
            val user = mapOf("input" to newUser("dup@example.com", "Dup"))
            gql(CREATE_USER, user)

            val body = raw(CREATE_USER, user)

            assertEquals(
                "Internal error",
                mapper.readTree(body).at("/errors/0/message").asText(),
            )
            assertFalse(body.contains("insert", ignoreCase = true))
        }

    @Test
    fun `malformed bodies are rejected and bad queries are graphql errors`() =
        graphQlTest {
            val malformed = client.post("/graphql") { setBody("not json") }
            assertEquals(HttpStatusCode.BadRequest, malformed.status)

            val unknownField = gql("{ listing(id: \"x\") { nope } }")
            assertNotEquals("", unknownField.at("/errors/0/message").asText())
        }

    @Test
    fun `search never returns more than the page cap`() =
        graphQlTest {
            seedBicycles(101)

            val hits =
                gql(SEARCH, search("limit" to 1000))
                    .at("/data/searchListings")

            assertEquals(100, hits.size())
        }

    private fun seedBicycles(count: Int): List<NewListing> {
        val owner = NewUser("owner@example.com", "Owner")
        db.createUsers(listOf(owner))
        return (1..count)
            .map {
                NewListing(
                    sellerId = owner.id,
                    title = "Bicycle number $it",
                    category = "vehicles.bikes",
                    lon = 10.40,
                    lat = 55.39,
                    city = "Odense",
                )
            }.also { db.createListings(it) }
    }

    @Test
    fun `aliased lookups share one query`() {
        val (first, second) = seedBicycles(2)

        val queries =
            executions {
                graphQlTest(this) {
                    val ids = mapOf("a" to "${first.id}", "b" to "${second.id}")
                    val data = gql(ALIASED, ids).path("data")
                    val titles =
                        listOf("/a/title", "/b/title")
                            .map { data.at(it).asText() }
                    assertEquals(
                        setOf("Bicycle number 1", "Bicycle number 2"),
                        titles.toSet(),
                    )
                }
            }

        assertEquals(1, queries)
    }

    @Test
    fun `nested fields cost a constant number of queries`() {
        seedBicycles(5)

        val queries =
            executions {
                graphQlTest(this) {
                    val hits = gql(NESTED, search()).at("/data/searchListings")
                    assertEquals(5, hits.size())
                    val sellers =
                        hits.map {
                            it.at("/listing/seller/displayName").asText()
                        }
                    assertEquals(setOf("Owner"), sellers.toSet())
                }
            }

        assertEquals(3, queries)
    }
}
