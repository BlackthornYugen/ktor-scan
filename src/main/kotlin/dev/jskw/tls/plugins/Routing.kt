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
import java.util.*

@KtorExperimentalLocationsAPI
fun Application.configureRouting() {
    install(AutoHeadResponse)
    install(Locations) {
    }

    routing {
        get("/") {
            call.respondText("Welcome to my TLS scan API!")
        }
        post<Scan> { scanArguments ->
            val file = File("cache/${scanArguments.host}.html")
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
                call.respond(HttpStatusCode(420, "Enhance your calm"));
                return@post
            }

            call.respond(HttpStatusCode.Accepted);
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


/**
 * Scan arguments for sslscan
 *
 * @property host the host to scan
 * @property port the port to scan
 * @property heartbleed check for OpenSSL Heartbleed (CVE-2014-0160)
 * @property renegotiation check for TLS renegotiation
 */
@KtorExperimentalLocationsAPI
@Location("/scan/{host}")
class Scan(val host: String, val port: Int = 443, val heartbleed: Boolean = true, val renegotiation: Boolean = true)

/**
 * Run a scan and save data to provided stream.
 *
 * @return two jobs that need to be run on different threads.
 */
fun Scan.run(streamToDisk: FileOutputStream): Array<Runnable> {
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

    val scanProcess = scanProcessBuilder.start()
    val formatProcess = formatProcessBuilder.start()

    return arrayOf(
        // Job that will move data from scan process to format process
        Runnable {
            formatProcess.outputStream.use { streamToFormatProcess ->
                scanProcess.inputStream.transferTo(streamToFormatProcess)
            }
        },
        // Job that will move data from format process to disk
        Runnable {
            formatProcess.inputStream.transferTo(streamToDisk)
        }
    )
}