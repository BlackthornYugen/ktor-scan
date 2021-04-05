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
        post<Scan> { scanArguments ->
            val file = File("cache/${scanArguments.host}.html")
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
                return@post
            }

            call.respond(HttpStatusCode.Accepted);
            launch(Dispatchers.IO) {
                fileOutputStream.use { stream ->
                    call.application.environment.log.debug("${Thread.currentThread().name}: Start scan")

                    val process = scanArguments.run()
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


/**
 * Scan arguments for sslscan
 *
 * @property host the host to scan
 * @property port the port to scan
 * @property heartbleed check for OpenSSL Heartbleed (CVE-2014-0160)
 * @property renegotiation check for TLS renegotiation
 * @constructor Creates an empty group.
 */
@Location("/scan/{host}")
class Scan(val host: String, val port: Int = 443, val heartbleed: Boolean = true, val renegotiation: Boolean = true)


fun Scan.run(): Array<Process> {
    val scanCommand = mutableListOf("./sslscan")

    if (!heartbleed) {
        scanCommand.add("--no-heartbleed")
    }

    if (!renegotiation) {
        scanCommand.add("--no-renegotiation")
    }

    scanCommand.add("$host:$port")

    val formatCommand = arrayOf("aha", "--black", "--title", scanCommand.toString())

    println(scanCommand)
    val scanProcessBuilder = ProcessBuilder(*scanCommand.toTypedArray())
    val formatProcessBuilder = ProcessBuilder(*formatCommand)

    return arrayOf(scanProcessBuilder.start(), formatProcessBuilder.start())
}