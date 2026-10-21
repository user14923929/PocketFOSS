package org.user14923929.pocketfoss.data

import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Streaming parser for F-Droid index-v2.json.
 * Only the fields PocketFOSS needs are read; everything else is skipped,
 * so memory use stays flat even for huge indexes.
 *
 * TODO: verify the index authenticity (entry.jar signature) before trusting it.
 */
object IndexParser {

    private class Version(
        var code: Long = -1,
        var name: String = "",
        var file: String = "",
        var sha256: String = "",
        var size: Long = 0,
        var minSdk: Int = 0,
        var maxSdk: Int = 0,
        var abis: MutableList<String> = ArrayList(),
        var anti: MutableMap<String, String> = LinkedHashMap()
    ) {
        /** True if this APK can be installed on the current device (API level and ABI). */
        fun compatible(): Boolean {
            val sdk = Build.VERSION.SDK_INT
            if (minSdk > sdk) return false
            if (maxSdk in 1 until sdk) return false
            if (abis.isNotEmpty() && abis.none { it in Build.SUPPORTED_ABIS }) return false
            return true
        }
    }

    fun parse(input: InputStream, repoUrl: String, onApp: (AppEntry) -> Unit) {
        JsonReader(InputStreamReader(input, "UTF-8")).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() == "packages") readPackages(r, repoUrl, onApp) else r.skipValue()
            }
            r.endObject()
        }
    }

    private fun readPackages(r: JsonReader, repoUrl: String, onApp: (AppEntry) -> Unit) {
        r.beginObject()
        while (r.hasNext()) {
            val pkg = r.nextName()
            readPackage(r, pkg, repoUrl)?.let(onApp)
        }
        r.endObject()
    }

    private fun readPackage(r: JsonReader, pkg: String, repoUrl: String): AppEntry? {
        var name = ""
        var summary = ""
        var best: Version? = null
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "metadata" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> name = readLocalized(r)
                            "summary" -> summary = readLocalized(r)
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "versions" -> best = readVersions(r)
                else -> r.skipValue()
            }
        }
        r.endObject()
        val v = best ?: return null
        if (v.file.isEmpty() || v.sha256.isEmpty()) return null
        return AppEntry(
            packageName = pkg,
            name = name.ifEmpty { pkg },
            summary = summary,
            versionName = v.name,
            versionCode = v.code,
            apkPath = v.file,
            sha256 = v.sha256,
            size = v.size,
            repoUrl = repoUrl,
            antiFeatures = v.anti
        )
    }

    private fun readLocalized(r: JsonReader): String {
        if (r.peek() == JsonToken.STRING) return r.nextString()
        if (r.peek() != JsonToken.BEGIN_OBJECT) {
            r.skipValue()
            return ""
        }
        var preferred: String? = null
        var first: String? = null
        r.beginObject()
        while (r.hasNext()) {
            val key = r.nextName()
            if (r.peek() == JsonToken.STRING) {
                val value = r.nextString()
                if (first == null) first = value
                if (key == "en-US") preferred = value
            } else {
                r.skipValue()
            }
        }
        r.endObject()
        return preferred ?: first ?: ""
    }

    private fun readVersions(r: JsonReader): Version? {
        var best: Version? = null
        r.beginObject()
        while (r.hasNext()) {
            r.nextName() // version hash
            val v = readVersion(r)
            if (v.compatible() && v.code > (best?.code ?: -1)) best = v
        }
        r.endObject()
        return best
    }

    private fun readAntiFeatures(r: JsonReader, v: Version) {
        when (r.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                r.beginObject()
                while (r.hasNext()) {
                    val key = r.nextName()
                    v.anti[key] = readLocalized(r)
                }
                r.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                r.beginArray()
                while (r.hasNext()) v.anti[r.nextString()] = ""
                r.endArray()
            }
            else -> r.skipValue()
        }
    }

    private fun readVersion(r: JsonReader): Version {
        val v = Version()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "file" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> v.file = r.nextString()
                            "sha256" -> v.sha256 = r.nextString()
                            "size" -> v.size = r.nextLong()
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "manifest" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "versionName" -> v.name = r.nextString()
                            "versionCode" -> v.code = r.nextLong()
                            "maxSdkVersion" -> v.maxSdk = r.nextInt()
                            "usesSdk" -> {
                                r.beginObject()
                                while (r.hasNext()) {
                                    if (r.nextName() == "minSdkVersion") v.minSdk = r.nextInt() else r.skipValue()
                                }
                                r.endObject()
                            }
                            "nativecode" -> {
                                if (r.peek() == JsonToken.BEGIN_ARRAY) {
                                    r.beginArray()
                                    while (r.hasNext()) v.abis.add(r.nextString())
                                    r.endArray()
                                } else {
                                    r.skipValue()
                                }
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                }
                "antiFeatures" -> readAntiFeatures(r, v)
                else -> r.skipValue()
            }
        }
        r.endObject()
        return v
    }
}
