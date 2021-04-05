package dev.jskw.tls

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import dev.jskw.tls.plugins.*

fun main() {
    embeddedServer(Netty, port = 8181, host = "0.0.0.0") {
        configureRouting()
        configureTemplating()
    }.start(wait = true)
}
