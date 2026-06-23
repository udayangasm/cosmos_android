package com.pos.cosmos.models

import android.util.Log
import com.google.gson.Gson
import com.pos.cosmos.api.ApiConfig
import java.net.HttpURLConnection
import java.net.URL

data class PromotionFreeItem(
    val itemId: Int = 0,
    val itemCode: String? = "",
    val itemName: String? = "",
    val qty: Double = 0.0,
    val sellingPrice: Double = 0.0
)

data class PromotionOffer(
    val promotionId: Int = 0,
    val name: String? = "",
    val description: String? = "",
    val sameItem: Boolean = false,
    val triggerItemIds: List<Int> = emptyList(),
    val freeItems: List<PromotionFreeItem> = emptyList()
) {
    /** Sum of all free items a promo gives (used as the "deal size" marker). */
    fun totalFreeQty(): Double = freeItems.sumOf { it.qty }

    fun freeValue(): Double = freeItems.sumOf { it.qty * it.sellingPrice }

    fun offerText(): String = freeItems.joinToString(" + ") {
        val q = if (it.qty % 1.0 == 0.0) it.qty.toInt().toString() else it.qty.toString()
        "$q x ${it.itemName}"
    }
}

data class PromoEvalLine(val itemId: Int, val qty: Double)

/**
 * POST /promotion/evaluate on bill-service. Mirrors the web frontend's
 * PromotionService.evaluate(). Returns null on network/server error so the
 * caller keeps the bill untouched (same as the web behavior).
 */
fun evaluatePromotionsApi(token: String, lines: List<PromoEvalLine>): List<PromotionOffer>? {
    if (lines.isEmpty()) return emptyList()
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/promotion/evaluate")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val body = Gson().toJson(mapOf("lines" to lines))
        connection.outputStream.bufferedWriter().use { it.write(body) }

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<PromotionOffer>::class.java)?.toList() ?: emptyList()
        } else {
            Log.e("Promotion", "evaluate failed: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("Promotion", "evaluate error: ${e.message}")
        null
    }
}

/**
 * Per-bill promotion bookkeeping, mirroring the web frontend's PromotionBilling
 * (dismissed / applied maps and one-promotion-per-trigger-item exclusivity).
 * The actual bill-line mutations stay in each screen because the line models differ.
 */
class PromotionState {

    private val dismissed = mutableMapOf<Int, Double>() // promotionId -> total free qty when declined
    private val applied = mutableMapOf<Int, PromotionOffer>() // promotionId -> last applied offer

    /** Reset per-bill state (call when the bill/order is cleared or saved). */
    fun reset() {
        dismissed.clear()
        applied.clear()
    }

    fun isApplied(promotionId: Int) = applied.containsKey(promotionId)

    fun appliedIds(): List<Int> = applied.keys.toList()

    fun appliedOffer(promotionId: Int): PromotionOffer? = applied[promotionId]

    fun updateApplied(offer: PromotionOffer) {
        applied[offer.promotionId] = offer
    }

    /** Applied promo no longer qualifies: forget it so it can be offered again later. */
    fun unapply(promotionId: Int) {
        applied.remove(promotionId)
        dismissed.remove(promotionId)
    }

    /**
     * Offer applied from the pop-up. Returns the remaining pop-up offers:
     * only one promotion per trigger item, so competing offers are dropped
     * (not marked dismissed - they come back if this promo is undone).
     */
    fun onApplied(offer: PromotionOffer, currentOffers: List<PromotionOffer>): List<PromotionOffer> {
        applied[offer.promotionId] = offer
        dismissed[offer.promotionId] = offer.totalFreeQty()
        return currentOffers.filter {
            it.promotionId != offer.promotionId && !sharesTrigger(it, offer)
        }
    }

    /** Offer declined from the pop-up (re-offered only if the deal grows). */
    fun onDeclined(offer: PromotionOffer, currentOffers: List<PromotionOffer>): List<PromotionOffer> {
        dismissed[offer.promotionId] = offer.totalFreeQty()
        return currentOffers.filter { it.promotionId != offer.promotionId }
    }

    fun declineAll(currentOffers: List<PromotionOffer>) {
        currentOffers.forEach { dismissed[it.promotionId] = it.totalFreeQty() }
    }

    /** Rep manually deleted a promo free line: forget it and don't re-nag. */
    fun onPromoLineDeleted(promotionId: Int) {
        if (promotionId == 0) return
        applied.remove(promotionId)
        dismissed[promotionId] = Double.MAX_VALUE
    }

    /**
     * New (or grown) offers to pop up, excluding promos that compete for a
     * trigger item already claimed by an applied promo.
     */
    fun freshOffers(offers: List<PromotionOffer>): List<PromotionOffer> {
        return offers.filter { o ->
            if (applied.containsKey(o.promotionId)) return@filter false
            if (applied.values.any { sharesTrigger(o, it) }) return@filter false
            val dismissedAt = dismissed[o.promotionId]
            dismissedAt == null || o.totalFreeQty() > dismissedAt
        }
    }

    /** Offers competing for any common trigger item are mutually exclusive. */
    private fun sharesTrigger(a: PromotionOffer, b: PromotionOffer): Boolean {
        if (a.triggerItemIds.isEmpty() || b.triggerItemIds.isEmpty()) return false
        return a.triggerItemIds.any { b.triggerItemIds.contains(it) }
    }
}
