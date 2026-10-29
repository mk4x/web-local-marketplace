package com.example

import io.ktor.server.routing

fun main() {
    // Read PORT from the environment so Docker or cloud hosts can change it
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        routing {
            // The API endpoint the React page calls
            get("/api/hello") {
                call.respondText(
                    """{"message":"Hello from Ktor"}""",
                    ContentType.Application.Json,
                )
            }

            // Serves src/main/resources/static/index.html at http://localhost:8080/
            staticResources("/", "static")
        }
    }.start(wait = true)
}
