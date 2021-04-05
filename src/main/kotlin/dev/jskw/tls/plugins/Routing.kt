package dev.jskw.tls.plugins

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileLock
import java.time.Duration
import java.time.LocalDateTime


fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(Locations) {
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get<Scan> { it ->
            val file = File("cache/${it.name}.txt")
            if(false) {

                if (file.totalSpace > 0) {
                    call.respond(HttpStatusCode.TooManyRequests)
                } else {
                    call.respondText("Location: name=${it.name}, arg1=${it.arg1}, arg2=${it.arg2}")
                }
            } else {
                println("Main               : I'm working in thread ${Thread.currentThread().name}")
                var fileOutputStream: FileOutputStream? = null
                var fileLock : FileLock? = null
                val getLock = launch(Dispatchers.IO) { // will get dispatched to DefaultDispatcher
                    fileOutputStream = FileOutputStream(file, true);

                    try {
                        fileLock = try {
                            fileOutputStream!!.channel.tryLock()
                        } catch (exception: Exception) {
//                            exception.toString().let { msg -> call.respondText(msg, status = HttpStatusCode.BadRequest) }
//                            call.application.environment.log.error("grrr", exception)
                            throw exception;
                        }
                    call.application.environment.log.debug("Lock aquired for ${file.absolutePath}.")

//                    val bytes = FileInputStream(file).use { it.readBytes() }
//                    println("Default               : I'm working in thread ${Thread.currentThread().name}")
//                    println(bytes.decodeToString())
                    } catch (exception: Exception) {
                        return@launch
                    }
                }

                getLock.join()
                if (fileLock?.isValid != true) {
                    println("${Thread.currentThread().name} found a lock.")
                    call.respond(HttpStatusCode(425, "Too Early"));
                    return@get
                }

                call.respond(HttpStatusCode.Accepted);
                val writeData = launch(Dispatchers.IO) {
                    delay(Duration.ofSeconds(3))
                    println("Before use lock: ${fileLock?.isValid}")
                    fileOutputStream.use {
                        it?.write("${LocalDateTime.now()}\n".encodeToByteArray())
                        println("Use lock: ${fileLock?.isValid}")
                        if (fileLock?.isValid == true) {
                            fileLock!!.release()
                            println("${Thread.currentThread().name} released a lock.")
                            call.application.environment.log.debug("Lock released for ${file.absolutePath}.")
                        } else {
                            println("${Thread.currentThread().name} said lock was invalid.")
                        }
                    }
                }
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
