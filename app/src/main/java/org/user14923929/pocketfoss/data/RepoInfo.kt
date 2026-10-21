package org.user14923929.pocketfoss.data

data class RepoInfo(
    val url: String,
    val name: String,
    /** Pinned SHA-256 fingerprint of the repo signing certificate (uppercase hex, no separators). */
    val fingerprint: String,
    val enabled: Boolean,
    /** Timestamp of the last verified entry.json, used for rollback protection. */
    val timestamp: Long
)
