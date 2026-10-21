package org.user14923929.pocketfoss.data

import android.net.Uri
import org.user14923929.pocketfoss.R

/** Parsing and validation of user-entered repository addresses (including fdroidrepo:// links). */
object RepoInput {

    class Parsed(val url: String, val fingerprint: String)

    private val FINGERPRINT = Regex("^[0-9A-F]{64}$")

    /**
     * Accepts "example.org/repo", "https://example.org/repo?fingerprint=AB:CD...",
     * "fdroidrepos://example.org/repo?fingerprint=..." and an optional separate [fingerprint].
     */
    fun parse(address: String, fingerprint: String = ""): Parsed {
        var a = address.trim()
        val secure = "fdroidrepos://"
        val plain = "fdroidrepo://"
        a = when {
            a.startsWith(secure, true) -> "https://" + a.substring(secure.length)
            a.startsWith(plain, true) -> "http://" + a.substring(plain.length) // rejected by validate()
            !a.contains("://") -> "https://$a"
            else -> a
        }
        var fp = fingerprint
        if (fp.isBlank()) {
            val uri = Uri.parse(a)
            fp = uri.getQueryParameter("fingerprint") ?: uri.getQueryParameter("FINGERPRINT") ?: ""
        }
        val base = a.substringBefore('?').substringBefore('#').trimEnd('/')
        val normalized = fp.filter { !it.isWhitespace() && it != ':' }.uppercase()
        return Parsed(base, normalized)
    }

    /** Returns a string resource id describing the problem, or null if the input is acceptable. */
    fun validate(p: Parsed): Int? {
        val uri = Uri.parse(p.url)
        if (!"https".equals(uri.scheme, ignoreCase = true) || uri.host.isNullOrEmpty()) return R.string.err_https
        if (!FINGERPRINT.matches(p.fingerprint)) return R.string.err_fingerprint
        return null
    }

    fun displayName(url: String): String = Uri.parse(url).host ?: url
}
