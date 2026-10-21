package org.user14923929.pocketfoss.store

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.user14923929.pocketfoss.data.AppEntry
import org.user14923929.pocketfoss.data.IndexParser
import org.user14923929.pocketfoss.data.Repo
import org.user14923929.pocketfoss.data.RepoInfo
import org.user14923929.pocketfoss.util.AntiFeatures
import java.io.InputStream

class LocalDb private constructor(context: Context) :
    SQLiteOpenHelper(context, "pocketfoss.db", null, 3) {

    init {
        // Readers must not block while a long index sync is writing.
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE repos (" +
                "url TEXT PRIMARY KEY, name TEXT NOT NULL, fingerprint TEXT NOT NULL, " +
                "enabled INTEGER NOT NULL DEFAULT 1, timestamp INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE apps (" +
                "pkg TEXT NOT NULL, name TEXT NOT NULL, summary TEXT NOT NULL, " +
                "version_name TEXT NOT NULL, version_code INTEGER NOT NULL, " +
                "apk_path TEXT NOT NULL, sha256 TEXT NOT NULL, size INTEGER NOT NULL, " +
                "repo TEXT NOT NULL, antifeatures TEXT NOT NULL DEFAULT '{}', " +
                "af_keys TEXT NOT NULL DEFAULT '', PRIMARY KEY (pkg, repo))"
        )
        db.execSQL("CREATE INDEX apps_repo ON apps(repo)")
        val v = ContentValues()
        v.put("url", Repo.DEFAULT_URL)
        v.put("name", Repo.DEFAULT_NAME)
        v.put("fingerprint", Repo.DEFAULT_FINGERPRINT)
        v.put("enabled", 1)
        v.put("timestamp", 0)
        db.insert("repos", null, v)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS apps")
            db.execSQL("DROP TABLE IF EXISTS repos")
            onCreate(db)
            return
        }
        if (oldVersion < 3) {
            // Keeps user repositories; anti-feature data fills in on the next sync.
            db.execSQL("ALTER TABLE apps ADD COLUMN antifeatures TEXT NOT NULL DEFAULT '{}'")
            db.execSQL("ALTER TABLE apps ADD COLUMN af_keys TEXT NOT NULL DEFAULT ''")
        }
    }

    // ---------- repositories ----------

    fun repos(): List<RepoInfo> {
        readableDatabase.query("repos", null, null, null, null, null, "name COLLATE NOCASE").use { c ->
            val out = ArrayList<RepoInfo>()
            while (c.moveToNext()) out.add(readRepo(c))
            return out
        }
    }

    fun getRepo(url: String): RepoInfo? {
        readableDatabase.query("repos", null, "url = ?", arrayOf(url), null, null, null).use { c ->
            return if (c.moveToFirst()) readRepo(c) else null
        }
    }

    /** Returns false if a repository with this URL already exists. */
    fun addRepo(r: RepoInfo): Boolean {
        val v = ContentValues()
        v.put("url", r.url)
        v.put("name", r.name)
        v.put("fingerprint", r.fingerprint)
        v.put("enabled", if (r.enabled) 1 else 0)
        v.put("timestamp", r.timestamp)
        return writableDatabase.insertWithOnConflict("repos", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    /** Removes the repository together with all apps that came from it. */
    fun removeRepo(url: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("apps", "repo = ?", arrayOf(url))
            db.delete("repos", "url = ?", arrayOf(url))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setEnabled(url: String, enabled: Boolean) {
        val v = ContentValues()
        v.put("enabled", if (enabled) 1 else 0)
        writableDatabase.update("repos", v, "url = ?", arrayOf(url))
    }

    fun setTimestamp(url: String, timestamp: Long) {
        val v = ContentValues()
        v.put("timestamp", timestamp)
        writableDatabase.update("repos", v, "url = ?", arrayOf(url))
    }

    private fun readRepo(c: Cursor) = RepoInfo(
        url = c.getString(c.getColumnIndexOrThrow("url")),
        name = c.getString(c.getColumnIndexOrThrow("name")),
        fingerprint = c.getString(c.getColumnIndexOrThrow("fingerprint")),
        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) != 0,
        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
    )

    // ---------- apps ----------

    /** Replaces all apps of [repoUrl] with the contents of the index file. Returns app count. */
    fun sync(repoUrl: String, input: InputStream): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransaction()
        try {
            db.delete("apps", "repo = ?", arrayOf(repoUrl))
            val v = ContentValues()
            IndexParser.parse(input, repoUrl) { a ->
                v.put("pkg", a.packageName)
                v.put("name", a.name)
                v.put("summary", a.summary)
                v.put("version_name", a.versionName)
                v.put("version_code", a.versionCode)
                v.put("apk_path", a.apkPath)
                v.put("sha256", a.sha256)
                v.put("size", a.size)
                v.put("repo", a.repoUrl)
                v.put("antifeatures", AntiFeatures.encode(a.antiFeatures))
                v.put("af_keys", AntiFeatures.keysColumn(a.antiFeatures))
                db.insertWithOnConflict("apps", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM apps", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** [hiddenAntiFeatures]: apps carrying any of these anti-feature keys are left out. */
    fun search(query: String, hiddenAntiFeatures: Set<String> = emptySet()): List<AppEntry> {
        val where = StringBuilder()
        val args = ArrayList<String>()
        val q = query.trim()
        if (q.isNotEmpty()) {
            where.append(" AND (a.name LIKE ? OR a.pkg LIKE ?)")
            args.add("%$q%")
            args.add("%$q%")
        }
        for (key in hiddenAntiFeatures) {
            where.append(" AND a.af_keys NOT LIKE ?")
            args.add("%,$key,%")
        }
        return select(where.toString(), args)
    }

    fun get(pkg: String): AppEntry? = select(" AND a.pkg = ?", listOf(pkg)).firstOrNull()

    /**
     * Apps of enabled repositories only. If several repos carry the same package, the entry with the
     * highest versionCode wins (SQLite returns the bare columns of the row that holds the MAX).
     */
    private fun select(extraWhere: String, args: List<String>): List<AppEntry> {
        val sql = "SELECT a.pkg AS pkg, a.name AS name, a.summary AS summary, " +
            "a.version_name AS version_name, MAX(a.version_code) AS version_code, " +
            "a.apk_path AS apk_path, a.sha256 AS sha256, a.size AS size, a.repo AS repo, " +
            "a.antifeatures AS antifeatures " +
            "FROM apps a JOIN repos r ON r.url = a.repo WHERE r.enabled = 1" + extraWhere +
            " GROUP BY a.pkg ORDER BY a.name COLLATE NOCASE"
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { return readAll(it) }
    }

    private fun readAll(c: Cursor): List<AppEntry> {
        val out = ArrayList<AppEntry>(c.count)
        while (c.moveToNext()) {
            out.add(
                AppEntry(
                    packageName = c.getString(c.getColumnIndexOrThrow("pkg")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    summary = c.getString(c.getColumnIndexOrThrow("summary")),
                    versionName = c.getString(c.getColumnIndexOrThrow("version_name")),
                    versionCode = c.getLong(c.getColumnIndexOrThrow("version_code")),
                    apkPath = c.getString(c.getColumnIndexOrThrow("apk_path")),
                    sha256 = c.getString(c.getColumnIndexOrThrow("sha256")),
                    size = c.getLong(c.getColumnIndexOrThrow("size")),
                    repoUrl = c.getString(c.getColumnIndexOrThrow("repo")),
                    antiFeatures = AntiFeatures.decode(c.getString(c.getColumnIndexOrThrow("antifeatures")))
                )
            )
        }
        return out
    }

    companion object {
        @Volatile
        private var instance: LocalDb? = null

        /** One shared instance per process, so all threads use the same connection pool. */
        fun get(context: Context): LocalDb =
            instance ?: synchronized(this) {
                instance ?: LocalDb(context.applicationContext).also { instance = it }
            }
    }
}
