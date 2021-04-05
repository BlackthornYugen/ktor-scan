package dev.jskw.tls.plugins

import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.locations.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.channels.FileLock

import java.nio.channels.FileChannel

import java.io.FileOutputStream
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope


fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(Locations) {
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get<Scan> {
            val file = File("cache/${it.name}")
            if(file.exists()) {
                if (file.totalSpace > 0) {
                    call.respond(HttpStatusCode.TooManyRequests);
                }
                call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
            } else {
                call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}",
                    status = HttpStatusCode.Accepted)

            }
        }
        // Register nested routes
        get<Type.Edit> {
            call.respondText("Inside $it")
        }
        get<Type.List> {
            call.respondText("Inside $it")
        }
    }
}

@Location("/scan/{name}")
class Scan(val name: String, val arg1: Int = 42, val arg2: String = "0")

@Location("/type/{name}")
data class Type(val name: String) {
    @Location("/edit")
    data class Edit(val type: Type)

    @Location("/list/{page}")
    data class List(val type: Type, val page: Int)
}
