package org.user14923929.pocketfoss.util

import org.json.JSONObject
import org.user14923929.pocketfoss.R

/** Anti-Feature tags as used by F-Droid repositories, with built-in English descriptions. */
object AntiFeatures {

    class Info(val key: String, val title: Int, val desc: Int)

    val ALL: List<Info> = listOf(
        Info("Ads", R.string.af_ads, R.string.af_ads_desc),
        Info("Tracking", R.string.af_tracking, R.string.af_tracking_desc),
        Info("NonFreeNet", R.string.af_nonfreenet, R.string.af_nonfreenet_desc),
        Info("NonFreeAdd", R.string.af_nonfreeadd, R.string.af_nonfreeadd_desc),
        Info("NonFreeDep", R.string.af_nonfreedep, R.string.af_nonfreedep_desc),
        Info("NonFreeAssets", R.string.af_nonfreeassets, R.string.af_nonfreeassets_desc),
        Info("UpstreamNonFree", R.string.af_upstreamnonfree, R.string.af_upstreamnonfree_desc),
        Info("TetheredNet", R.string.af_tetherednet, R.string.af_tetherednet_desc),
        Info("NoSourceSince", R.string.af_nosourcesince, R.string.af_nosourcesince_desc),
        Info("KnownVuln", R.string.af_knownvuln, R.string.af_knownvuln_desc),
        Info("DisabledAlgorithm", R.string.af_disabledalgorithm, R.string.af_disabledalgorithm_desc),
        Info("NSFW", R.string.af_nsfw, R.string.af_nsfw_desc)
    )

    fun find(key: String): Info? = ALL.firstOrNull { it.key == key }

    /** key -> repo-provided detail text (may be empty), stored as JSON. */
    fun encode(map: Map<String, String>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    fun decode(json: String?): Map<String, String> {
        if (json.isNullOrEmpty()) return emptyMap()
        val o = try {
            JSONObject(json)
        } catch (e: Exception) {
            return emptyMap()
        }
        val out = LinkedHashMap<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = o.optString(k, "")
        }
        return out
    }

    /** ",Ads,Tracking," so that LIKE '%,Ads,%' matches whole keys only. */
    fun keysColumn(map: Map<String, String>): String =
        if (map.isEmpty()) "" else map.keys.joinToString(",", ",", ",")
}
