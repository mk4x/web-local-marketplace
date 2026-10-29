import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val DEFAULT_PORT = 8080

fun main() {
    // Read PORT from the environment so Docker or cloud hosts can change it
    val port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        routing {
            // The API endpoint the React page calls
            get("/api/hello") {
                call.respondText(
                    """{"message":"Hello from Ktor"}""",
                    ContentType.Application.Json
                )
            }
        }
    }
        .start(wait = true)
}
