package com.pos.cosmos.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pos.cosmos.api.ApiConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared helpers for applying item-wise sales returns to an already-saved bill.
 *
 * This mirrors the two-step flow used by Image Bills: first the bill is saved
 * normally (with returnQty = 0), then this loads the saved bill and POSTs to
 * /bill/salesReturn so the backend writes returnQty onto the bill items and
 * creates customer_item_return rows. Outstanding is recomputed by the backend
 * from (qty - returnQty), so this nets the bill correctly without double counting.
 */
object BillReturnUtils {
    private const val TAG = "BillReturnUtils"

    /** Load the freshly-saved bill so we have server-side item line details. */
    suspend fun loadSavedBill(token: String, billNo: Int, billType: String): JsonObject? {
        return try {
            val conn = URL("${ApiConfig.BASE_URL_8080}/bill/loadBill?billId=$billNo&billType=$billType")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            if (conn.responseCode in 200..299) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                Gson().fromJson(responseBody, JsonObject::class.java)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
                Log.e(TAG, "loadSavedBill failed: ${conn.responseCode} - $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadSavedBill error: ${e.message}", e)
            null
        }
    }

    /**
     * Submit a sales return for the given bill. [returnQtyMap] maps itemId -> returnQty.
     * Returns true on success.
     */
    suspend fun submitBillReturn(
        token: String,
        billNo: Int,
        billType: String,
        loadedBillItems: JsonObject,
        returnQtyMap: Map<Int, Double>,
        returnType: String = "STOCK"
    ): Boolean {
        return try {
            val serverItems = loadedBillItems.getAsJsonArray("billItems")
                ?: loadedBillItems.getAsJsonObject("bill")?.getAsJsonArray("billItems")
            if (serverItems == null) {
                Log.e(TAG, "No billItems found in loaded bill response")
                return false
            }

            // Pick, per itemId, the single server line with the largest qty — that's where
            // we attach the user's returnQty (so the backend's qty >= returnQty check passes
            // even when FIFO distribution split the bill across multiple invoices).
            val maxLineIdxPerItem = mutableMapOf<Int, Int>()
            val maxLineQtyPerItem = mutableMapOf<Int, Double>()
            serverItems.forEachIndexed { idx, el ->
                val obj = el.asJsonObject
                val itemId = obj.get("itemId")?.asInt ?: 0
                val q = obj.get("qty")?.asDouble ?: 0.0
                if (q > (maxLineQtyPerItem[itemId] ?: -1.0)) {
                    maxLineQtyPerItem[itemId] = q
                    maxLineIdxPerItem[itemId] = idx
                }
            }

            val billData = JsonObject().apply {
                val itemsArray = JsonArray()
                serverItems.forEachIndexed { index, element ->
                    val serverItem = element.asJsonObject
                    val itemId = serverItem.get("itemId")?.asInt ?: 0
                    val isReturnLine = maxLineIdxPerItem[itemId] == index
                    val userReturnQty = if (isReturnLine) (returnQtyMap[itemId] ?: 0.0) else 0.0
                    val existingReturnQty = serverItem.get("returnQty")?.asDouble ?: 0.0

                    val obj = JsonObject().apply {
                        addProperty("index", index)
                        addProperty("itemCode", serverItem.get("itemCode")?.asString ?: "")
                        addProperty("itemName", serverItem.get("itemName")?.asString ?: "")
                        addProperty("unitPrice", serverItem.get("unitPrice")?.asNumber ?: 0)
                        addProperty("qty", serverItem.get("qty")?.asNumber ?: 0)
                        addProperty("totalPrice", serverItem.get("totalPrice")?.asNumber ?: 0)
                        addProperty("delete", false)
                        addProperty("itemId", itemId)
                        addProperty("discount", serverItem.get("discount")?.asNumber ?: 0)
                        addProperty("unitCost", serverItem.get("unitCost")?.asNumber ?: 0)
                        addProperty("supplierId", serverItem.get("supplierId")?.asInt ?: 0)
                        addProperty("invoiceId", serverItem.get("invoiceId")?.asInt ?: 0)
                        addProperty("supplierName", serverItem.get("supplierName")?.asString ?: "")
                        addProperty("invoiceName", serverItem.get("invoiceName")?.asString ?: "")
                        addProperty("totalQty", serverItem.get("totalQty")?.asInt ?: 0)
                        addProperty("newReturnQty", 0)
                        addProperty("returnQty", existingReturnQty + userReturnQty)
                        addProperty("discountValue", serverItem.get("discountValue")?.asNumber ?: 0)
                        addProperty("discountPercentage", serverItem.get("discountPercentage")?.asNumber ?: 0)
                        addProperty("reservedQty", serverItem.get("reservedQty")?.asInt ?: 0)
                        addProperty("isBox", serverItem.get("isBox")?.asBoolean ?: false)
                        addProperty("freeIssueQty", serverItem.get("freeIssueQty")?.asInt ?: 0)
                        addProperty("freeIssueReturnQty", serverItem.get("freeIssueReturnQty")?.asInt ?: 0)
                        addProperty("returnType", returnType)
                    }
                    itemsArray.add(obj)
                }
                add("billItems", itemsArray)
                addProperty("billNo", billNo)
                addProperty("billType", billType)
            }

            val conn = URL("${ApiConfig.BASE_URL_8080}/bill/salesReturn")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val jsonString = billData.toString()
            Log.d(TAG, "Submitting sales return: $jsonString")
            conn.outputStream.bufferedWriter().use { it.write(jsonString) }

            val responseCode = conn.responseCode
            val success = responseCode in 200..299
            if (!success) {
                val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Sales return failed: $responseCode - $errorResponse")
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "submitBillReturn error: ${e.message}", e)
            false
        }
    }

    /**
     * Convenience: given the cart items' (itemId -> returnQty) for items where returnQty > 0,
     * load the saved bill and apply the return. No-op (returns true) when there are no returns.
     */
    suspend fun applyReturnsIfAny(
        token: String,
        billNo: Int,
        billType: String,
        returnQtyMap: Map<Int, Double>,
        returnType: String = "STOCK"
    ): Boolean {
        val effective = returnQtyMap.filterValues { it > 0.0 }
        if (effective.isEmpty()) return true
        val loaded = loadSavedBill(token, billNo, billType) ?: run {
            Log.e(TAG, "Could not load bill #$billNo to apply returns")
            return false
        }
        return submitBillReturn(token, billNo, billType, loaded, effective, returnType)
    }
}
