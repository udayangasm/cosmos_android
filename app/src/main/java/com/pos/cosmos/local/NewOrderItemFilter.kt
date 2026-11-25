package com.pos.cosmos.local

import android.content.Context
import com.google.gson.JsonParser
import com.pos.cosmos.local.entities.ItemEntity

/**
 * Hide For New Orders - local-database (offline) billing mirror of the server filter in
 * bill-service BillProcessor.getBillItems. Online billing already gets a filtered list from
 * /bill/getAllBillItems; this applies the same rule to the synced items table.
 */
object NewOrderItemFilter {

    /** Reads enableHideItemsForNewOrders from the synced config blob (billing_configs, key "config"). */
    suspend fun isEnabled(context: Context): Boolean {
        return try {
            val json = CosmosDatabase.get(context).billingConfigDao().all()
                .firstOrNull { it.key == "config" }?.value ?: return false
            val obj = JsonParser.parseString(json).asJsonObject
            val v = obj.get("enableHideItemsForNewOrders")
            v != null && !v.isJsonNull && v.asString == "Y"
        } catch (e: Exception) {
            false
        }
    }

    /** Items that may be offered for a new bill / order. */
    suspend fun sellableItems(context: Context): List<ItemEntity> {
        val all = CosmosDatabase.get(context).itemDao().all()
        return if (isEnabled(context)) all.filter { !it.hideForNewOrders } else all
    }
}
