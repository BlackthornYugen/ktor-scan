package dev.jskw.tls

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.locations.*

@KtorExperimentalLocationsAPI
fun main() {
    embeddedServer(Netty, port = 8181, host = "0.0.0.0") {
        configureRouting()
    }.start(wait = true)
}
