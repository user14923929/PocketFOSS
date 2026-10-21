package org.user14923929.pocketfoss.util

import android.content.pm.PackageManager
import android.os.Build

object Apps {
    /** Installed versionCode of [pkg], or -1 if it is not installed. */
    @Suppress("DEPRECATION")
    fun installedVersion(pm: PackageManager, pkg: String): Long = try {
        val info = pm.getPackageInfo(pkg, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: PackageManager.NameNotFoundException) {
        -1L
    }
}
