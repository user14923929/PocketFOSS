package org.user14923929.pocketfoss.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import org.user14923929.pocketfoss.data.AppEntry

class AppAdapter(context: Context) :
    ArrayAdapter<AppEntry>(context, android.R.layout.simple_list_item_2) {

    fun setItems(items: List<AppEntry>) {
        clear()
        addAll(items)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView
            ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
        val a = getItem(position)!!
        v.findViewById<TextView>(android.R.id.text1).text = a.name
        v.findViewById<TextView>(android.R.id.text2).text = a.summary
        return v
    }
}
