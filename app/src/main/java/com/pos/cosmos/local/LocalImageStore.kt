package com.pos.cosmos.local

import android.content.Context
import java.io.File

/**
 * Disk cache for item images downloaded by the desktop-equivalent
 * "Sync Images" feature. Files live under filesDir/images/ so they
 * survive app upgrades. Coil can load `File` objects directly, so
 * call sites pass `localOrRemote(...)` straight into AsyncImage(model=...).
 */
object LocalImageStore {

    fun dir(context: Context): File {
        val d = File(context.applicationContext.filesDir, "images")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun itemFile(context: Context, itemId: Long): File =
        File(dir(context), "item_$itemId.jpeg")

    fun categoryFile(context: Context, categoryId: Long): File =
        File(dir(context), "category_$categoryId.jpeg")

    fun localCategoryOrRemote(context: Context, categoryId: Long, fallbackUrl: String?): Any? {
        val f = categoryFile(context, categoryId)
        return if (f.exists() && f.length() > 0) f else fallbackUrl?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns the local cached image File if it exists and is non-empty,
     * otherwise the supplied remote URL string. Coil's AsyncImage handles
     * either type seamlessly.
     */
    fun localOrRemote(context: Context, itemId: Long, fallbackUrl: String?): Any? {
        val f = itemFile(context, itemId)
        return if (f.exists() && f.length() > 0) f else fallbackUrl?.takeIf { it.isNotBlank() }
    }
}
