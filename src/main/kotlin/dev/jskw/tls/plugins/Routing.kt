package dev.jskw.tls.plugins

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileLock


fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(Locations) {
    }

    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get<Scan> { scanArguments ->
            val file = File("cache/${scanArguments.name}.html")
            if(false) {
                call.respondText("Location: name=${scanArguments.name}, arg1=${scanArguments.arg1}, arg2=${scanArguments.arg2}\n")
            } else {
                var fileOutputStream: FileOutputStream? = null
                var fileLock : FileLock? = null
                val getLock = launch(Dispatchers.IO) { // will get dispatched to DefaultDispatcher
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

                getLock.join()
                if (fileLock?.isValid != true) {
                    call.respond(HttpStatusCode(420, "Enhance your calm"));
                    return@get
                }

                call.respond(HttpStatusCode.Accepted);
                launch(Dispatchers.IO) {
                    call.application.environment.log.debug("${Thread.currentThread().name}: Before use lock: ${fileLock?.isValid}")
                    fileOutputStream.use { stream ->
                        call.application.environment.log.debug("${Thread.currentThread().name}: Start scan")
                        call.application.environment.log.debug("${Thread.currentThread().name}: Use lock: ${fileLock?.isValid}")
                        val process = scanArguments.run(file.parentFile, stream!!)
                        process[1].outputStream.use {
                            call.application.environment.log.debug("${Thread.currentThread().name}: Waiting 0")
                            process[0].inputStream.transferTo(it);
                            call.application.environment.log.debug("${Thread.currentThread().name}: Finished")
                        }
                        launch(Dispatchers.IO) {
                            call.application.environment.log.debug("${Thread.currentThread().name}: Waiting 1")
                            process[1].inputStream.transferTo(stream);
                            call.application.environment.log.debug("${Thread.currentThread().name}: Finished")
                        }
                        call.application.environment.log.debug("${Thread.currentThread().name}: Waiting Scan")
                        process[0].waitFor()
                        call.application.environment.log.debug("${Thread.currentThread().name}: Finished scan")
                        process[1].waitFor()
                        call.application.environment.log.debug("${Thread.currentThread().name}: Finished format")
                    }
                }
            }
        }
    }
}

@Location("/scan/{name}")
class Scan(val name: String, val arg1: Int = 42, val arg2: String = "0")

fun Scan.run(workingDir: File, fileOutputStream: FileOutputStream): Array<Process> {
    val scanCommand = arrayOf("../sslscan", this.name)
    val formatCommand = arrayOf("aha", "--black")

    val scanProcessBuilder = ProcessBuilder(*scanCommand).directory(workingDir)
    val formatProcessBuilder = ProcessBuilder(*formatCommand).directory(workingDir)

    return arrayOf(scanProcessBuilder.start(), formatProcessBuilder.start())
}