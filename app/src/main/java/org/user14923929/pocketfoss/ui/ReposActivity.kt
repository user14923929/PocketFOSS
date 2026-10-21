package org.user14923929.pocketfoss.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.user14923929.pocketfoss.R
import org.user14923929.pocketfoss.data.Repo
import org.user14923929.pocketfoss.data.RepoInfo
import org.user14923929.pocketfoss.data.RepoInput
import org.user14923929.pocketfoss.net.RepoSync
import org.user14923929.pocketfoss.store.LocalDb

class ReposActivity : Activity() {

    private lateinit var db: LocalDb
    private lateinit var adapter: RepoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LocalDb.get(this)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = RepoAdapter(this)
        val list = ListView(this)
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> adapter.getItem(pos)?.let { showActions(it) } }
        setContentView(list)

        reload()
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** fdroidrepos://host/repo?fingerprint=... opens the add dialog prefilled; the user must confirm. */
    private fun handleIntent(intent: Intent) {
        val data = intent.dataString
        if (intent.action == Intent.ACTION_VIEW && data != null) showAddDialog(data)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.repos, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_add_repo -> showAddDialog(null)
            R.id.action_restore_default -> restoreDefault()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun reload() {
        adapter.clear()
        adapter.addAll(db.repos())
    }

    private fun toast(res: Int, vararg args: Any) =
        Toast.makeText(this, getString(res, *args), Toast.LENGTH_LONG).show()

    // ---------- actions on an existing repo ----------

    private fun showActions(repo: RepoInfo) {
        val items = arrayOf(
            getString(if (repo.enabled) R.string.disable else R.string.enable),
            getString(R.string.sync_now),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setTitle(repo.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        db.setEnabled(repo.url, !repo.enabled)
                        reload()
                    }
                    1 -> syncRepo(repo)
                    2 -> confirmDelete(repo)
                }
            }
            .show()
    }

    private fun confirmDelete(repo: RepoInfo) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_repo_q, repo.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                db.removeRepo(repo.url)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun syncRepo(repo: RepoInfo) {
        Toast.makeText(this, R.string.syncing, Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                getString(R.string.synced, RepoSync.sync(this, db, repo))
            } catch (e: Exception) {
                Log.e("PocketFOSS", "sync failed for ${repo.url}", e)
                getString(R.string.sync_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                reload()
            }
        }.start()
    }

    private fun restoreDefault() {
        val info = RepoInfo(Repo.DEFAULT_URL, Repo.DEFAULT_NAME, Repo.DEFAULT_FINGERPRINT, true, 0)
        if (db.addRepo(info)) {
            reload()
            syncRepo(info)
        } else {
            toast(R.string.err_exists)
        }
    }

    // ---------- adding a repo ----------

    private fun showAddDialog(prefill: String?) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, 0)

        val address = EditText(this)
        address.setHint(R.string.hint_address)
        address.setSingleLine()
        address.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

        val fingerprint = EditText(this)
        fingerprint.setHint(R.string.hint_fingerprint)
        fingerprint.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

        box.addView(address, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(fingerprint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        if (prefill != null) {
            val p = RepoInput.parse(prefill)
            address.setText(p.url)
            fingerprint.setText(p.fingerprint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_repo)
            .setView(box)
            .setPositiveButton(R.string.add) { _, _ ->
                addRepo(address.text.toString(), fingerprint.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Adds the repo, syncs it right away and rolls the addition back if verification fails. */
    private fun addRepo(addressText: String, fingerprintText: String) {
        val p = RepoInput.parse(addressText, fingerprintText)
        val problem = RepoInput.validate(p)
        if (problem != null) {
            toast(problem)
            return
        }
        val info = RepoInfo(p.url, RepoInput.displayName(p.url), p.fingerprint, true, 0)
        if (!db.addRepo(info)) {
            toast(R.string.err_exists)
            return
        }
        reload()
        Toast.makeText(this, R.string.syncing, Toast.LENGTH_SHORT).show()
        Thread {
            val msg = try {
                getString(R.string.synced, RepoSync.sync(this, db, info))
            } catch (e: Exception) {
                Log.e("PocketFOSS", "add failed for ${info.url}", e)
                db.removeRepo(info.url)
                getString(R.string.add_failed, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                reload()
            }
        }.start()
    }

    private class RepoAdapter(context: Context) :
        ArrayAdapter<RepoInfo>(context, android.R.layout.simple_list_item_2) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView
                ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
            val r = getItem(position)!!
            v.findViewById<TextView>(android.R.id.text1).text =
                if (r.enabled) r.name else r.name + context.getString(R.string.disabled_suffix)
            v.findViewById<TextView>(android.R.id.text2).text = r.url
            return v
        }
    }
}
