package com.tlw.photodokan.station

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

/**
 * The Print Station's own HTTPS certificate, made once and kept in [file]. It is self-signed: no
 * authority vouches for a shop PC, so instead each phone remembers its [fingerprint] when paired
 * and trusts nothing else. Keeping the same certificate for years keeps paired phones working.
 */
class StationCertificate private constructor(val keyStore: KeyStore) {

    val certificate: X509Certificate get() = keyStore.getCertificate(ALIAS) as X509Certificate

    /** SHA-256 of the certificate, in hex: what a phone checks the PC by. */
    val fingerprint: String get() = fingerprintOf(certificate)

    companion object {
        const val ALIAS = "photo-dokan-station"

        /**
         * Guards the key file in the app's own data folder. The key needs no stronger secret: anyone
         * who can read that folder can already run the app.
         */
        internal const val PASSWORD = "photo-dokan-station"

        /** About 30 years: phones pin the certificate itself, so it never needs renewing. */
        private const val DAYS_VALID = 30L * 365

        /** The certificate in [file], made there first if it isn't there or can't be read. */
        fun loadOrCreate(file: File): StationCertificate {
            if (file.isFile) {
                runCatching {
                    val store = KeyStore.getInstance("PKCS12")
                    file.inputStream().use { store.load(it, PASSWORD.toCharArray()) }
                    if (store.isKeyEntry(ALIAS)) return StationCertificate(store)
                }
            }
            val store = buildKeyStore {
                certificate(ALIAS) {
                    password = PASSWORD
                    daysValid = DAYS_VALID
                    subject = X500Principal("CN=Photo Dokan Print Station, O=Photo Dokan")
                    domains = listOf("photo-dokan.local")
                }
            }
            file.parentFile?.mkdirs()
            store.saveToFile(file, PASSWORD)
            return StationCertificate(store)
        }

        fun fingerprintOf(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }
    }
}
