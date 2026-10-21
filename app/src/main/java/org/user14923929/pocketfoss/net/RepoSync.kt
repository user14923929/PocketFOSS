package org.user14923929.pocketfoss.net

import android.content.Context
import android.util.Log
import org.user14923929.pocketfoss.data.RepoInfo
import org.user14923929.pocketfoss.store.LocalDb
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object RepoSync {

    class Result(val repos: Int, val apps: Int, val errors: List<String>)

    /** Verified sync of one repository. Returns the number of apps stored. */
    fun sync(context: Context, db: LocalDb, repo: RepoInfo): Int {
        val entry = IndexVerifier.verifyEntry(context, repo.url, repo.fingerprint)

        // Rollback protection: never accept an index older than one we already saw.
        val last = db.getRepo(repo.url)?.timestamp ?: repo.timestamp
        if (entry.timestamp < last) {
            throw IOException("index is older than the last synced one (rollback?)")
        }

        val file = File.createTempFile("index", ".json", context.cacheDir)
        try {
            val hex = Net.download(repo.url.trimEnd('/') + entry.indexName, file, entry.size)
            if (!hex.equals(entry.sha256, ignoreCase = true)) throw IOException("index SHA-256 mismatch")
            val count = BufferedInputStream(FileInputStream(file)).use { db.sync(repo.url, it) }
            db.setTimestamp(repo.url, entry.timestamp)
            return count
        } finally {
            file.delete()
        }
    }

    /** Syncs every enabled repository; one failing repo does not stop the others. */
    fun syncAll(context: Context, db: LocalDb): Result {
        val enabled = db.repos().filter { it.enabled }
        var apps = 0
        val errors = ArrayList<String>()
        for (r in enabled) {
            try {
                apps += sync(context, db, r)
            } catch (e: Exception) {
                Log.e("PocketFOSS", "sync failed for ${r.url}", e)
                errors.add("${r.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return Result(enabled.size, apps, errors)
    }
}
