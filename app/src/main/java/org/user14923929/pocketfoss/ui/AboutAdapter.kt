package org.user14923929.pocketfoss.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import org.user14923929.pocketfoss.R
import org.user14923929.pocketfoss.data.About
import org.user14923929.pocketfoss.util.AntiFeatures

class AboutRow(
    val kind: Int,
    val title: String,
    val subtitle: String = "",
    val url: String? = null,
    val action: Int = 0
) {
    companion object {
        const val HEADER = 0
        const val ITEM = 1
        const val ACTION_ANTIFEATURES = 1
    }
}

object AboutRows {
    fun build(context: Context): List<AboutRow> {
        fun s(res: Int) = context.getString(res)
        val rows = ArrayList<AboutRow>()
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

        rows.add(AboutRow(AboutRow.HEADER, s(R.string.app_name)))
        rows.add(AboutRow(AboutRow.ITEM, s(R.string.about_version), version))
        rows.add(AboutRow(AboutRow.ITEM, s(R.string.about_tagline), s(R.string.about_tagline_desc)))
        rows.add(AboutRow(AboutRow.ITEM, s(R.string.about_license), "GPL-3.0"))
        rows.add(AboutRow(AboutRow.ITEM, s(R.string.about_source), About.SOURCE_URL, url = About.SOURCE_URL))

        rows.add(AboutRow(AboutRow.HEADER, s(R.string.antifeatures)))
        rows.add(
            AboutRow(
                AboutRow.ITEM, s(R.string.antifeatures_settings), s(R.string.antifeatures_settings_desc),
                action = AboutRow.ACTION_ANTIFEATURES
            )
        )
        for (info in AntiFeatures.ALL) {
            rows.add(AboutRow(AboutRow.ITEM, s(info.title), s(info.desc)))
        }

        rows.add(AboutRow(AboutRow.HEADER, s(R.string.about_libraries)))
        for (lib in About.LIBRARIES) {
            rows.add(AboutRow(AboutRow.ITEM, lib.name, "${lib.author} · ${lib.license}", url = lib.url))
        }

        rows.add(AboutRow(AboutRow.HEADER, s(R.string.about_credits)))
        rows.add(AboutRow(AboutRow.ITEM, "F-Droid", s(R.string.about_fdroid_credit), url = "https://f-droid.org"))
        return rows
    }
}

class AboutAdapter(private val context: Context, private val rows: List<AboutRow>) : BaseAdapter() {

    override fun getCount() = rows.size
    override fun getItem(position: Int): AboutRow = rows[position]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = rows[position].kind
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int): Boolean {
        val r = rows[position]
        return r.kind == AboutRow.ITEM && (r.url != null || r.action != 0)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = rows[position]
        if (row.kind == AboutRow.HEADER) {
            val tv = (convertView as? TextView) ?: TextView(context).also {
                val d = context.resources.displayMetrics.density
                it.setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (4 * d).toInt())
                it.setTextColor(0xFF33B5E5.toInt())
                it.textSize = 13f
                it.setTypeface(it.typeface, Typeface.BOLD)
            }
            tv.text = row.title.uppercase()
            return tv
        }
        val v = convertView
            ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
        v.findViewById<TextView>(android.R.id.text1).text = row.title
        val sub = v.findViewById<TextView>(android.R.id.text2)
        sub.text = row.subtitle
        sub.visibility = if (row.subtitle.isEmpty()) View.GONE else View.VISIBLE
        return v
    }
}
