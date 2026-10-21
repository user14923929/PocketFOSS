package org.user14923929.pocketfoss.ui

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.TextView
import org.user14923929.pocketfoss.R
import org.user14923929.pocketfoss.util.AntiFeatures
import org.user14923929.pocketfoss.util.Settings

/** Checked anti-features are hidden from the "Available" list (same idea as F-Droid's settings). */
class AntiFeaturesActivity : Activity() {

    private val hidden = HashSet<String>()
    private lateinit var adapter: ArrayAdapter<AntiFeatures.Info>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        hidden.addAll(Settings.hiddenAntiFeatures(this))

        val pad = (16 * resources.displayMetrics.density).toInt()
        val intro = TextView(this)
        intro.setPadding(pad, pad, pad, pad)
        intro.setText(R.string.antifeatures_intro)

        val list = ListView(this)
        list.addHeaderView(intro, null, false)

        adapter = object : ArrayAdapter<AntiFeatures.Info>(
            this, android.R.layout.simple_list_item_multiple_choice, AntiFeatures.ALL
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as CheckedTextView
                val info = getItem(position)!!
                v.text = getString(info.title) + "\n" + getString(info.desc)
                v.isChecked = info.key in hidden
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { parent, _, pos, _ ->
            val info = parent.getItemAtPosition(pos) as? AntiFeatures.Info ?: return@setOnItemClickListener
            if (!hidden.add(info.key)) hidden.remove(info.key)
            Settings.setHiddenAntiFeatures(this, hidden)
            adapter.notifyDataSetChanged()
        }
        setContentView(list)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
