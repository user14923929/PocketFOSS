package org.user14923929.pocketfoss.net

import android.content.Context
import org.user14923929.pocketfoss.data.AppEntry
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Net {
    private const val UA = "PocketFOSS/0.1"

    /** Opens a connection and returns it only on HTTP 200. Caller must disconnect. */
    fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", UA)
        c.connect()
        if (c.responseCode != HttpURLConnection.HTTP_OK) {
            val code = c.responseCode
            c.disconnect()
            throw IOException("HTTP $code")
        }
        return c
    }

    /** Downloads [url] into [dest] and returns the lowercase hex SHA-256 of the received bytes. */
    fun download(
        url: String,
        dest: File,
        totalHint: Long = 0,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        val c = open(url)
        try {
            val total = if (totalHint > 0) totalHint else c.contentLength.toLong()
            c.inputStream.use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Downloads the APK to the cache dir and verifies its SHA-256 against the index. */
    fun downloadApk(context: Context, app: AppEntry, onProgress: (done: Long, total: Long) -> Unit): File {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, "${app.packageName}_${app.versionCode}.apk")
        val hex = download(app.apkUrl, file, app.size, onProgress)
        if (!hex.equals(app.sha256, ignoreCase = true)) {
            file.delete()
            throw IOException("SHA-256 mismatch")
        }
        return file
    }
}
