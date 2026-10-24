package com.tlw.photodokan.station

import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager

/**
 * Talks to the Print Station over HTTPS, trusting only the certificate with the paired
 * fingerprint, whatever address the PC has today. Plain `java.net`, the same on the phone and in
 * the PC's tests.
 */
class StationClient(
    private val timeoutMillis: Int = 4_000,
    /** How long the PC may take to print a sheet before answering. */
    private val printTimeoutMillis: Int = 120_000,
) : StationConnector {

    override fun pair(invite: PairingInvite, deviceName: String): StationLink {
        val body = json.encodeToString(PairRequest(invite.code, deviceName)).toByteArray()
        val (host, response) = firstAnswer(invite.hosts, invite.port, invite.fingerprint, NO_ANSWER) { host ->
            request(host, invite.port, invite.fingerprint, "POST", StationProtocol.PAIR_PATH, body = body)
        }
        val paired = json.decodeFromString<PairResponse>(response.expect(HttpURLConnection.HTTP_OK))
        return StationLink(paired.stationName, listOf(host) + (invite.hosts - host), invite.port, invite.fingerprint, paired.deviceId, paired.token)
    }

    override fun check(link: StationLink): PairedHello {
        val (_, response) = firstAnswer(link.hosts, link.port, link.fingerprint, NO_ANSWER) { host ->
            request(host, link.port, link.fingerprint, "GET", StationProtocol.PAIRED_PATH, token = link.token)
        }
        return json.decodeFromString(response.expect(HttpURLConnection.HTTP_OK))
    }

    override fun send(link: StationLink, pdf: ByteArray, name: String): JobResult {
        val path = StationProtocol.JOBS_PATH + "?" + StationProtocol.JOB_NAME + "=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        // Once the PC has the sheet, trying another address could print it twice.
        val (_, response) = firstAnswer(link.hosts, link.port, link.fingerprint, NO_ANSWER_TO_SHEET) { host ->
            request(host, link.port, link.fingerprint, "POST", path, body = pdf, contentType = "application/pdf", token = link.token, readTimeout = printTimeoutMillis)
        }
        return json.decodeFromString(response.expect(HttpURLConnection.HTTP_OK))
    }

    private class Response(val code: Int, val body: String)

    /** The request reached the PC, which then didn't answer in time; it may still be acting on it. */
    private class SentWithoutAnswer(cause: IOException) : IOException(cause)

    /**
     * The first of [hosts] that answers, with its answer. Hosts that can't be reached are skipped,
     * but a request the PC received and didn't answer stops there, with [noAnswer].
     */
    private fun firstAnswer(hosts: List<String>, port: Int, fingerprint: String, noAnswer: String, call: (String) -> Response): Pair<String, Response> {
        var wrongPc = false
        for (host in hosts) {
            try {
                return host to call(host)
            } catch (e: SentWithoutAnswer) {
                throw StationException(noAnswer, StationException.Reason.NoAnswer)
            } catch (e: SSLHandshakeException) {
                wrongPc = true // something answered, but not the paired PC: try the next address
            } catch (e: IOException) {
                // not there: try the next address
            }
        }
        val message = if (wrongPc) {
            "Another device answered instead of the PC. Pair this phone with the PC again."
        } else {
            "The PC can't be reached. Check that Photo Dokan is open on it and that this phone is on the shop's Wi-Fi."
        }
        throw StationException(message, StationException.Reason.Unreachable)
    }

    private fun Response.expect(code: Int): String {
        if (this.code == code) return body
        val message = runCatching { json.decodeFromString<StationError>(body).message }.getOrNull() ?: "The PC answered with error ${this.code}."
        val reason = if (this.code == HttpURLConnection.HTTP_UNAUTHORIZED) StationException.Reason.NotPaired else StationException.Reason.Refused
        throw StationException(message, reason)
    }

    private fun request(
        host: String,
        port: Int,
        fingerprint: String,
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        token: String? = null,
        readTimeout: Int = timeoutMillis,
    ): Response {
        val connection = URI("https://$host:$port$path").toURL().openConnection() as HttpsURLConnection
        try {
            connection.sslSocketFactory = pinnedTo(fingerprint).socketFactory
            // The certificate's fingerprint is the check; the PC's address may change.
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = readTimeout
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty(StationProtocol.AUTHORIZATION, StationProtocol.BEARER + it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                if (body != null) throw SentWithoutAnswer(e) else throw e
            }
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            return Response(code, stream?.use { it.readBytes().decodeToString() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private const val NO_ANSWER = "The PC stopped answering. Try again."
        private const val NO_ANSWER_TO_SHEET =
            "The sheet reached the PC, but the PC didn't say whether it printed. Check the PC's printer before sending it again."

        /** TLS that accepts only the certificate whose SHA-256 is [fingerprint]. */
        fun pinnedTo(fingerprint: String): SSLContext {
            val pinned = object : X509TrustManager {
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                    val leaf = chain.firstOrNull() ?: throw CertificateException("No certificate")
                    val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
                    if (!MessageDigest.isEqual(actual.toByteArray(), fingerprint.toByteArray())) throw CertificateException("Not the paired PC")
                }

                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
                    throw CertificateException("The phone never accepts connections")

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinned), null) }
        }
    }
}
