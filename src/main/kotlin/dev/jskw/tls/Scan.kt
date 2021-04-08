package dev.jskw.tls

import io.ktor.locations.*
import java.io.FileOutputStream

/**
 * Scan arguments for sslscan
 *
 * @property host the host to scan
 * @property port the port to scan
 * --ssl2               Only check if SSLv2 is enabled
 * --ssl3               Only check if SSLv3 is enabled
 * --tls10              Only check TLSv1.0 ciphers
 * --tls11              Only check TLSv1.1 ciphers
 * --tls12              Only check TLSv1.2 ciphers
 * --tls13              Only check TLSv1.3 ciphers
 * --tlsall             Only check TLS ciphers (all versions)
 * --show-ciphers       Show supported client ciphers
 * --show-cipher-ids    Show cipher ids
 * --show-times         Show handhake times in milliseconds
 * --no-cipher-details  Disable EC curve names and EDH/RSA key lengths output
 * --no-ciphersuites    Do not check for supported ciphersuites
 * --no-compression     Do not check for TLS compression (CRIME)
 * --no-fallback        Do not check for TLS Fallback SCSV
 * --no-groups          Do not enumerate key exchange groups
 * @property noHeartbleed      Do not check for OpenSSL Heartbleed (CVE-2014-0160)
 * @property noRenegotiation   Do not check for TLS renegotiation
 * --show-sigs          Enumerate signature algorithms
 */
@KtorExperimentalLocationsAPI
@Location("/")
class Scan(
    val host: String = "scan.jskw.dev",
    val port: Int = 443,
    val noHeartbleed: Boolean = false,
    val noRenegotiation: Boolean = false
) {
    /**
     * Run a scan and save data to provided stream.
     *
     * @return two jobs that need to be run on different threads.
     */
    fun run(streamToDisk: FileOutputStream): Array<Runnable> {
        val scanCommand = mutableListOf("./sslscan")

        if (noHeartbleed) {
            scanCommand.add("--no-heartbleed")
        }

        if (noRenegotiation) {
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

    val form: Map<String, Array<Map<String, Any>>> = mapOf(
            "inputs" to arrayOf(mapOf(
                "name" to "host",
                "label" to "Scan Host: ",
                "type" to "text",
                "value" to host,
            ), mapOf(
                "name" to "port",
                "label" to "Scan Port: ",
                "type" to "number",
                "value" to port,
            )),
            "checkboxes" to arrayOf(mapOf(
                "name" to "noHeartbleed",
                "description" to "Do not check for OpenSSL Heartbleed (CVE-2014-0160)",
                "label" to "--no-heartbleed",
                "checked" to noHeartbleed,
            ), mapOf(
                "name" to "noRenegotiation",
                "description" to "Do not check for TLS renegotiation",
                "label" to "--no-renegotiation",
                "checked" to noRenegotiation,
            ))
        )
}