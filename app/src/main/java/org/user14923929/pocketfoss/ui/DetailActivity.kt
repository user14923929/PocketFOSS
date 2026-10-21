package org.user14923929.pocketfoss.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.StyleSpan
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.user14923929.pocketfoss.R
import org.user14923929.pocketfoss.data.AppEntry
import org.user14923929.pocketfoss.install.Installer
import org.user14923929.pocketfoss.net.Net
import org.user14923929.pocketfoss.store.LocalDb
import org.user14923929.pocketfoss.util.AntiFeatures
import org.user14923929.pocketfoss.util.Apps

class DetailActivity : Activity() {

    private lateinit var app: AppEntry
    private lateinit var progress: ProgressBar
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG)
        val found = if (pkg != null) LocalDb.get(this).get(pkg) else null
        if (found == null) {
            finish()
            return
        }
        app = found
        setContentView(R.layout.activity_detail)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<TextView>(R.id.name).text = app.name
        findViewById<TextView>(R.id.pkg).text = app.packageName
        findViewById<TextView>(R.id.version).text =
            getString(R.string.version_fmt, app.versionName, app.versionCode)
        findViewById<TextView>(R.id.size).text =
            getString(R.string.size_fmt, Formatter.formatShortFileSize(this, app.size))
        findViewById<TextView>(R.id.summary).text = app.summary
        showAntiFeatures()
        progress = findViewById(R.id.progress)
        button = findViewById(R.id.install)

        val installed = Apps.installedVersion(packageManager, app.packageName)
        button.setText(
            when {
                installed < 0 -> R.string.install
                installed < app.versionCode -> R.string.update
                else -> R.string.reinstall
            }
        )
        button.setOnClickListener { startInstall() }
    }

    private fun showAntiFeatures() {
        if (app.antiFeatures.isEmpty()) return
        findViewById<View>(R.id.af_header).visibility = View.VISIBLE
        val box = findViewById<LinearLayout>(R.id.af_list)
        box.visibility = View.VISIBLE
        val pad = (6 * resources.displayMetrics.density).toInt()
        for ((key, detail) in app.antiFeatures) {
            val info = AntiFeatures.find(key)
            val title = if (info != null) getString(info.title) else key
            val desc = if (info != null) getString(info.desc) else ""
            val text = SpannableStringBuilder(title)
            text.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (desc.isNotEmpty()) text.append('\n').append(desc)
            if (detail.isNotEmpty() && detail != desc) text.append('\n').append(detail)
            val row = TextView(this)
            row.text = text
            row.setPadding(0, pad, 0, pad)
            box.addView(row)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, R.string.allow_unknown, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            return
        }
        button.isEnabled = false
        progress.progress = 0
        progress.visibility = View.VISIBLE
        Thread {
            val file = try {
                Net.downloadApk(this, app) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    runOnUiThread { progress.progress = pct }
                }
            } catch (e: Exception) {
                fail(R.string.download_failed, e)
                return@Thread
            }
            try {
                Installer.install(this, file)
            } catch (e: Exception) {
                fail(R.string.install_failed, e)
                return@Thread
            }
            runOnUiThread {
                progress.visibility = View.GONE
                button.isEnabled = true
            }
        }.start()
    }

    private fun fail(res: Int, e: Exception) {
        Log.e("PocketFOSS", getString(res, "-"), e)
        runOnUiThread {
            progress.visibility = View.GONE
            button.isEnabled = true
            Toast.makeText(
                this,
                getString(res, "${e.javaClass.simpleName}: ${e.message}"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
    }
}
