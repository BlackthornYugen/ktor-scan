package dev.jskw.tls.plugins

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.application.*
import io.ktor.mustache.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.configureTemplating() {
    install(Mustache) {
        mustacheFactory = DefaultMustacheFactory("templates/mustache")
    }

    routing {
        get("/html-mustache") {
            call.respond(MustacheContent("index.hbs", mapOf("user" to MustacheUser(1, "user1"))))
        }
    }
}

data class MustacheUser(val id: Int, val name: String)
