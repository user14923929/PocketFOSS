package org.user14923929.pocketfoss.ui

import android.app.ActionBar
import android.app.Activity
import android.app.FragmentTransaction
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import android.widget.SearchView
import android.widget.Toast
import org.user14923929.pocketfoss.R
import org.user14923929.pocketfoss.net.RepoSync
import org.user14923929.pocketfoss.store.LocalDb
import org.user14923929.pocketfoss.util.Apps
import org.user14923929.pocketfoss.util.Settings

@Suppress("DEPRECATION")
class MainActivity : Activity() {

    private lateinit var db: LocalDb
    private lateinit var list: ListView
    private lateinit var appAdapter: AppAdapter
    private lateinit var aboutAdapter: AboutAdapter
    private var tab = TAB_AVAILABLE
    private var query = ""
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LocalDb.get(this)
        appAdapter = AppAdapter(this)
        aboutAdapter = AboutAdapter(this, AboutRows.build(this))

        list = ListView(this)
        list.adapter = appAdapter
        list.setOnItemClickListener { _, _, pos, _ ->
            if (tab == TAB_ABOUT) {
                onAboutClick(aboutAdapter.getItem(pos))
            } else {
                val app = appAdapter.getItem(pos) ?: return@setOnItemClickListener
                startActivity(
                    Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_PKG, app.packageName)
                )
            }
        }
        setContentView(list)
        setupTabs()
        if (db.count() == 0) sync()
    }

    override fun onResume() {
        super.onResume()
        reload() // picks up repository and anti-feature changes made in other screens
    }

    private fun setupTabs() {
        val bar = actionBar ?: return
        bar.navigationMode = ActionBar.NAVIGATION_MODE_TABS
        val listener = object : ActionBar.TabListener {
            override fun onTabSelected(t: ActionBar.Tab, ft: FragmentTransaction?) {
                tab = t.position
                list.adapter = if (tab == TAB_ABOUT) aboutAdapter else appAdapter
                reload()
            }
            override fun onTabUnselected(t: ActionBar.Tab, ft: FragmentTransaction?) {}
            override fun onTabReselected(t: ActionBar.Tab, ft: FragmentTransaction?) {}
        }
        val titles = intArrayOf(
            R.string.tab_available, R.string.tab_installed, R.string.tab_updates, R.string.tab_about
        )
        for (title in titles) {
            bar.addTab(bar.newTab().setText(title).setTabListener(listener))
        }
    }

    private fun onAboutClick(row: AboutRow) {
        when {
            row.action == AboutRow.ACTION_ANTIFEATURES ->
                startActivity(Intent(this, AntiFeaturesActivity::class.java))
            row.url != null -> try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(row.url)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val search = menu.findItem(R.id.action_search).actionView as SearchView
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean = true
            override fun onQueryTextChange(q: String): Boolean {
                query = q
                reload()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> sync()
            R.id.action_repos -> startActivity(Intent(this, ReposActivity::class.java))
            R.id.action_antifeatures -> startActivity(Intent(this, AntiFeaturesActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun reload() {
        val t = tab
        if (t == TAB_ABOUT) return
        val q = query
        // Hidden anti-features only filter the browse list; installed apps and updates stay visible.
        val hidden = if (t == TAB_AVAILABLE) Settings.hiddenAntiFeatures(this) else emptySet()
        Thread {
            val all = db.search(q, hidden)
            val pm = packageManager
            val shown = when (t) {
                TAB_INSTALLED -> all.filter { Apps.installedVersion(pm, it.packageName) >= 0 }
                TAB_UPDATES -> all.filter {
                    val v = Apps.installedVersion(pm, it.packageName)
                    v >= 0 && it.versionCode > v
                }
                else -> all
            }
            runOnUiThread { appAdapter.setItems(shown) }
        }.start()
    }

    private fun sync() {
        if (syncing) return
        syncing = true
        Toast.makeText(this, R.string.syncing, Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                val r = RepoSync.syncAll(this, db)
                when {
                    r.repos == 0 -> getString(R.string.no_repos)
                    r.errors.isEmpty() -> getString(R.string.synced, r.apps)
                    else -> getString(R.string.synced_with_errors, r.apps, r.errors.joinToString("; "))
                }
            } catch (e: Exception) {
                Log.e("PocketFOSS", "sync failed", e)
                getString(R.string.sync_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                syncing = false
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                reload()
            }
        }.start()
    }

    companion object {
        private const val TAB_AVAILABLE = 0
        private const val TAB_INSTALLED = 1
        private const val TAB_UPDATES = 2
        private const val TAB_ABOUT = 3
    }
}
