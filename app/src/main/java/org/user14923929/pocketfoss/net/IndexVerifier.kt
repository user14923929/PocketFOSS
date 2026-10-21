package org.user14923929.pocketfoss.net

import android.content.Context
import android.util.JsonReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.jar.JarFile

/**
 * Verifies an F-Droid v2 repository: entry.jar is a JAR signed with the repo key and contains
 * entry.json, which pins the SHA-256 of index-v2.json. Trust chain:
 *   pinned fingerprint -> entry.jar signature -> entry.json -> index SHA-256 -> index-v2.json
 */
object IndexVerifier {

    class VerifyException(message: String) : IOException(message)

    class Entry(
        val timestamp: Long,
        val indexName: String,
        val sha256: String,
        val size: Long
    )

    /** Downloads and verifies entry.jar of [repoUrl]; [fingerprint] is the pinned signing-cert SHA-256. */
    fun verifyEntry(context: Context, repoUrl: String, fingerprint: String): Entry {
        val jar = File.createTempFile("entry", ".jar", context.cacheDir)
        try {
            try {
                Net.download(repoUrl.trimEnd('/') + "/entry.jar", jar)
            } catch (e: IOException) {
                throw VerifyException("cannot fetch entry.jar (v2 index required): ${e.message}")
            }
            JarFile(jar, true).use { jf ->
                val je = jf.getJarEntry("entry.json") ?: throw VerifyException("entry.json missing")
                // The stream must be read to the end: signature errors surface at EOF.
                val bytes = jf.getInputStream(je).use { it.readBytes() }
                val certs: Array<Certificate>? = je.certificates
                if (certs == null || certs.isEmpty()) throw VerifyException("entry.jar is not signed")
                val pinned = fingerprint.replace(":", "").replace(" ", "")
                if (certs.none { fingerprintOf(it).equals(pinned, ignoreCase = true) }) {
                    throw VerifyException("untrusted signing key: ${fingerprintOf(certs[0])}")
                }
                return parse(bytes)
            }
        } catch (e: SecurityException) {
            throw VerifyException("bad signature: ${e.message}")
        } finally {
            jar.delete()
        }
    }

    private fun fingerprintOf(cert: Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    private fun parse(bytes: ByteArray): Entry {
        var ts = 0L
        var name = ""
        var sha = ""
        var size = 0L
        JsonReader(InputStreamReader(ByteArrayInputStream(bytes), "UTF-8")).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "timestamp" -> ts = r.nextLong()
                    "index" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "name" -> name = r.nextString()
                                "sha256" -> sha = r.nextString()
                                "size" -> size = r.nextLong()
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        if (sha.isEmpty() || !name.startsWith("/") || name.contains("..") || name.contains("://")) {
            throw VerifyException("entry.json has no valid index info")
        }
        return Entry(ts, name, sha, size)
    }
}
