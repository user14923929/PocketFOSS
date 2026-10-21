package org.user14923929.pocketfoss.data

data class AppEntry(
    val packageName: String,
    val name: String,
    val summary: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val sha256: String,
    val size: Long,
    val repoUrl: String,
    /** Anti-Feature key -> repo-provided detail text (may be empty). */
    val antiFeatures: Map<String, String> = emptyMap()
) {
    val apkUrl: String get() = repoUrl.trimEnd('/') + "/" + apkPath.trimStart('/')
}
