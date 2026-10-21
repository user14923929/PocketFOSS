package org.user14923929.pocketfoss.util

import android.content.Context

object Settings {
    private const val PREFS = "settings"
    private const val KEY_HIDDEN = "hidden_antifeatures"

    /** Like F-Droid, apps marked with Tracking are hidden until the user opts in. */
    private val DEFAULT_HIDDEN = setOf("Tracking")

    fun hiddenAntiFeatures(context: Context): Set<String> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_HIDDEN, null)
        return if (stored == null) DEFAULT_HIDDEN else HashSet(stored)
    }

    fun setHiddenAntiFeatures(context: Context, hidden: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_HIDDEN, HashSet(hidden)).apply()
    }
}
