package dev.jskw.tls

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.mustache.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileLock
import java.util.*

@KtorExperimentalLocationsAPI
fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(Locations)
    install(Mustache) { mustacheFactory = DefaultMustacheFactory("templates") }

    routing {
        get<Scan> { scanArguments ->
            call.respond(
                message = MustacheContent(
                    template = "index.hbs",
                    model = mapOf(
                        "title" to "Welcome!",
                        "scan" to scanArguments,
                    ),
                ),
            )
        }
        post<Scan> { scanArguments ->
            val file = File("cache/${scanArguments.host}.${scanArguments.port}.html")
            var fileOutputStream: FileOutputStream? = null
            var fileLock: FileLock? = null

            val getLockThread = launch(Dispatchers.IO) {
                fileOutputStream = FileOutputStream(file);

                try {
                    fileLock = try {
                        fileOutputStream!!.channel.tryLock()
                    } catch (exception: Exception) {
                        throw exception;
                    }
                    call.application.environment.log.trace("Lock aquired for ${file.absolutePath}.")
                } catch (exception: Exception) {
                    call.application.environment.log.trace("Failed to get lock: ", exception);
                    return@launch
                }
            }

            getLockThread.join()
            if (fileLock?.isValid != true) {
                call.respond(
                    status = HttpStatusCode(420, "Enhance your calm"),
                    message = MustacheContent(
                        template = "index.hbs",
                        model = mapOf(
                            "title" to "Please enhanse your calm.",
                            "scan" to scanArguments,
                        ),
                    ),
                )
                return@post
            }

            call.response.header("Location", "/scan/result/${scanArguments.host}/${scanArguments.port}")
            call.respond(
                status = HttpStatusCode.Accepted,
                message = MustacheContent(
                    template = "index.hbs",
                    model = mapOf("scan" to scanArguments),
                ),
            )
            val writeFileThread = launch(Dispatchers.IO) {
                fileOutputStream.use { streamToDisk ->
                    call.application.environment.log.debug("${Thread.currentThread().name}: Start scan")

                    val arrayOfRunnables = scanArguments.run(streamToDisk!!)

                    launch {
                        arrayOfRunnables[0].run()
                    }

                    arrayOfRunnables[1].run()
                }
            }
        }
    }
}
