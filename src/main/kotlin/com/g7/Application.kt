package com.g7

import com.g7.db.Database
import com.g7.graphql.graphQl
import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.flywaydb.core.Flyway

private const val DEFAULT_PORT = 8080

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val database = Database()
    Flyway.configure()
        .dataSource(database.dataSource)
        .load()
        .migrate()

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        routing {
            graphQl(database.dsl)
            // The API endpoint the React page calls
            get("/api/hello") {
                call.respondText(
                    """{"message":"Hello from Ktor"}""",
                    ContentType.Application.Json,
                )
            }
        }
    }.start(wait = true)
}
