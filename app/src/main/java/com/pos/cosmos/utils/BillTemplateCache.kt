package com.pos.cosmos.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Utility class for caching bill template data locally using SharedPreferences
 * This prevents unnecessary API calls by storing the template data once it's fetched
 */
object BillTemplateCache {
    private const val PREFS_NAME = "bill_template_prefs"
    private const val KEY_TEMPLATE_DATA = "template_data"
    private const val KEY_LAST_UPDATED = "last_updated"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun keyForType(billType: String) = "${KEY_TEMPLATE_DATA}_$billType"
    private fun lastUpdatedKeyForType(billType: String) = "${KEY_LAST_UPDATED}_$billType"

    /**
     * Save bill template to local cache
     * @param context Android context
     * @param template JsonObject containing bill template data
     * @param billType The bill type key (e.g. "android", "android58")
     */
    fun saveTemplate(context: Context, template: JsonObject, billType: String = "android") {
        val prefs = getPrefs(context)
        val gson = Gson()
        val templateJson = gson.toJson(template)

        prefs.edit().apply {
            putString(keyForType(billType), templateJson)
            putLong(lastUpdatedKeyForType(billType), System.currentTimeMillis())
            // Keep backward compatibility - also save to default key for "android"
            if (billType == "android") {
                putString(KEY_TEMPLATE_DATA, templateJson)
                putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            }
            apply()
        }
    }

    /**
     * Retrieve cached bill template
     * @param context Android context
     * @param billType The bill type key (e.g. "android", "android58")
     * @return JsonObject if template exists in cache, null otherwise
     */
    fun getTemplate(context: Context, billType: String = "android"): JsonObject? {
        val prefs = getPrefs(context)
        // Try type-specific key first, then fall back to default key for backward compatibility
        val templateJson = prefs.getString(keyForType(billType), null)
            ?: if (billType == "android") prefs.getString(KEY_TEMPLATE_DATA, null) else null
            ?: return null

        return try {
            val gson = Gson()
            gson.fromJson(templateJson, JsonObject::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a valid template exists in cache
     * @param context Android context
     * @param billType The bill type key
     * @return true if cached template exists, false otherwise
     */
    fun hasTemplate(context: Context, billType: String = "android"): Boolean {
        return getTemplate(context, billType) != null
    }

    /**
     * Clear cached template data
     * @param context Android context
     */
    fun clearTemplate(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }

    /**
     * Get the timestamp of when template was last updated
     * @param context Android context
     * @param billType The bill type key
     * @return timestamp in milliseconds, or 0 if never cached
     */
    fun getLastUpdated(context: Context, billType: String = "android"): Long {
        val prefs = getPrefs(context)
        return prefs.getLong(lastUpdatedKeyForType(billType), prefs.getLong(KEY_LAST_UPDATED, 0L))
    }
}
