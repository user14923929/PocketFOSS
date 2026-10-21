package org.user14923929.pocketfoss.data

object About {
    const val SOURCE_URL = "https://github.com/user14923929/PocketFOSS"

    class Library(val name: String, val author: String, val license: String, val url: String)

    /** Runtime libraries actually shipped in the APK. */
    val LIBRARIES: List<Library> = listOf(
        Library("Kotlin Standard Library", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
        Library("Android Open Source Project", "Google", "Apache-2.0", "https://source.android.com")
    )
}
