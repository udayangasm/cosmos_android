package com.pos.cosmos.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.utils.LocalResponsiveDimensions
import com.pos.cosmos.utils.LocationHelper
import com.pos.cosmos.utils.ReceiptShareUtils
import com.pos.cosmos.utils.ReceiptPreviewDialog
import java.io.File
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pos.cosmos.components.PromotionOfferDialog
import com.pos.cosmos.models.PromoEvalLine
import com.pos.cosmos.models.PromotionOffer
import com.pos.cosmos.models.PromotionState
import com.pos.cosmos.models.evaluatePromotionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.pos.cosmos.api.ApiConfig

data class SupplierInvoiceDetail(
    val invoiceId: Int,
    val supplierId: Int,
    val qty: Int,
    val sellingPrice: Double = 0.0,
    val reservedQty: Int = 0,
    val defaultDiscountForPrice: Double = 0.0
)

data class CreditItemOption(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    val invoiceId: Int = 0,
    val supplierId: Int = 0,
    val totalQty: Int = 0,
    val barcode: String = "",
    val supplierInvoiceDetails: List<SupplierInvoiceDetail> = emptyList()
)

/**
 * Splits a requested qty across multiple invoices (oldest first).
 * Each split gets the correct invoiceId for backend Phase 1 matching.
 * The price can be overridden by the user (backend matches by invoiceId, not price).
 */
private fun splitItemAcrossInvoices(
    item: CreditItemOption,
    requestedQty: Int,
    discountValue: Double = 0.0,
    discountPercentage: Double = 0.0,
    freeIssueQty: Double = 0.0,
    overridePrice: Double? = null,
    applyDefaultDiscountForPrice: Boolean = false,
    // When true, every split row uses the item's last-updated (master) selling price
    // instead of the per-purchase-invoice price, matching the web credit-bill when
    // "Use last updated selling price" is enabled.
    useLastUpdatedSellingPrice: Boolean = true
): List<BillItem> {
    val invoices = item.supplierInvoiceDetails.filter { it.qty > 0 }

    if (invoices.isEmpty()) {
        val price = overridePrice ?: item.sellingPrice
        return listOf(
            BillItem(
                id = item.id, code = item.code, name = item.name,
                sellingPrice = price, quantity = requestedQty,
                unitPrice = price,
                invoiceId = item.invoiceId, supplierId = item.supplierId,
                totalQty = item.totalQty, discount = discountValue,
                discountValue = discountValue, discountPercentage = discountPercentage,
                freeIssueQty = freeIssueQty
            )
        )
    }

    val result = mutableListOf<BillItem>()
    var remaining = requestedQty
    var remainingFreeQty = freeIssueQty

    for (inv in invoices) {
        if (remaining <= 0) break
        val availableQty = inv.qty - inv.reservedQty
        if (availableQty <= 0) continue
        val take = minOf(remaining, availableQty)
        val freeForThisRow = minOf(remainingFreeQty, take.toDouble())
        val sellingQty = take - freeForThisRow.toInt()
        remainingFreeQty -= freeForThisRow

        val rowPrice = overridePrice ?: if (useLastUpdatedSellingPrice) item.sellingPrice else inv.sellingPrice

        // When caller didn't supply an explicit discount and the config flag is on,
        // apply the per-invoice default discount.
        val useInvoiceDefault = applyDefaultDiscountForPrice && discountValue == 0.0 &&
            inv.defaultDiscountForPrice > 0 && rowPrice > 0
        val rowDiscountValue = if (useInvoiceDefault) inv.defaultDiscountForPrice else discountValue
        val rowDiscountPercentage = if (useInvoiceDefault) (inv.defaultDiscountForPrice / rowPrice) * 100.0 else discountPercentage

        result.add(
            BillItem(
                id = item.id, code = item.code, name = item.name,
                sellingPrice = rowPrice, quantity = sellingQty,
                unitPrice = rowPrice,
                invoiceId = inv.invoiceId, supplierId = inv.supplierId,
                totalQty = inv.qty, discount = rowDiscountValue,
                discountValue = rowDiscountValue, discountPercentage = rowDiscountPercentage,
                freeIssueQty = freeForThisRow
            )
        )
        remaining -= take
    }

    // If remaining qty still not covered, add to last invoice
    if (remaining > 0 && result.isNotEmpty()) {
        val last = result.last()
        result[result.lastIndex] = last.copy(quantity = last.quantity + remaining)
    } else if (remaining > 0) {
        val price = overridePrice ?: item.sellingPrice
        result.add(
            BillItem(
                id = item.id, code = item.code, name = item.name,
                sellingPrice = price, quantity = requestedQty,
                unitPrice = price,
                invoiceId = item.invoiceId, supplierId = item.supplierId,
                totalQty = item.totalQty, discount = discountValue,
                discountValue = discountValue, discountPercentage = discountPercentage,
                freeIssueQty = freeIssueQty
            )
        )
    }

    return result
}

/**
 * Merge cart items with the same name and sellingPrice into single receipt lines.
 * Items with different prices are kept separate.
 */
private fun mergeCartItemsForReceipt(items: List<BillItem>): List<ReceiptItem> {
    val mergeMap = LinkedHashMap<String, ReceiptItem>()
    for (item in items) {
        // Include discountValue in the key so rows with different per-line discounts stay separate
        // (promo free lines get their own key so they never merge with paid lines)
        val key = "${item.id}|${String.format("%.2f", item.sellingPrice)}|${String.format("%.2f", item.discountValue)}|${if (item.isPromoFreeItem) "P${item.promotionId}" else ""}"
        val existing = mergeMap[key]
        if (existing != null) {
            mergeMap[key] = ReceiptItem(
                existing.name,
                existing.quantity + item.quantity,
                existing.price,
                existing.amount + item.amount,
                existing.discountValue
            )
        } else {
            // Promotion free lines print with a (FREE) marker
            val displayName = if (item.isPromoFreeItem) "${item.name} (FREE)" else item.name
            mergeMap[key] = ReceiptItem(displayName, item.quantity.toDouble(), item.sellingPrice, item.amount, item.discountValue)
        }
    }
    return mergeMap.values.toList()
}

private fun totalLineItemDiscount(items: List<BillItem>): Double =
    items.sumOf { it.quantity * it.discountValue }

/** Total money value of returned units across the cart. */
private fun cartTotalReturn(items: List<BillItem>): Double =
    items.sumOf { it.returnAmount }

/** Build the receipt's "returns" lines from cart items that have a return qty. */
private fun cartReturnReceiptItems(items: List<BillItem>): List<ReceiptItem> =
    items.filter { it.returnQty > 0.0 }
        .map { ReceiptItem(it.name, it.returnQty, it.sellingPrice, it.returnAmount, it.discountValue) }

private const val CREDIT_BILL_PREFS = "credit_bill_cache"
private const val KEY_CART_ITEMS = "cart_items"
private const val KEY_CUSTOMER_NAME = "customer_name"
private const val KEY_CUSTOMER_ID = "customer_id"
private const val KEY_SALES_REP = "sales_rep"
private const val KEY_SALES_REP_ID = "sales_rep_id"
private const val KEY_DISCOUNT = "discount"
private const val KEY_MEMO = "memo"

private fun saveCreditBillCache(context: Context, cartItems: List<BillItem>, customerName: String, customerId: Long, salesRep: String, salesRepId: Long, discount: String, memo: String) {
    val prefs = context.getSharedPreferences(CREDIT_BILL_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_CART_ITEMS, Gson().toJson(cartItems))
        .putString(KEY_CUSTOMER_NAME, customerName)
        .putLong(KEY_CUSTOMER_ID, customerId)
        .putString(KEY_SALES_REP, salesRep)
        .putLong(KEY_SALES_REP_ID, salesRepId)
        .putString(KEY_DISCOUNT, discount)
        .putString(KEY_MEMO, memo)
        .apply()
}

private fun loadCachedCartItems(context: Context): List<BillItem> {
    val prefs = context.getSharedPreferences(CREDIT_BILL_PREFS, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_CART_ITEMS, null) ?: return emptyList()
    return try {
        val type = object : com.google.gson.reflect.TypeToken<List<BillItem>>() {}.type
        Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

private fun loadCachedFormData(context: Context): Map<String, Any> {
    val prefs = context.getSharedPreferences(CREDIT_BILL_PREFS, Context.MODE_PRIVATE)
    return mapOf(
        KEY_CUSTOMER_NAME to (prefs.getString(KEY_CUSTOMER_NAME, "") ?: ""),
        KEY_CUSTOMER_ID to prefs.getLong(KEY_CUSTOMER_ID, 0L),
        KEY_SALES_REP to (prefs.getString(KEY_SALES_REP, "") ?: ""),
        KEY_SALES_REP_ID to prefs.getLong(KEY_SALES_REP_ID, 0L),
        KEY_DISCOUNT to (prefs.getString(KEY_DISCOUNT, "0") ?: "0"),
        KEY_MEMO to (prefs.getString(KEY_MEMO, "") ?: "")
    )
}

private fun clearCreditBillCache(context: Context) {
    context.getSharedPreferences(CREDIT_BILL_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun CreditBillScreen(
    navController: NavHostController,
    appViewModel: AppViewModel,
    orderNo: Int = 0,
    customerId: Long = 0L,
    salesRefId: Long = 0L
) {
    val context = LocalContext.current

    // Load cached form data
    val cachedForm = remember { loadCachedFormData(context) }
    val cachedCart = remember { loadCachedCartItems(context) }

    var cartItems by remember { mutableStateOf(cachedCart) }
    var successMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastReceiptPdf by remember { mutableStateOf<File?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var availableItems by remember { mutableStateOf<List<ItemOption>>(emptyList()) }
    var creditItemOptions by remember { mutableStateOf<List<CreditItemOption>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<ItemOption>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showItemSearch by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf(
        if (customerId > 0L) "" else cachedForm[KEY_CUSTOMER_NAME] as? String ?: ""
    ) }
    var selectedCustomerId by remember { mutableStateOf(
        if (customerId > 0L) customerId else cachedForm[KEY_CUSTOMER_ID] as? Long ?: 0L
    ) }
    var selectedSalesRep by remember { mutableStateOf(
        if (salesRefId > 0L) "" else cachedForm[KEY_SALES_REP] as? String ?: ""
    ) }
    var filteredSalesReps by remember { mutableStateOf<List<SalesRepData>>(emptyList()) }
    var expandedSalesRep by remember { mutableStateOf(false) }
    var discountValue by remember { mutableStateOf(cachedForm[KEY_DISCOUNT] as? String ?: "0") }
    var loading by remember { mutableStateOf(false) }
    var isLoadingCustomers by remember { mutableStateOf(true) }
    var customers by remember { mutableStateOf<List<String>>(emptyList()) }
    var allCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var filteredCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var salesReps by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSalesRepId by remember { mutableStateOf(
        if (salesRefId > 0L) salesRefId else cachedForm[KEY_SALES_REP_ID] as? Long ?: 0L
    ) }
    var allSalesReps by remember { mutableStateOf<List<SalesRepData>>(emptyList()) }
    var editingItemId by remember { mutableStateOf<Int?>(null) }
    var editingQuantity by remember { mutableStateOf("") }
    var editingPrice by remember { mutableStateOf("") }
    var editingDiscountPercentage by remember { mutableStateOf("") }
    var editingDiscountValue by remember { mutableStateOf("") }
    var editingFreeIssue by remember { mutableStateOf("") }
    var editingReturnQty by remember { mutableStateOf("") }
    var expandedCustomer by remember { mutableStateOf(false) }
    var hideSalesPersonField by remember { mutableStateOf(false) }
    var showPaymentDialogInCreditBills by remember { mutableStateOf("") }
    var allowCashBillsInCreditBillPage by remember { mutableStateOf("") }
    var createNewCustomerWhenBillingIfNotExists by remember { mutableStateOf("") }
    var enableMultiplePaymentSameTimeInBilling by remember { mutableStateOf("") }
    var showSplitPaymentDialog by remember { mutableStateOf(false) }

    // Cash bill mode state (when allowCashBillsInCreditBillPage is enabled and no customer selected)
    var isCashBillMode by remember { mutableStateOf(false) }
    var cashBillNo by remember { mutableStateOf("") }
    var showFreeIssueInOrderForm by remember { mutableStateOf("") }
    var showCustomerAddressInOrderForm by remember { mutableStateOf("") }
    var showPaymentOptionsInOrderForm by remember { mutableStateOf("") }
    var orderPaymentOption by remember { mutableStateOf("") }
    var showMemoInOrderFormAndCreditBill by remember { mutableStateOf("") }
    var memoText by remember { mutableStateOf(cachedForm[KEY_MEMO] as? String ?: "") }

    // Config for promotion offers (pop-up in Order Form & Credit Bill)
    var enablePromotions by remember { mutableStateOf("") }

    // Configs for selling price editing (free / upward-only / locked)
    var editSellingPrice by remember { mutableStateOf("") }
    var editSellingPriceUpward by remember { mutableStateOf("") }
    val canEditSellingPrice = editSellingPrice == "Y" || editSellingPriceUpward == "Y"
    val sellingPriceUpwardOnly = editSellingPrice != "Y" && editSellingPriceUpward == "Y"

    // Promotion state (mirrors web PromotionBilling)
    val promoState = remember { PromotionState() }
    var promoOffers by remember { mutableStateOf<List<PromotionOffer>>(emptyList()) }
    var showPromoDialog by remember { mutableStateOf(false) }
    var promoEvalJob by remember { mutableStateOf<Job?>(null) }

    // Address dropdown state
    var customerAddresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf("") }
    var showAddressDropdown by remember { mutableStateOf(false) }
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }
    var paperWidthMm by remember { mutableFloatStateOf(80f) }
    var billNo by remember { mutableStateOf("1") }
    var isLoadingBillNo by remember { mutableStateOf(false) }
    var orderNoFromParams by remember { mutableStateOf(orderNo) }
    var isLoadingOrder by remember { mutableStateOf(orderNo > 0) }

    // Payment dialog states
    var showPaymentMethodDialog by remember { mutableStateOf(false) }
    var showCashPaymentDialog by remember { mutableStateOf(false) }
    var showChequePaymentDialog by remember { mutableStateOf(false) }
    var showCardPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var cashTRN by remember { mutableStateOf("") }
    var chequeNumber by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf("") }
    var showChequeDatePicker by remember { mutableStateOf(false) }
    var bankAndBranch by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var chequeTRN by remember { mutableStateOf("") }
    var cardTRN by remember { mutableStateOf("") }
    var cardLastFourDigits by remember { mutableStateOf("") }

    // Error dialog state
    var showErrorDialog by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val scope = rememberCoroutineScope()

    // Auto-save cart and form data to cache whenever they change
    LaunchedEffect(cartItems, selectedCustomer, selectedCustomerId, selectedSalesRep, selectedSalesRepId, discountValue, memoText) {
        saveCreditBillCache(context, cartItems, selectedCustomer, selectedCustomerId, selectedSalesRep, selectedSalesRepId, discountValue, memoText)
    }

    // ---- Promotions (mirrors web PromotionBilling.applyOffer/removeApplied/run) ----

    /** Write an offer's free items onto the cart lines. */
    fun applyOfferToLines(offer: PromotionOffer, items: List<BillItem>): List<BillItem> {
        if (offer.sameItem) {
            val free = offer.freeItems.firstOrNull() ?: return items
            val idx = items.indexOfFirst { !it.isPromoFreeItem && it.id == free.itemId }
            if (idx >= 0 && items[idx].freeIssueQty != free.qty) {
                return items.toMutableList().apply { this[idx] = this[idx].copy(freeIssueQty = free.qty) }
            }
            return items
        }
        var list = items
        for (free in offer.freeItems) {
            val idx = list.indexOfFirst {
                it.isPromoFreeItem && it.promotionId == offer.promotionId && it.id == free.itemId
            }
            list = if (idx >= 0) {
                if (list[idx].quantity != free.qty.toInt()) {
                    list.toMutableList().apply { this[idx] = this[idx].copy(quantity = free.qty.toInt()) }
                } else list
            } else {
                list + BillItem(
                    id = free.itemId,
                    code = free.itemCode ?: "",
                    name = free.itemName ?: "",
                    sellingPrice = 0.0,
                    quantity = free.qty.toInt(),
                    unitPrice = 0.0,
                    isPromoFreeItem = true,
                    promotionId = offer.promotionId
                )
            }
        }
        return list
    }

    /** Undo a previously applied promo (qty dropped below the trigger). */
    fun removeAppliedOffer(offer: PromotionOffer?, promotionId: Int, items: List<BillItem>): List<BillItem> {
        var list = items
        if (offer != null && offer.sameItem) {
            val free = offer.freeItems.firstOrNull()
            if (free != null) {
                val idx = list.indexOfFirst { !it.isPromoFreeItem && it.id == free.itemId }
                if (idx >= 0 && list[idx].freeIssueQty > 0) {
                    list = list.toMutableList().apply { this[idx] = this[idx].copy(freeIssueQty = 0.0) }
                }
            }
        }
        return list.filterNot { it.isPromoFreeItem && it.promotionId == promotionId }
    }

    /** Debounced re-evaluation. Call after any item add / qty change / delete. */
    fun schedulePromotionCheck() {
        if (enablePromotions != "Y") return
        val tk = token ?: return
        promoEvalJob?.cancel()
        promoEvalJob = scope.launch {
            delay(400)
            val lines = cartItems
                .filter { !it.isPromoFreeItem && it.id > 0 && it.quantity > 0 }
                .map { PromoEvalLine(it.id, it.quantity.toDouble()) }
            val offers = if (lines.isEmpty()) emptyList()
            else withContext(Dispatchers.IO) { evaluatePromotionsApi(tk, lines) }
                ?: return@launch // network/server issue: keep the bill untouched

            val offerIds = offers.map { it.promotionId }.toSet()
            var items = cartItems

            // 1) Undo applied promos that no longer qualify
            for (id in promoState.appliedIds()) {
                if (id !in offerIds) {
                    items = removeAppliedOffer(promoState.appliedOffer(id), id, items)
                    promoState.unapply(id)
                }
            }

            // 2) Silently reconcile already-applied promos to the new multiplier
            for (offer in offers) {
                if (promoState.isApplied(offer.promotionId)) {
                    items = applyOfferToLines(offer, items)
                    promoState.updateApplied(offer)
                }
            }

            // 3) Pop up only genuinely new (or grown) offers
            val fresh = promoState.freshOffers(offers)
            if (fresh.isNotEmpty()) {
                promoOffers = fresh
                showPromoDialog = true
            }

            if (items !== cartItems) cartItems = items
        }
    }

    fun fetchAllCustomers() {
        scope.launch {
            isLoadingCustomers = true
            try {
                val customerList = withContext(Dispatchers.IO) {
                    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                        try {
                            com.pos.cosmos.local.CosmosDatabase.get(context).customerDao().all().map {
                                CustomerData(
                                    id = it.id,
                                    name = it.name,
                                    address = it.address,
                                    telephone = it.telephone,
                                    whatsappNo = it.whatsapp,
                                    email = it.email,
                                    companyCode = it.companyCode
                                )
                            }
                        } catch (e: Exception) { emptyList() }
                    } else try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<CustomerData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
                allCustomers = customerList
                filteredCustomers = customerList
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingCustomers = false
            }
        }
    }

    fun fetchAllSalesReps() {
        scope.launch {
            try {
                val salesRepList = withContext(Dispatchers.IO) {
                    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                        try {
                            com.pos.cosmos.local.CosmosDatabase.get(context).salesRefDao().all().map {
                                SalesRepData(id = it.id, name = it.name)
                            }
                        } catch (e: Exception) { emptyList() }
                    } else try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/salesRef/getAllSalesRef")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<SalesRepData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
                allSalesReps = salesRepList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun filterCustomers(query: String) {
        selectedCustomer = query
        if (query.isEmpty()) {
            filteredCustomers = allCustomers
            expandedCustomer = false
        } else {
            val filtered = allCustomers.filter { customer ->
                (customer.name?.contains(query, ignoreCase = true) ?: false)
            }
            filteredCustomers = filtered
            expandedCustomer = filtered.isNotEmpty()
        }
    }

    fun filterSalesReps(query: String) {
        selectedSalesRep = query
        if (query.isEmpty()) {
            filteredSalesReps = allSalesReps
            expandedSalesRep = false
        } else {
            val filtered = allSalesReps.filter { salesRep ->
                (salesRep.name?.contains(query, ignoreCase = true) ?: false)
            }
            filteredSalesReps = filtered
            expandedSalesRep = filtered.isNotEmpty()
        }
    }

    fun onSelectCustomer(customer: CustomerData) {
        selectedCustomer = customer.name ?: ""
        selectedCustomerId = customer.id
        expandedCustomer = false
        // Populate address dropdown with all addresses of customers with same name
        val sameNameCustomers = allCustomers.filter { it.name.equals(customer.name, ignoreCase = true) }
        customerAddresses = sameNameCustomers.mapNotNull { it.address }.distinct()
        selectedAddress = customer.address ?: ""
    }

    fun onSelectAddress(address: String) {
        selectedAddress = address
        // Find and select the customer with this address
        val customerWithAddress = allCustomers.find {
            it.name.equals(selectedCustomer, ignoreCase = true) && it.address == address
        }
        if (customerWithAddress != null) {
            selectedCustomerId = customerWithAddress.id
        }
    }

    fun onSelectSalesRep(salesRep: SalesRepData) {
        selectedSalesRep = salesRep.name ?: ""
        selectedSalesRepId = salesRep.id
        expandedSalesRep = false
    }

    fun loadNextBillNo() {
        scope.launch {
            isLoadingBillNo = true
            withContext(Dispatchers.IO) {
                if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                    try {
                        billNo = com.pos.cosmos.local.LocalBillSaver.nextBillNoForType(context, "CREDIT").toString()
                        Log.d("CreditBillScreen", "Loaded next CREDIT bill number from local DB: $billNo")
                    } catch (e: Exception) {
                        Log.e("CreditBillScreen", "Error reading next bill number locally: ${e.message}")
                    }
                } else try {
                    val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getNextBillNo?billType=CREDIT")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        billNo = response.trim().replace("\"", "")
                        Log.d("CreditBillScreen", "Loaded next bill number: $billNo")
                    }
                } catch (e: Exception) {
                    Log.e("CreditBillScreen", "Error loading next bill number: ${e.message}")
                }
            }
            isLoadingBillNo = false
        }
    }

    suspend fun loadNextCashBillNo(): String {
        return withContext(Dispatchers.IO) {
            if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                return@withContext try {
                    com.pos.cosmos.local.LocalBillSaver.nextBillNoForType(context, "CASH").toString()
                } catch (e: Exception) { "" }
            }
            try {
                val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getNextBillNo?billType=CASH")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val nextCashBillNo = response.trim().replace("\"", "")
                    Log.d("CreditBillScreen", "Loaded next CASH bill number: $nextCashBillNo")
                    nextCashBillNo
                } else {
                    Log.e("CreditBillScreen", "Error loading next cash bill number: ${connection.responseCode}")
                    ""
                }
            } catch (e: Exception) {
                Log.e("CreditBillScreen", "Error loading next cash bill number: ${e.message}")
                ""
            }
        }
    }

    // Load order data for update (when coming from All Orders screen)
    fun loadOrderForUpdate(orderNumber: Int, customerIdParam: Long, salesRefIdParam: Long) {
        scope.launch {
            isLoadingOrder = true
            try {
                val orderData = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL("${ApiConfig.BASE_URL_8080}/order/loadOrderForm?orderId=$orderNumber")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            Log.d("CreditBillScreen", "Order data loaded: ${content.take(500)}")
                            val gson = Gson()
                            gson.fromJson(content, JsonObject::class.java)
                        } else {
                            Log.e("CreditBillScreen", "Failed to load order: ${connection.responseCode}")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("CreditBillScreen", "Error loading order: ${e.message}")
                        null
                    }
                }

                if (orderData != null) {
                    val order = orderData.getAsJsonObject("order")

                    // Set customer from param or order
                    val customerIdToUse = if (customerIdParam > 0) customerIdParam else (order?.get("customerId")?.asLong ?: 0L)
                    if (customerIdToUse > 0) {
                        selectedCustomerId = customerIdToUse
                        val customer = allCustomers.find { it.id == customerIdToUse }
                        if (customer != null) {
                            selectedCustomer = customer.name ?: ""
                        }
                    }

                    // Set sales rep from param or order
                    val salesRefIdToUse = if (salesRefIdParam > 0) salesRefIdParam else (order?.get("salesRefId")?.asLong ?: 0L)
                    if (salesRefIdToUse > 0) {
                        selectedSalesRepId = salesRefIdToUse
                        val salesRep = allSalesReps.find { it.id == salesRefIdToUse }
                        if (salesRep != null) {
                            selectedSalesRep = salesRep.name ?: ""
                        }
                    }

                    // Set discount values and memo from order
                    order?.let {
                        discountValue = (it.get("discountValue")?.asDouble ?: 0.0).toString()
                        // Extract payment option from order
                        orderPaymentOption = it.get("paymentOption")?.asString ?: ""
                        Log.d("CreditBillScreen", "Order payment option: $orderPaymentOption")
                        // Extract memo from order
                        memoText = it.get("memo")?.asString ?: ""
                        Log.d("CreditBillScreen", "Order memo: $memoText")
                    }

                    // Load order items as bill items
                    val orderItems = order?.getAsJsonArray("orderItems")
                    if (orderItems != null && orderItems.size() > 0) {
                        val newBillItems = mutableListOf<BillItem>()

                        for (item in orderItems) {
                            val orderItem = item.asJsonObject
                            val itemId = orderItem.get("itemId")?.asInt ?: 0
                            val qty = orderItem.get("qty")?.asDouble ?: 0.0
                            val unitPrice = orderItem.get("unitPrice")?.asDouble ?: 0.0
                            val itemDiscountValue = orderItem.get("discountValue")?.asDouble ?: 0.0
                            val itemDiscountPercentage = orderItem.get("discountPercentage")?.asDouble ?: 0.0
                            val itemFreeIssueQty = orderItem.get("freeIssueQty")?.asDouble ?: 0.0
                            val supplierId = orderItem.get("supplierId")?.asInt ?: 0
                            val invoiceId = orderItem.get("invoiceId")?.asInt ?: 0
                            val itemIsPromoFree = try { orderItem.get("isPromoFreeItem")?.asBoolean ?: false } catch (e: Exception) { false }
                            val itemPromotionId = try { orderItem.get("promotionId")?.asInt ?: 0 } catch (e: Exception) { 0 }

                            // Find item details from available items
                            val itemFromCatalog = availableItems.find { it.id == itemId }

                            val billItem = BillItem(
                                id = itemId,
                                code = itemFromCatalog?.code ?: "",
                                name = itemFromCatalog?.name ?: "Unknown Item",
                                sellingPrice = unitPrice,
                                quantity = qty.toInt(),
                                unitPrice = unitPrice,
                                discount = itemDiscountValue,
                                discountValue = itemDiscountValue,
                                discountPercentage = itemDiscountPercentage,
                                freeIssueQty = itemFreeIssueQty,
                                supplierId = supplierId,
                                invoiceId = invoiceId,
                                isPromoFreeItem = itemIsPromoFree,
                                promotionId = itemPromotionId
                            )
                            newBillItems.add(billItem)
                        }

                        cartItems = newBillItems
                        Log.d("CreditBillScreen", "Loaded ${newBillItems.size} items from order")
                    }
                }
            } catch (e: Exception) {
                Log.e("CreditBillScreen", "Error in loadOrderForUpdate: ${e.message}")
            } finally {
                isLoadingOrder = false
            }
        }
    }

    fun loadBillByNo(billNumber: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val connection = URL("${ApiConfig.BASE_URL_8080}/bill/loadBill?billId=$billNumber")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val billData = JsonParser.parseString(response).asJsonObject

                        billNo = billData.get("billNo")?.asString ?: billNumber

                        // Load customer
                        val customerId = billData.get("customerId")?.asLong ?: 0L
                        if (customerId > 0) {
                            selectedCustomerId = customerId
                            // Find customer name from allCustomers list
                            val customer = allCustomers.find { it.id == customerId }
                            selectedCustomer = customer?.name ?: ""
                        }

                        // Load sales rep
                        val salesRefId = billData.get("salesRefId")?.asLong ?: 0L
                        if (salesRefId > 0) {
                            selectedSalesRepId = salesRefId
                            val salesRep = allSalesReps.find { it.id == salesRefId }
                            selectedSalesRep = salesRep?.name ?: ""
                        }

                        // Load discount
                        val discountVal = billData.get("discount")?.asDouble ?: 0.0
                        discountValue = discountVal.toString()

                        // Load bill items
                        val billItems = billData.getAsJsonArray("billItems")
                        val loadedItems = billItems.map { itemElement ->
                            val item = itemElement.asJsonObject
                            BillItem(
                                id = item.get("itemId")?.asInt ?: 0,
                                code = item.get("itemCode")?.asString ?: "",
                                name = item.get("itemName")?.asString ?: "",
                                sellingPrice = item.get("unitPrice")?.asDouble ?: 0.0,
                                quantity = item.get("qty")?.asString?.toIntOrNull() ?: 1,
                                unitPrice = item.get("unitPrice")?.asDouble ?: 0.0,
                                discount = item.get("discount")?.asDouble ?: 0.0,
                                discountValue = item.get("discountValue")?.asDouble ?: 0.0,
                                discountPercentage = item.get("discountPercentage")?.asDouble ?: 0.0,
                                invoiceId = item.get("invoiceId")?.asInt ?: 0,
                                supplierId = item.get("supplierId")?.asInt ?: 0,
                                totalQty = item.get("totalQty")?.asInt ?: 0,
                                freeIssueQty = item.get("freeIssueQty")?.asDouble ?: 0.0,
                                isPromoFreeItem = try { item.get("isPromoFreeItem")?.asBoolean ?: false } catch (e: Exception) { false },
                                promotionId = try { item.get("promotionId")?.asInt ?: 0 } catch (e: Exception) { 0 }
                            )
                        }
                        cartItems = loadedItems

                        Log.d("CreditBillScreen", "Loaded bill $billNumber successfully")
                    } else {
                        Log.e("CreditBillScreen", "Bill not found: $billNumber")
                    }
                } catch (e: Exception) {
                    Log.e("CreditBillScreen", "Error loading bill: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    // Fetch config
                    val appConfig = fetchConfig(context, token!!)
                    if (appConfig != null) {
                        appViewModel.setConfig(appConfig)
                        hideSalesPersonField = appConfig.hideSalesPersonInCashBill == "Y"
                        showPaymentDialogInCreditBills = appConfig.showPaymentDialogInCreditBills ?: ""
                        allowCashBillsInCreditBillPage = appConfig.allowCashBillsInCreditBillPage ?: ""
                        createNewCustomerWhenBillingIfNotExists = appConfig.createNewCustomerWhenBillingIfNotExists ?: ""
                        enableMultiplePaymentSameTimeInBilling = appConfig.enableMultiplePaymentSameTimeInBilling ?: ""
                        Log.d("CreditBillScreen", "=== CONFIG LOADED ===")
                        Log.d("CreditBillScreen", "allowCashBillsInCreditBillPage from API: '${appConfig.allowCashBillsInCreditBillPage}'")
                        Log.d("CreditBillScreen", "allowCashBillsInCreditBillPage after assignment: '$allowCashBillsInCreditBillPage'")
                        showFreeIssueInOrderForm = appConfig.showFreeIssueInOrderForm ?: ""
                        showCustomerAddressInOrderForm = appConfig.showCustomerAddressInOrderForm ?: ""
                        showPaymentOptionsInOrderForm = appConfig.showPaymentOptionsInOrderForm ?: ""
                        showMemoInOrderFormAndCreditBill = appConfig.showMemoInOrderFormAndCreditBill ?: ""
                        enablePromotions = appConfig.enablePromotions ?: ""
                        editSellingPrice = appConfig.editSellingPriceWhenBilling ?: ""
                        editSellingPriceUpward = appConfig.editSellingPriceUpwardWhenBilling ?: ""
                        Log.d("CreditBillScreen", "enablePromotions: $enablePromotions")
                        Log.d("CreditBillScreen", "editSellingPrice: $editSellingPrice, upward: $editSellingPriceUpward")
                        Log.d("CreditBillScreen", "createNewCustomerWhenBillingIfNotExists: $createNewCustomerWhenBillingIfNotExists")
                        Log.d("CreditBillScreen", "showFreeIssueInOrderForm: $showFreeIssueInOrderForm")
                        Log.d("CreditBillScreen", "showCustomerAddressInOrderForm: $showCustomerAddressInOrderForm")
                        Log.d("CreditBillScreen", "showPaymentOptionsInOrderForm: $showPaymentOptionsInOrderForm")
                    }
                }

                // Fetch customers and wait for completion
                val customerList = withContext(Dispatchers.IO) {
                    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                        try {
                            com.pos.cosmos.local.CosmosDatabase.get(context).customerDao().all().map {
                                CustomerData(
                                    id = it.id, name = it.name, address = it.address,
                                    telephone = it.telephone, whatsappNo = it.whatsapp,
                                    email = it.email, companyCode = it.companyCode
                                )
                            }
                        } catch (e: Exception) { emptyList() }
                    } else try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<CustomerData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                allCustomers = customerList
                filteredCustomers = customerList

                // Fetch sales reps and wait for completion
                val salesRepList = withContext(Dispatchers.IO) {
                    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
                        try {
                            com.pos.cosmos.local.CosmosDatabase.get(context).salesRefDao().all().map {
                                SalesRepData(id = it.id, name = it.name)
                            }
                        } catch (e: Exception) { emptyList() }
                    } else try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/salesRef/getAllSalesRef")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<SalesRepData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                allSalesReps = salesRepList

                // Fetch items
                withContext(Dispatchers.Default) {
                    val creditItems = fetchCreditBillItemsWithInvoices(context, token!!)
                    creditItemOptions = creditItems
                    val items = creditItems.map { ci ->
                        ItemOption(
                            id = ci.id, code = ci.code, name = ci.name,
                            sellingPrice = ci.sellingPrice,
                            invoiceId = ci.invoiceId, supplierId = ci.supplierId,
                            totalQty = ci.totalQty,
                            defaultDiscountForPrice = ci.supplierInvoiceDetails.firstOrNull { it.qty > 0 }?.defaultDiscountForPrice ?: 0.0
                        )
                    }
                    availableItems = items
                    filteredItems = items

                    // Paper width comes from config. The receipt template itself is loaded
                    // separately in the background (see the template-preload LaunchedEffect
                    // below) so the (potentially network-bound) template fetch never slows
                    // down this initial page load.
                    val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
                    paperWidthMm = if (use58mm) 58f else 80f
                }

                // Load order if orderNo is provided (from All Orders screen)
                if (orderNoFromParams > 0) {
                    Log.d("CreditBillScreen", "Loading order $orderNoFromParams for update")
                    loadOrderForUpdate(orderNoFromParams, customerId, salesRefId)
                }

                loading = false
                isLoadingCustomers = false
            }

            // Load next bill number
            loadNextBillNo()
        }
    }

    // Preload the receipt/bill template in the background as soon as the page opens, so it
    // is ready by the time the user saves a bill. This runs independently of the `loading`
    // block above, so it never delays the initial render. Cache-first, network fallback.
    LaunchedEffect(token, config) {
        val t = token ?: return@LaunchedEffect
        if (billTemplate != null) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
            val billType = if (use58mm) "android58" else "android"
            val loaded = loadCreditBillTemplate(context, t, billType)
            if (loaded != null && billTemplate == null) {
                billTemplate = loaded
                Log.d("CreditBillScreen", "Bill template preloaded (type=$billType)")
            } else if (loaded == null) {
                Log.w("CreditBillScreen", "Bill template preload failed (type=$billType)")
            }
        }
    }

    LaunchedEffect(config) {
        config?.let {
            hideSalesPersonField = it.hideSalesPersonInCashBill == "Y"
        }
    }

    val grossTotal = cartItems.sumOf { it.amount }
    val discount = discountValue.toDoubleOrNull() ?: 0.0
    val totalReturn = cartTotalReturn(cartItems)
    val netTotal = grossTotal - totalReturn - discount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit Bill", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF0F3460)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFE94560))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF0F3460))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showItemSearch = true },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Add Item")
                                    }
                                    Button(
                                        onClick = { showBarcodeScanner = !showBarcodeScanner },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (showBarcodeScanner) Color(0xFFE94560) else Color(0xFF6366F1)
                                        )
                                    ) {
                                        Icon(
                                            if (showBarcodeScanner) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "Scan",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (showBarcodeScanner) "Close" else "Scan")
                                    }
                                }
                                if (showBarcodeScanner) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    InlineBarcodeScanner(
                                        onBarcodeScanned = { barcode ->
                                            showBarcodeScanner = false
                                            val matched = creditItemOptions.find {
                                                it.barcode == barcode || it.code == barcode
                                            }
                                            if (matched != null) {
                                                if (matched.supplierInvoiceDetails.size > 1) {
                                                    val currentTotalQty = cartItems.filter { it.id == matched.id && !it.isPromoFreeItem }.sumOf { it.quantity }
                                                    val otherItems = cartItems.filter { it.id != matched.id || it.isPromoFreeItem }
                                                    cartItems = otherItems + splitItemAcrossInvoices(
                                                        matched, currentTotalQty + 1,
                                                        applyDefaultDiscountForPrice = config?.setDefaultDiscountForPrice == "Y",
                                                        useLastUpdatedSellingPrice = config?.useLastUpdatedSellingPrice != "N"
                                                    )
                                                } else {
                                                    val existingItem = cartItems.find { it.id == matched.id && !it.isPromoFreeItem }
                                                    cartItems = if (existingItem != null) {
                                                        cartItems.map {
                                                            if (it.id == matched.id && !it.isPromoFreeItem) it.copy(quantity = it.quantity + 1)
                                                            else it
                                                        }
                                                    } else {
                                                        val firstInv = matched.supplierInvoiceDetails.firstOrNull { it.qty > 0 }
                                                        val invDefault = firstInv?.defaultDiscountForPrice ?: 0.0
                                                        val applyDefault = config?.setDefaultDiscountForPrice == "Y" &&
                                                            invDefault > 0 && matched.sellingPrice > 0
                                                        val dv = if (applyDefault) invDefault else 0.0
                                                        val dp = if (applyDefault) (dv / matched.sellingPrice) * 100.0 else 0.0
                                                        cartItems + BillItem(
                                                            id = matched.id, code = matched.code, name = matched.name,
                                                            sellingPrice = matched.sellingPrice, invoiceId = matched.invoiceId,
                                                            supplierId = matched.supplierId, totalQty = matched.totalQty,
                                                            discount = dv, discountValue = dv, discountPercentage = dp
                                                        )
                                                    }
                                                }
                                                schedulePromotionCheck()
                                                Toast.makeText(context, "Added: ${matched.name}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "No item found for: $barcode", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Compute item number for each unique item id
                    val uniqueItemIds = cartItems.map { it.id }.distinct()
                    val itemNumberMap = uniqueItemIds.withIndex().associate { (index, id) -> id to (index + 1) }

                    items(cartItems) { item ->
                        val itemNumber = itemNumberMap[item.id] ?: 0
                        CartItemCard(
                            item = item,
                            itemNumber = itemNumber,
                            onEdit = {
                                editingItemId = item.id
                                // Show total qty across all invoice splits for this item (promo free lines excluded)
                                val allSplits = cartItems.filter { it.id == item.id && !it.isPromoFreeItem }
                                editingQuantity = allSplits.sumOf { it.quantity }.toString()
                                editingPrice = item.sellingPrice.toString()
                                editingDiscountPercentage = item.discountPercentage.toString()
                                editingDiscountValue = item.discountValue.toString()
                                editingFreeIssue = allSplits.sumOf { it.freeIssueQty }.toString()
                                editingReturnQty = allSplits.sumOf { it.returnQty }.let {
                                    if (it > 0.0) formatReturnQty(it) else ""
                                }
                            },
                            onDelete = {
                                if (item.isPromoFreeItem) {
                                    // Rep removed a promo free line: drop it and don't re-nag
                                    cartItems = cartItems.filterNot { it.isPromoFreeItem && it.promotionId == item.promotionId }
                                    promoState.onPromoLineDeleted(item.promotionId)
                                } else {
                                    cartItems = cartItems.filter { it.id != item.id || it.isPromoFreeItem }
                                }
                                schedulePromotionCheck()
                            },
                            showFreeIssue = showFreeIssueInOrderForm == "Y"
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Responsive layout: Row for tablets, Column for phones
                        val configuration = LocalContext.current.resources.configuration
                        val isTablet = configuration.screenWidthDp >= 600

                        // Row 1: Customer and Sales Rep on same line
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Customer Dropdown
                            Box(modifier = Modifier.weight(1f)) {
                                TextField(
                                    value = selectedCustomer,
                                    onValueChange = { filterCustomers(it) },
                                    label = { Text("Customer") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedCustomer = true },
                                    singleLine = true,
                                    readOnly = false
                                )

                                if (expandedCustomer && filteredCustomers.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                            items(filteredCustomers) { customer ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelectCustomer(customer)
                                                        }
                                                        .padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = customer.name ?: "",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (customer.address != null) {
                                                        Text(
                                                            text = customer.address,
                                                            fontSize = 12.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                }
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }

                            // Sales Rep Dropdown - same line as Customer (always visible for credit bills)
                            Box(modifier = Modifier.weight(1f)) {
                                TextField(
                                    value = selectedSalesRep,
                                    onValueChange = { filterSalesReps(it) },
                                    label = { Text("Sales Rep") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSalesRep = true },
                                    singleLine = true,
                                    readOnly = false
                                )

                                if (expandedSalesRep && filteredSalesReps.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                            items(filteredSalesReps) { salesRep ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelectSalesRep(salesRep)
                                                        }
                                                        .padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = salesRep.name ?: "",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Address dropdown - show if config enabled or multiple customers have same name
                        if ((showCustomerAddressInOrderForm == "Y" || customerAddresses.size > 1) && customerAddresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var addressExpanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = selectedAddress,
                                    onValueChange = { },
                                    label = { Text("Customer Address") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { addressExpanded = !addressExpanded },
                                    singleLine = true,
                                    readOnly = true,
                                    trailingIcon = {
                                        Icon(
                                            imageVector = if (addressExpanded) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "Expand",
                                            modifier = Modifier.clickable { addressExpanded = !addressExpanded }
                                        )
                                    }
                                )

                                if (addressExpanded) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 56.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                                            items(customerAddresses) { address ->
                                                Text(
                                                    text = address.ifEmpty { "(No address)" },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelectAddress(address)
                                                            addressExpanded = false
                                                        }
                                                        .padding(12.dp),
                                                    fontSize = 14.sp,
                                                    color = Color.Black
                                                )
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Memo field - show if config enabled
                        if (showMemoInOrderFormAndCreditBill == "Y") {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = memoText,
                                onValueChange = { memoText = it },
                                label = { Text("Memo") },
                                placeholder = { Text("Enter memo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Row 2: Discount %, Discount Value, Invoice Date (BIGGER row)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Discount Percentage - Editable
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Discount (%)",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = if (grossTotal > 0) String.format("%.1f", (discount / grossTotal * 100)) else "0",
                                        onValueChange = { newValue ->
                                            val percentage = newValue.toDoubleOrNull() ?: 0.0
                                            discountValue = (grossTotal * percentage / 100).toString()
                                        },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6366F1),
                                            unfocusedBorderColor = Color(0xFFE0E0E0),
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Discount Value - Editable
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dis. Val",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = String.format("%.2f", discount),
                                        onValueChange = { newValue ->
                                            discountValue = newValue
                                        },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6366F1),
                                            unfocusedBorderColor = Color(0xFFE0E0E0),
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Invoice Date - Read-only
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Invoice Date",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = Color(0xFFF5F5F5),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 14.dp)
                                    ) {
                                        Text(
                                            text = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date()),
                                            fontSize = 9.sp,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 3: Bill No, Gross, Return, Net Total, Pcs, Items (ALL in one line)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CompactField(
                                    label = "Bill No",
                                    value = billNo,
                                    modifier = Modifier.weight(1f),
                                    readOnly = true
                                )
                                CompactField(
                                    label = "Gross",
                                    value = String.format("%.0f", grossTotal),
                                    modifier = Modifier.weight(1f),
                                    readOnly = true
                                )
                                CompactField(
                                    label = "Net Total",
                                    value = String.format("%.0f", netTotal),
                                    modifier = Modifier.weight(1f),
                                    readOnly = true,
                                    isBold = true,
                                    isHighlighted = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (cartItems.isNotEmpty()) {
                                    // Validate customer selection or allow new customer creation
                                    val customerName = selectedCustomer.trim()

                                    // Check if cash bill mode should be enabled (no customer selected and config allows it)
                                    Log.d("CreditBillScreen", "=== BUTTON CLICKED ===")
                                    Log.d("CreditBillScreen", "selectedCustomerId: $selectedCustomerId, customerName: '$customerName'")
                                    Log.d("CreditBillScreen", "allowCashBillsInCreditBillPage value: '$allowCashBillsInCreditBillPage'")
                                    if (selectedCustomerId <= 0 && customerName.isEmpty()) {
                                        Log.d("CreditBillScreen", "No customer selected, checking config...")
                                        if (allowCashBillsInCreditBillPage == "Y") {
                                            // Enable cash bill mode - load cash bill number and show payment dialog
                                            Log.d("CreditBillScreen", "Cash bill mode enabled - no customer, config allows cash bills")
                                            isCashBillMode = true
                                            paymentAmount = "%.2f".format(netTotal)  // Pre-fill with net total
                                            scope.launch {
                                                cashBillNo = loadNextCashBillNo()
                                                Log.d("CreditBillScreen", "Cash bill number loaded: $cashBillNo")
                                                showPaymentMethodDialog = true
                                            }
                                            return@Button
                                        } else {
                                            Toast.makeText(context, "Please select or enter a customer name", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                    }

                                    // If no customer selected but name entered, check config
                                    if (selectedCustomerId <= 0 && customerName.isNotEmpty() && createNewCustomerWhenBillingIfNotExists != "Y") {
                                        Toast.makeText(context, "Please select an existing customer", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    // Reset cash bill mode since we have a customer
                                    isCashBillMode = false

                                    // Check if payment dialog is enabled
                                    Log.d("CreditBillScreen", "showPaymentDialogInCreditBills: $showPaymentDialogInCreditBills")
                                    if (showPaymentDialogInCreditBills == "Y") {
                                        Log.d("CreditBillScreen", "Opening payment method dialog")
                                        showPaymentMethodDialog = true
                                    } else {
                                        Log.d("CreditBillScreen", "Saving bill directly without payment dialog")
                                        scope.launch {
                                            // Save bill to backend
                                            Log.d("CreditBillScreen", "User clicked 'Print & Save Bill' button")
                                            Log.d("CreditBillScreen", "Cart has ${cartItems.size} items")
                                            val result = withContext(Dispatchers.Default) {
                                                saveBill(
                                                    context,
                                                    token!!,
                                                    cartItems,
                                                    selectedCustomer,
                                                    selectedCustomerId,
                                                    selectedSalesRep,
                                                    selectedSalesRepId,
                                                    discount,
                                                    "CREDIT",
                                                    billNo,
                                                    orderNoFromParams,
                                                    memo = memoText
                                                )
                                            }

                                            // Check if result is an error
                                            if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                                Log.e("CreditBillScreen", "Bill save failed: $result")
                                                // Extract and show error message to user
                                                errorMessage = extractErrorMessage(result)
                                                showErrorDialog = true
                                            } else {
                                                // result contains the bill number
                                                val savedBillNo = result ?: ""
                                                Log.d("CreditBillScreen", "Bill saved successfully, billNo: $savedBillNo")

                                                // Print receipt using PDF - use singleton PrinterService (same as CashBillScreen)
                                                try {
                                                    val printerService = PrinterService.getInstance()

                                                    // Check if already connected
                                                    val isAlreadyConnected = printerService.isConnected()
                                                    Log.d("CreditBillScreen", "Printer already connected: $isAlreadyConnected")

                                                    if (!isAlreadyConnected) {
                                                        // Try to connect if not already connected.
                                                        // IMPORTANT: reading bondedDevices / connecting needs the
                                                        // BLUETOOTH_CONNECT runtime permission on Android 12+. If it
                                                        // is not granted this throws SecurityException. Keep it in its
                                                        // own try/catch so a Bluetooth failure can NEVER abort the
                                                        // print-preview / share PDF generation below.
                                                        try {
                                                            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                                            if (bluetoothAdapter != null) {
                                                                val pairedDevices = bluetoothAdapter.bondedDevices.toList()
                                                                if (pairedDevices.isNotEmpty()) {
                                                                    val printerDevice = pairedDevices.firstOrNull()
                                                                    if (printerDevice != null) {
                                                                        Log.d("CreditBillScreen", "Connecting to printer: ${printerDevice.name}")
                                                                        printerService.connectDevice(printerDevice)
                                                                    }
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.w("CreditBillScreen", "Printer auto-connect skipped (preview/share still proceeds): ${e.message}")
                                                        }
                                                    }

                                                    if (printerService.isConnected()) {
                                                        Log.d("CreditBillScreen", "Printer connected, preparing to print")
                                                        // Build receipt data from cached template
                                                        if (billTemplate != null) {
                                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                                            val receiptItems = mergeCartItemsForReceipt(cartItems)
                                                            val receiptData = buildReceiptFromTemplate(
                                                                billTemplate!!,
                                                                receiptItems,
                                                                selectedCustomer.ifEmpty { "CASH" },
                                                                selectedSalesRep.ifEmpty { "N/A" },
                                                                cartItems.sumOf { it.amount },
                                                                discount,
                                                                netTotal,
                                                                0.0,
                                                                savedBillNo
                                                            ).copy(
                                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                                totalItemDiscount = totalLineItemDiscount(cartItems),
                                                                returnItems = cartReturnReceiptItems(cartItems),
                                                                totalReturn = cartTotalReturn(cartItems)
                                                            )

                                                            Log.d("CreditBillScreen", "Printing receipt...")
                                                            val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                            Log.d("CreditBillScreen", "Print completed - keeping connection alive")
                                                            // DO NOT disconnect - keep connection for next print
                                                            if (printedFile != null) {
                                                                lastReceiptPdf = printedFile
                                                                if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                                    previewPdfFile = printedFile
                                                                }
                                                            }
                                                        } else {
                                                            Log.e("CreditBillScreen", "Bill template not available, cannot print")
                                                        }
                                                    } else {
                                                        Log.e("CreditBillScreen", "Printer not connected. Please connect from Printer screen first.")
                                                        // Even without a printer, generate the PDF so the
                                                        // print-preview / share buttons work.
                                                        val showPreview = config?.showReceiptPrintPreviewAndroidApp == "Y"
                                                        val enableShare = config?.enableShareAndroidApp == "Y"
                                                        if (billTemplate != null && (showPreview || enableShare)) {
                                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                                            val receiptItems = mergeCartItemsForReceipt(cartItems)
                                                            val receiptData = buildReceiptFromTemplate(
                                                                billTemplate!!,
                                                                receiptItems,
                                                                selectedCustomer.ifEmpty { "CASH" },
                                                                selectedSalesRep.ifEmpty { "N/A" },
                                                                cartItems.sumOf { it.amount },
                                                                discount,
                                                                netTotal,
                                                                0.0,
                                                                savedBillNo
                                                            ).copy(
                                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                                totalItemDiscount = totalLineItemDiscount(cartItems),
                                                                returnItems = cartReturnReceiptItems(cartItems),
                                                                totalReturn = cartTotalReturn(cartItems)
                                                            )
                                                            val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                            lastReceiptPdf = pdfFile
                                                            if (showPreview) {
                                                                previewPdfFile = pdfFile
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("CreditBillScreen", "Print error: ${e.message}", e)
                                                    e.printStackTrace()
                                                }

                                                // Reset form
                                                cartItems = emptyList(); clearCreditBillCache(context)
                                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                successMessage = "Bill #${result ?: ""} saved successfully!"
                                showSuccessDialog = true
                                                selectedCustomer = ""
                                                selectedCustomerId = 0L
                                                selectedSalesRep = ""
                                                selectedSalesRepId = 0L
                                                discountValue = "0"
                                                memoText = ""

                                                // Load next bill number
                                                loadNextBillNo()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            // Allow saving a return-only bill (net total <= 0) only when the
                            // return feature is enabled and there are actual return items.
                            enabled = cartItems.isNotEmpty() && (netTotal > 0 ||
                                (config?.showReturnQtyItemWiseInImageBillsAndroidApp == "Y" &&
                                    cartItems.any { it.returnQty > 0.0 }))
                        ) {
                            Text("Print & Save Bill")
                        }
                    }
                }
            }
        }
    }

    // Promotion Offer Dialog
    if (showPromoDialog && promoOffers.isNotEmpty()) {
        PromotionOfferDialog(
            offers = promoOffers,
            onApply = { offer ->
                cartItems = applyOfferToLines(offer, cartItems)
                promoOffers = promoState.onApplied(offer, promoOffers)
                if (promoOffers.isEmpty()) showPromoDialog = false
            },
            onDecline = { offer ->
                promoOffers = promoState.onDeclined(offer, promoOffers)
                if (promoOffers.isEmpty()) showPromoDialog = false
            },
            onDeclineAll = {
                promoState.declineAll(promoOffers)
                promoOffers = emptyList()
                showPromoDialog = false
            }
        )
    }

    if (showItemSearch) {
        AlertDialog(
            onDismissRequest = { showItemSearch = false },
            title = { Text("Search Items") },
            text = {
                Column {
                    TextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            filteredItems = if (query.isEmpty()) {
                                availableItems
                            } else {
                                availableItems.filter {
                                    it.name.contains(query, ignoreCase = true) ||
                                    it.code.contains(query, ignoreCase = true)
                                }
                            }
                        },
                        label = { Text("Search by name or code") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredItems.take(20)) { item ->
                            ItemRow(
                                item = item,
                                onClick = {
                                    val creditItem = creditItemOptions.find { it.id == item.id }
                                    if (creditItem != null && creditItem.supplierInvoiceDetails.size > 1) {
                                        // Multiple invoices: re-split with incremented total qty
                                        val currentTotalQty = cartItems.filter { it.id == item.id && !it.isPromoFreeItem }.sumOf { it.quantity }
                                        val otherItems = cartItems.filter { it.id != item.id || it.isPromoFreeItem }
                                        cartItems = otherItems + splitItemAcrossInvoices(
                                            creditItem, currentTotalQty + 1,
                                            applyDefaultDiscountForPrice = config?.setDefaultDiscountForPrice == "Y",
                                            useLastUpdatedSellingPrice = config?.useLastUpdatedSellingPrice != "N"
                                        )
                                    } else {
                                        val existingItem = cartItems.find { it.id == item.id && !it.isPromoFreeItem }
                                        cartItems = if (existingItem != null) {
                                            cartItems.map {
                                                if (it.id == item.id && !it.isPromoFreeItem) it.copy(quantity = it.quantity + 1)
                                                else it
                                            }
                                        } else {
                                            val firstInv = creditItem?.supplierInvoiceDetails?.firstOrNull { it.qty > 0 }
                                            val applyDefault = config?.setDefaultDiscountForPrice == "Y" &&
                                                ((firstInv?.defaultDiscountForPrice ?: 0.0) > 0) && item.sellingPrice > 0
                                            val dv = if (applyDefault) firstInv!!.defaultDiscountForPrice else 0.0
                                            val dp = if (applyDefault) (dv / item.sellingPrice) * 100.0 else 0.0
                                            cartItems + BillItem(
                                                id = item.id,
                                                code = item.code,
                                                name = item.name,
                                                sellingPrice = item.sellingPrice,
                                                invoiceId = item.invoiceId,
                                                supplierId = item.supplierId,
                                                totalQty = item.totalQty,
                                                discount = dv,
                                                discountValue = dv,
                                                discountPercentage = dp
                                            )
                                        }
                                    }
                                    schedulePromotionCheck()
                                    searchQuery = ""
                                    showItemSearch = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showItemSearch = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (editingItemId != null) {
        val editingItem = cartItems.find { it.id == editingItemId && !it.isPromoFreeItem }
        AlertDialog(
            onDismissRequest = { editingItemId = null },
            title = { Text("Edit ${editingItem?.name ?: "Item"}") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val q = (editingQuantity.toIntOrNull() ?: 1) - 1
                                // Allow going to 0 for pure returns when the return feature is on.
                                val minQty = if (config?.showReturnQtyItemWiseInImageBillsAndroidApp == "Y") 0 else 1
                                if (q >= minQty) editingQuantity = q.toString()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        OutlinedTextField(
                            value = editingQuantity,
                            onValueChange = { editingQuantity = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val q = (editingQuantity.toIntOrNull() ?: 0) + 1
                                editingQuantity = q.toString()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingPrice,
                        onValueChange = { editingPrice = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEditSellingPrice
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingDiscountPercentage,
                        onValueChange = {
                            editingDiscountPercentage = it
                            if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                val price = editingPrice.toDoubleOrNull() ?: 0.0
                                val percentage = it.toDoubleOrNull() ?: 0.0
                                editingDiscountValue = ((percentage * price) / 100).toString()
                            }
                        },
                        label = { Text("Discount %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingDiscountValue,
                        onValueChange = {
                            editingDiscountValue = it
                            if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                val price = editingPrice.toDoubleOrNull() ?: 0.0
                                val discValue = it.toDoubleOrNull() ?: 0.0
                                if (price > 0) {
                                    editingDiscountPercentage = ((discValue / price) * 100).toString()
                                }
                            }
                        },
                        label = { Text("Discount Value") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Free Issue Qty (only show if config is enabled)
                    if (showFreeIssueInOrderForm == "Y") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editingFreeIssue,
                            onValueChange = { editingFreeIssue = it },
                            label = { Text("Free Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Return Qty (item-wise) — only when the config is enabled
                    if (config?.showReturnQtyItemWiseInImageBillsAndroidApp == "Y") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val rq = (editingReturnQty.toDoubleOrNull() ?: 0.0) - 1.0
                                    editingReturnQty = if (rq > 0.0) formatReturnQty(rq) else ""
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Return", tint = Color.White)
                            }
                            OutlinedTextField(
                                value = editingReturnQty,
                                onValueChange = { editingReturnQty = it },
                                label = { Text("Return Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val rq = (editingReturnQty.toDoubleOrNull() ?: 0.0) + 1.0
                                    editingReturnQty = formatReturnQty(rq)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Return", tint = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editingQuantity.toIntOrNull() ?: 1
                        val price = editingPrice.toDoubleOrNull() ?: editingItem?.sellingPrice ?: 0.0
                        val discPercentage = editingDiscountPercentage.toDoubleOrNull() ?: 0.0
                        val discValue = editingDiscountValue.toDoubleOrNull() ?: 0.0
                        val freeQty = editingFreeIssue.toDoubleOrNull() ?: 0.0
                        val retQty = editingReturnQty.toDoubleOrNull() ?: 0.0
                        val editedId = editingItemId

                        // Upward-only price edits: never below the current selling price
                        val currentPrice = editingItem?.sellingPrice ?: 0.0
                        if (sellingPriceUpwardOnly && price < currentPrice) {
                            errorMessage = "Selling Price can not be less than ${String.format("%.2f", currentPrice)}"
                            showErrorDialog = true
                            return@Button
                        }

                        // Keep the line if there is either a sale qty or a return qty.
                        if (qty > 0 || retQty > 0.0) {
                            val creditItem = creditItemOptions.find { it.id == editedId }
                            if (qty > 0 && creditItem != null && creditItem.supplierInvoiceDetails.size > 1) {
                                // Re-split across invoices, applying the user-entered price to every row
                                val otherItems = cartItems.filter { it.id != editedId || it.isPromoFreeItem }
                                cartItems = otherItems + splitItemAcrossInvoices(
                                    creditItem, qty, discValue, discPercentage, freeQty,
                                    overridePrice = price,
                                    applyDefaultDiscountForPrice = config?.setDefaultDiscountForPrice == "Y",
                                    useLastUpdatedSellingPrice = config?.useLastUpdatedSellingPrice != "N"
                                )
                            } else {
                                cartItems = cartItems.map {
                                    if (it.id == editedId && !it.isPromoFreeItem) it.copy(
                                        quantity = qty,
                                        sellingPrice = price,
                                        unitPrice = price,
                                        discountPercentage = discPercentage,
                                        discountValue = discValue,
                                        discount = discValue,
                                        freeIssueQty = freeQty
                                    )
                                    else it
                                }
                            }
                            // Attach the total return qty for this item to its first cart line
                            // (0 on the rest), so per-item return totals are unambiguous when the
                            // item is split across multiple invoice lines.
                            if (config?.showReturnQtyItemWiseInImageBillsAndroidApp == "Y") {
                                var assigned = false
                                cartItems = cartItems.map {
                                    if (it.id == editedId && !it.isPromoFreeItem) {
                                        if (!assigned) { assigned = true; it.copy(returnQty = retQty) }
                                        else it.copy(returnQty = 0.0)
                                    } else it
                                }
                            }
                            schedulePromotionCheck()
                        }
                        editingItemId = null
                        editingQuantity = ""
                        editingPrice = ""
                        editingDiscountPercentage = ""
                        editingDiscountValue = ""
                        editingFreeIssue = ""
                        editingReturnQty = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Payment Method Selection Dialog
    if (showPaymentMethodDialog) {
        AlertDialog(
            onDismissRequest = {
                // Only allow closing if not in cash bill mode
                if (!isCashBillMode) {
                    showPaymentMethodDialog = false
                }
            },
            title = {
                Text(if (isCashBillMode) "Select Payment Method (Cash Bill)" else "Select Payment Method")
            },
            text = {
                Column {
                    // Display Order Payment Option if available
                    if (showPaymentOptionsInOrderForm == "Y" && orderPaymentOption.isNotEmpty()) {
                        Text(
                            text = "Order Payment Option: $orderPaymentOption",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            showPaymentMethodDialog = false
                            showCashPaymentDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Cash Payment")
                    }
                    Button(
                        onClick = {
                            showPaymentMethodDialog = false
                            showChequePaymentDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Cheque Payment")
                    }
                    Button(
                        onClick = {
                            showPaymentMethodDialog = false
                            showCardPaymentDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text("Card Payment")
                    }

                    // Split-payment entry â€” visible only when the corresponding config is on.
                    if (enableMultiplePaymentSameTimeInBilling == "Y") {
                        Button(
                            onClick = {
                                showPaymentMethodDialog = false
                                showSplitPaymentDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                        ) {
                            Text("Split Payment")
                        }
                    }

                    // Show Cancel button in cash bill mode to allow going back
                    if (isCashBillMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showPaymentMethodDialog = false
                                isCashBillMode = false
                                cashBillNo = ""
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            },
            confirmButton = {
                // Only show "Skip Payment" button when NOT in cash bill mode
                if (!isCashBillMode) {
                    Button(
                        onClick = {
                            showPaymentMethodDialog = false
                            // Save without payment and print
                            scope.launch {
                                // Capture values before save
                                val itemsForPrint = cartItems.toList()
                                val customerForPrint = selectedCustomer
                                val salesRepForPrint = selectedSalesRep
                                val discountForPrint = discount
                                val netTotalForPrint = netTotal
                                val templateForPrint = billTemplate

                                val result = withContext(Dispatchers.Default) {
                                    saveBill(
                                        context,
                                        token!!,
                                        cartItems,
                                        selectedCustomer,
                                        selectedCustomerId,
                                        selectedSalesRep,
                                        selectedSalesRepId,
                                        discount,
                                        "CREDIT",
                                        billNo,
                                        orderNoFromParams,
                                        memo = memoText
                                    )
                                }

                                // Check if result is an error
                                if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                    Log.e("CreditBillScreen", "Bill save failed: $result")
                                    errorMessage = extractErrorMessage(result)
                                    showErrorDialog = true
                                    return@launch
                                }

                                // Print receipt after saving
                                if (result != null) {
                                    val savedBillNo = result
                                    try {
                                        val printerService = PrinterService.getInstance()
                                        if (templateForPrint != null) {
                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                            val receiptItems = mergeCartItemsForReceipt(itemsForPrint)
                                            val receiptData = buildReceiptFromTemplate(
                                                templateForPrint,
                                                receiptItems,
                                                customerForPrint.ifEmpty { "CASH" },
                                                salesRepForPrint.ifEmpty { "N/A" },
                                                itemsForPrint.sumOf { it.amount },
                                                discountForPrint,
                                                netTotalForPrint,
                                                0.0,
                                                savedBillNo
                                            ).copy(
                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                totalItemDiscount = totalLineItemDiscount(itemsForPrint),
                                                returnItems = cartReturnReceiptItems(itemsForPrint),
                                                totalReturn = cartTotalReturn(itemsForPrint)
                                            )
                                            if (printerService.isConnected()) {
                                                Log.d("CreditBillScreen", "Printing from Skip Payment...")
                                                val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                if (printedFile != null) {
                                                    lastReceiptPdf = printedFile
                                                    if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                        previewPdfFile = printedFile
                                                    }
                                                }
                                            } else if (config?.showReceiptPrintPreviewAndroidApp == "Y" || config?.enableShareAndroidApp == "Y") {
                                                // No printer connected: still generate the PDF so
                                                // the print-preview / share buttons work.
                                                val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                lastReceiptPdf = pdfFile
                                                if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                    previewPdfFile = pdfFile
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CreditBillScreen", "Print error: ${e.message}")
                                    }
                                }

                                // Clear form after save
                                cartItems = emptyList(); clearCreditBillCache(context)
                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                successMessage = "Bill #${result ?: ""} saved successfully!"
                                showSuccessDialog = true
                                selectedCustomer = ""
                                selectedCustomerId = 0L
                                selectedSalesRep = ""
                                selectedSalesRepId = 0L
                                discountValue = "0"
                                memoText = ""
                                loadNextBillNo()
                            }
                        }
                    ) {
                        Text("Skip Payment and Create Bill")
                    }
                }
            }
        )
    }

    // Split Payment Dialog â€” bill saved with multiple payment rows in one call.
    if (showSplitPaymentDialog) {
        SplitPaymentDialog(
            billTotal = netTotal,
            onDismiss = { showSplitPaymentDialog = false },
            onConfirm = { rows ->
                showSplitPaymentDialog = false
                val itemsForPrint = cartItems.toList()
                val customerForPrint = selectedCustomer
                val salesRepForPrint = selectedSalesRep
                val discountForPrint = discount
                val netTotalForPrint = netTotal
                val templateForPrint = billTemplate
                val totalPaid = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                // Group split rows by payment type so the receipt shows each
                // method (Cash / Cheque / Card) with its total amount.
                val paymentBreakdown = rows
                    .groupBy { it.type.trim().lowercase() }
                    .mapNotNull { (type, rs) ->
                        val amt = rs.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                        if (amt <= 0) return@mapNotNull null
                        val label = when (type) {
                            "cheque" -> "Cheque:"
                            "card" -> "Card:"
                            else -> "Cash:"
                        }
                        label to amt
                    }
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        saveBillWithSplitPayments(
                            context = context,
                            token = token!!,
                            items = cartItems,
                            customer = selectedCustomer,
                            customerId = selectedCustomerId,
                            salesRep = selectedSalesRep,
                            salesRepId = selectedSalesRepId,
                            discount = discount,
                            billType = "CREDIT",
                            billNumber = billNo,
                            payments = rows,
                            orderNo = orderNoFromParams,
                            memo = memoText
                        )
                    }
                    if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                        Log.e("CreditBillScreen", "Split payment save failed: $result")
                        errorMessage = extractErrorMessage(result)
                        showErrorDialog = true
                        return@launch
                    }
                    if (result != null) {
                        val savedBillNo = result
                        try {
                            val printerService = PrinterService.getInstance()
                            val previewCfg = config?.showReceiptPrintPreviewAndroidApp
                            val shareCfg = config?.enableShareAndroidApp
                            Log.d("CreditBillScreen", "Split receipt: template=${templateForPrint != null}, printerConnected=${printerService.isConnected()}, previewCfg=$previewCfg, shareCfg=$shareCfg, breakdown=$paymentBreakdown")
                            if (templateForPrint != null) {
                                val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                val receiptItems = mergeCartItemsForReceipt(itemsForPrint)
                                val receiptData = buildReceiptFromTemplate(
                                    templateForPrint,
                                    receiptItems,
                                    customerForPrint.ifEmpty { "CASH" },
                                    salesRepForPrint.ifEmpty { "N/A" },
                                    itemsForPrint.sumOf { it.amount },
                                    discountForPrint,
                                    netTotalForPrint,
                                    totalPaid,
                                    savedBillNo
                                ).copy(
                                    showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                    totalItemDiscount = totalLineItemDiscount(itemsForPrint),
                                    paymentBreakdown = paymentBreakdown,
                                    returnItems = cartReturnReceiptItems(itemsForPrint),
                                    totalReturn = cartTotalReturn(itemsForPrint)
                                )
                                // Generate the PDF up-front so preview/share never depend on the
                                // printer succeeding. Printing (if connected) is best-effort.
                                val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                lastReceiptPdf = pdfFile
                                if (previewCfg == "Y") {
                                    previewPdfFile = pdfFile
                                }
                                if (printerService.isConnected()) {
                                    try {
                                        printerService.printPdfFile(pdfFile)
                                    } catch (pe: Exception) {
                                        Log.e("CreditBillScreen", "Split print failed (file kept for preview/share): ${pe.message}", pe)
                                    }
                                }
                                Log.d("CreditBillScreen", "Split receipt PDF ready at ${pdfFile.absolutePath}; previewSet=${previewCfg == "Y"}, shareAvailable=${shareCfg == "Y"}")
                            } else {
                                Log.w("CreditBillScreen", "Split receipt skipped: billTemplate was null")
                            }
                        } catch (e: Exception) {
                            Log.e("CreditBillScreen", "Split print error: ${e.message}", e)
                        }
                    }
                    cartItems = emptyList(); clearCreditBillCache(context)
                    promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                    successMessage = "Bill #${result ?: ""} saved with ${rows.size} payment(s)."
                    showSuccessDialog = true
                    selectedCustomer = ""
                    selectedCustomerId = 0L
                    selectedSalesRep = ""
                    selectedSalesRepId = 0L
                    discountValue = "0"
                    memoText = ""
                    paymentAmount = ""
                    loadNextBillNo()
                }
            }
        )
    }

    // Cash Payment Dialog
    if (showCashPaymentDialog) {
        AlertDialog(
            onDismissRequest = {
                showCashPaymentDialog = false
                if (isCashBillMode) {
                    // Go back to payment method selection in cash bill mode
                    showPaymentMethodDialog = true
                }
            },
            title = { Text(if (isCashBillMode) "Cash Payment (Cash Bill)" else "Cash Payment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text(if (isCashBillMode) "Cash Received" else "Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashTRN,
                        onValueChange = { cashTRN = it },
                        label = { Text("TRN (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Bill Total: %.2f".format(netTotal))
                    if (isCashBillMode) {
                        // Show Change (amount received - bill total) for cash bill mode
                        val receivedAmount = paymentAmount.toDoubleOrNull() ?: 0.0
                        val change = if (receivedAmount >= netTotal) receivedAmount - netTotal else 0.0
                        Text("Change: %.2f".format(change))
                        if (receivedAmount < netTotal) {
                            Text(
                                "Cash received must be at least %.2f".format(netTotal),
                                color = androidx.compose.ui.graphics.Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text("Balance: %.2f".format(netTotal - (paymentAmount.toDoubleOrNull() ?: 0.0)))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val amount = paymentAmount.toDoubleOrNull()
                            if (amount != null && amount > 0) {
                                // Validate payment amount in cash bill mode
                                if (isCashBillMode && amount < netTotal) {
                                    Toast.makeText(context, "Cash received cannot be less than the bill total", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // Capture values before save
                                val itemsForPrint = cartItems.toList()
                                // Use empty customer for cash bill mode to hide customer line in receipt
                                val customerForPrint = if (isCashBillMode) "" else selectedCustomer
                                val salesRepForPrint = selectedSalesRep
                                val discountForPrint = discount
                                val netTotalForPrint = netTotal
                                val templateForPrint = billTemplate

                                // Use CASH bill type and cashBillNo when in cash bill mode
                                val billTypeToUse = if (isCashBillMode) "CASH" else "CREDIT"
                                val billNoToUse = if (isCashBillMode) cashBillNo else billNo
                                val customerIdToUse = if (isCashBillMode) 0L else selectedCustomerId

                                val result = withContext(Dispatchers.Default) {
                                    saveBillWithPayment(
                                        context,
                                        token!!,
                                        cartItems,
                                        if (isCashBillMode) "" else selectedCustomer,
                                        customerIdToUse,
                                        selectedSalesRep,
                                        selectedSalesRepId,
                                        discount,
                                        billTypeToUse,
                                        billNoToUse,
                                        "cash",
                                        amount,
                                        cashTRN = cashTRN,
                                        orderNo = orderNoFromParams,
                                        memo = memoText
                                    )
                                }

                                // Check if result is an error
                                if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                    Log.e("CreditBillScreen", "Bill save with cash payment failed: $result")
                                    errorMessage = extractErrorMessage(result)
                                    showErrorDialog = true
                                    showCashPaymentDialog = false
                                    return@launch
                                }

                                // Print receipt after saving
                                if (result != null) {
                                    val savedBillNo = result
                                    try {
                                        val printerService = PrinterService.getInstance()
                                        if (templateForPrint != null) {
                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                            val receiptItems = mergeCartItemsForReceipt(itemsForPrint)
                                            val receiptData = buildReceiptFromTemplate(
                                                templateForPrint,
                                                receiptItems,
                                                customerForPrint.ifEmpty { "CASH" },
                                                salesRepForPrint.ifEmpty { "N/A" },
                                                itemsForPrint.sumOf { it.amount },
                                                discountForPrint,
                                                netTotalForPrint,
                                                amount,
                                                savedBillNo
                                            ).copy(
                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                totalItemDiscount = totalLineItemDiscount(itemsForPrint),
                                                returnItems = cartReturnReceiptItems(itemsForPrint),
                                                totalReturn = cartTotalReturn(itemsForPrint)
                                            )
                                            if (printerService.isConnected()) {
                                                Log.d("CreditBillScreen", "Printing from Cash Payment Dialog...")
                                                val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                if (printedFile != null) {
                                                    lastReceiptPdf = printedFile
                                                    if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                        previewPdfFile = printedFile
                                                    }
                                                }
                                            } else if (config?.showReceiptPrintPreviewAndroidApp == "Y" || config?.enableShareAndroidApp == "Y") {
                                                // No printer connected: still generate the PDF so
                                                // the print-preview / share buttons work.
                                                val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                lastReceiptPdf = pdfFile
                                                if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                    previewPdfFile = pdfFile
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CreditBillScreen", "Print error: ${e.message}")
                                    }
                                }

                                // Clear form
                                showCashPaymentDialog = false
                                cartItems = emptyList(); clearCreditBillCache(context)
                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                successMessage = "Bill #${result ?: ""} saved successfully!"
                                showSuccessDialog = true
                                selectedCustomer = ""
                                selectedCustomerId = 0L
                                selectedSalesRep = ""
                                selectedSalesRepId = 0L
                                discountValue = "0"
                                memoText = ""
                                paymentAmount = ""
                                cashTRN = ""
                                isCashBillMode = false
                                cashBillNo = ""
                                loadNextBillNo()
                            }
                        }
                    }
                ) {
                    Text("Save Bill & Pay")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showCashPaymentDialog = false
                    if (isCashBillMode) {
                        // Go back to payment method selection in cash bill mode
                        showPaymentMethodDialog = true
                    }
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cheque Payment Dialog
    if (showChequePaymentDialog) {
        AlertDialog(
            onDismissRequest = {
                showChequePaymentDialog = false
                if (isCashBillMode) {
                    // Go back to payment method selection in cash bill mode
                    showPaymentMethodDialog = true
                }
            },
            title = { Text(if (isCashBillMode) "Cheque Payment (Cash Bill)" else "Cheque Payment") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chequeNumber,
                        onValueChange = { chequeNumber = it },
                        label = { Text("Cheque Number *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chequeDate,
                        onValueChange = { },
                        label = { Text("Cheque Date") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChequeDatePicker = true },
                        trailingIcon = {
                            IconButton(onClick = { showChequeDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select date")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bankAndBranch,
                        onValueChange = { bankAndBranch = it },
                        label = { Text("Bank & Branch") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chequeTRN,
                        onValueChange = { chequeTRN = it },
                        label = { Text("TRN (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Bill Total: %.2f".format(netTotal))
                    if (isCashBillMode) {
                        val receivedAmount = paymentAmount.toDoubleOrNull() ?: 0.0
                        if (receivedAmount < netTotal) {
                            Text(
                                "Amount must be at least %.2f".format(netTotal),
                                color = androidx.compose.ui.graphics.Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text("Balance: %.2f".format(netTotal - (paymentAmount.toDoubleOrNull() ?: 0.0)))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val amount = paymentAmount.toDoubleOrNull()
                            if (amount != null && amount > 0 && chequeNumber.isNotEmpty()) {
                                // Validate payment amount in cash bill mode
                                if (isCashBillMode && amount < netTotal) {
                                    Toast.makeText(context, "Cheque amount cannot be less than the bill total", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // Capture values before save
                                val itemsForPrint = cartItems.toList()
                                // Use empty customer for cash bill mode to hide customer line in receipt
                                val customerForPrint = if (isCashBillMode) "" else selectedCustomer
                                val salesRepForPrint = selectedSalesRep
                                val discountForPrint = discount
                                val netTotalForPrint = netTotal
                                val templateForPrint = billTemplate

                                // Use CASH bill type and cashBillNo when in cash bill mode
                                val billTypeToUse = if (isCashBillMode) "CASH" else "CREDIT"
                                val billNoToUse = if (isCashBillMode) cashBillNo else billNo
                                val customerIdToUse = if (isCashBillMode) 0L else selectedCustomerId

                                val result = withContext(Dispatchers.Default) {
                                    saveBillWithPayment(
                                        context,
                                        token!!,
                                        cartItems,
                                        if (isCashBillMode) "" else selectedCustomer,
                                        customerIdToUse,
                                        selectedSalesRep,
                                        selectedSalesRepId,
                                        discount,
                                        billTypeToUse,
                                        billNoToUse,
                                        "cheque",
                                        amount,
                                        chequeNumber = chequeNumber,
                                        chequeDate = chequeDate,
                                        bankAndBranch = bankAndBranch,
                                        accountNumber = accountNumber,
                                        trn = chequeTRN,
                                        orderNo = orderNoFromParams,
                                        memo = memoText
                                    )
                                }

                                // Check if result is an error
                                if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                    Log.e("CreditBillScreen", "Bill save with cheque payment failed: $result")
                                    errorMessage = extractErrorMessage(result)
                                    showErrorDialog = true
                                    showChequePaymentDialog = false
                                    return@launch
                                }

                                // Print receipt after saving
                                if (result != null) {
                                    val savedBillNo = result
                                    try {
                                        val printerService = PrinterService.getInstance()
                                        if (templateForPrint != null) {
                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                            val receiptItems = mergeCartItemsForReceipt(itemsForPrint)
                                            val receiptData = buildReceiptFromTemplate(
                                                templateForPrint,
                                                receiptItems,
                                                customerForPrint.ifEmpty { "CASH" },
                                                salesRepForPrint.ifEmpty { "N/A" },
                                                itemsForPrint.sumOf { it.amount },
                                                discountForPrint,
                                                netTotalForPrint,
                                                amount,
                                                savedBillNo
                                            ).copy(
                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                totalItemDiscount = totalLineItemDiscount(itemsForPrint),
                                                returnItems = cartReturnReceiptItems(itemsForPrint),
                                                totalReturn = cartTotalReturn(itemsForPrint)
                                            )
                                            if (printerService.isConnected()) {
                                                Log.d("CreditBillScreen", "Printing from Cheque Payment Dialog...")
                                                val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                if (printedFile != null) {
                                                    lastReceiptPdf = printedFile
                                                    if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                        previewPdfFile = printedFile
                                                    }
                                                }
                                            } else if (config?.showReceiptPrintPreviewAndroidApp == "Y" || config?.enableShareAndroidApp == "Y") {
                                                // No printer connected: still generate the PDF so
                                                // the print-preview / share buttons work.
                                                val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                lastReceiptPdf = pdfFile
                                                if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                    previewPdfFile = pdfFile
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CreditBillScreen", "Print error: ${e.message}")
                                    }
                                }

                                // Clear form
                                showChequePaymentDialog = false
                                cartItems = emptyList(); clearCreditBillCache(context)
                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                successMessage = "Bill #${result ?: ""} saved successfully!"
                                showSuccessDialog = true
                                selectedCustomer = ""
                                selectedCustomerId = 0L
                                selectedSalesRep = ""
                                selectedSalesRepId = 0L
                                discountValue = "0"
                                memoText = ""
                                paymentAmount = ""
                                chequeNumber = ""
                                chequeDate = ""
                                bankAndBranch = ""
                                accountNumber = ""
                                chequeTRN = ""
                                isCashBillMode = false
                                cashBillNo = ""
                                loadNextBillNo()
                            }
                        }
                    }
                ) {
                    Text("Save Bill & Pay")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showChequePaymentDialog = false
                    if (isCashBillMode) {
                        // Go back to payment method selection in cash bill mode
                        showPaymentMethodDialog = true
                    }
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cheque Date Picker Dialog
    if (showChequeDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showChequeDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        chequeDate = dateFormatter.format(Date(millis))
                    }
                    showChequeDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChequeDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Card Payment Dialog
    if (showCardPaymentDialog) {
        AlertDialog(
            onDismissRequest = {
                showCardPaymentDialog = false
                if (isCashBillMode) {
                    // Go back to payment method selection in cash bill mode
                    showPaymentMethodDialog = true
                }
            },
            title = { Text(if (isCashBillMode) "Card Payment (Cash Bill)" else "Card Payment") },
            text = {
                Column {
                    OutlinedTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cardLastFourDigits,
                        onValueChange = { cardLastFourDigits = it },
                        label = { Text("Card Last 4 Digits") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cardTRN,
                        onValueChange = { cardTRN = it },
                        label = { Text("TRN (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Bill Total: %.2f".format(netTotal))
                    if (isCashBillMode) {
                        val receivedAmount = paymentAmount.toDoubleOrNull() ?: 0.0
                        if (receivedAmount < netTotal) {
                            Text(
                                "Amount must be at least %.2f".format(netTotal),
                                color = androidx.compose.ui.graphics.Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text("Balance: %.2f".format(netTotal - (paymentAmount.toDoubleOrNull() ?: 0.0)))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val amount = paymentAmount.toDoubleOrNull()
                            if (amount != null && amount > 0) {
                                if (cardLastFourDigits.isEmpty()) {
                                    Toast.makeText(context, "Please enter card last 4 digits", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // Validate payment amount in cash bill mode
                                if (isCashBillMode && amount < netTotal) {
                                    Toast.makeText(context, "Card amount cannot be less than the bill total", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                // Capture values before save
                                val itemsForPrint = cartItems.toList()
                                // Use empty customer for cash bill mode to hide customer line in receipt
                                val customerForPrint = if (isCashBillMode) "" else selectedCustomer
                                val salesRepForPrint = selectedSalesRep
                                val discountForPrint = discount
                                val netTotalForPrint = netTotal
                                val templateForPrint = billTemplate

                                // Use CASH bill type and cashBillNo when in cash bill mode
                                val billTypeToUse = if (isCashBillMode) "CASH" else "CREDIT"
                                val billNoToUse = if (isCashBillMode) cashBillNo else billNo
                                val customerIdToUse = if (isCashBillMode) 0L else selectedCustomerId

                                val result = withContext(Dispatchers.Default) {
                                    saveBillWithPayment(
                                        context,
                                        token!!,
                                        cartItems,
                                        if (isCashBillMode) "" else selectedCustomer,
                                        customerIdToUse,
                                        selectedSalesRep,
                                        selectedSalesRepId,
                                        discount,
                                        billTypeToUse,
                                        billNoToUse,
                                        "card",
                                        amount,
                                        trn = cardTRN,
                                        cardLastFourDigits = cardLastFourDigits,
                                        orderNo = orderNoFromParams,
                                        memo = memoText
                                    )
                                }

                                // Check if result is an error
                                if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                    Log.e("CreditBillScreen", "Bill save with card payment failed: $result")
                                    errorMessage = extractErrorMessage(result)
                                    showErrorDialog = true
                                    showCardPaymentDialog = false
                                    return@launch
                                }

                                // Print receipt after saving
                                if (result != null) {
                                    val savedBillNo = result
                                    try {
                                        val printerService = PrinterService.getInstance()
                                        if (templateForPrint != null) {
                                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                            val receiptItems = mergeCartItemsForReceipt(itemsForPrint)
                                            val receiptData = buildReceiptFromTemplate(
                                                templateForPrint,
                                                receiptItems,
                                                customerForPrint.ifEmpty { "CASH" },
                                                salesRepForPrint.ifEmpty { "N/A" },
                                                itemsForPrint.sumOf { it.amount },
                                                discountForPrint,
                                                netTotalForPrint,
                                                amount,
                                                savedBillNo
                                            ).copy(
                                                showLineDiscount = config?.showLineDiscountInSmallBills == "Y",
                                                totalItemDiscount = totalLineItemDiscount(itemsForPrint),
                                                returnItems = cartReturnReceiptItems(itemsForPrint),
                                                totalReturn = cartTotalReturn(itemsForPrint)
                                            )
                                            if (printerService.isConnected()) {
                                                Log.d("CreditBillScreen", "Printing from Card Payment Dialog...")
                                                val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                if (printedFile != null) {
                                                    lastReceiptPdf = printedFile
                                                    if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                        previewPdfFile = printedFile
                                                    }
                                                }
                                            } else if (config?.showReceiptPrintPreviewAndroidApp == "Y" || config?.enableShareAndroidApp == "Y") {
                                                // No printer connected: still generate the PDF so
                                                // the print-preview / share buttons work.
                                                val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                lastReceiptPdf = pdfFile
                                                if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                    previewPdfFile = pdfFile
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CreditBillScreen", "Print error: ${e.message}")
                                    }
                                }

                                showCardPaymentDialog = false
                                cartItems = emptyList(); clearCreditBillCache(context)
                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                successMessage = "Bill #${result ?: ""} saved successfully!"
                                showSuccessDialog = true
                                selectedCustomer = ""
                                selectedCustomerId = 0L
                                selectedSalesRep = ""
                                selectedSalesRepId = 0L
                                discountValue = "0"
                                memoText = ""
                                paymentAmount = ""
                                cardLastFourDigits = ""
                                cardTRN = ""
                                isCashBillMode = false
                                cashBillNo = ""
                                loadNextBillNo()
                            } else {
                                Toast.makeText(context, "Please enter a valid payment amount", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Save Bill & Pay")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showCardPaymentDialog = false
                    if (isCashBillMode) {
                        // Go back to payment method selection in cash bill mode
                        showPaymentMethodDialog = true
                    }
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error Dialog
    if (showSuccessDialog) {
        LaunchedEffect(successMessage) {
            kotlinx.coroutines.delay(5000)
            showSuccessDialog = false
        }
        val canShare = config?.enableShareAndroidApp == "Y" && lastReceiptPdf != null
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success") },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) { Text("OK") }
            },
            dismissButton = if (canShare) {
                {
                    TextButton(onClick = {
                        lastReceiptPdf?.let { ReceiptShareUtils.sharePdf(context, it) }
                    }) { Text("Share") }
                }
            } else null
        )
    }

    previewPdfFile?.let { pdf ->
        ReceiptPreviewDialog(
            pdfFile = pdf,
            canShare = config?.enableShareAndroidApp == "Y",
            onShare = { ReceiptShareUtils.sharePdf(context, pdf) },
            onDismiss = { previewPdfFile = null }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    "Error",
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            },
            text = {
                Text(
                    errorMessage,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text("OK")
                }
            }
        )
    }

}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun InlineBarcodeScanner(
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
        ) {
            val previewView = remember { PreviewView(context) }
            var scanned by remember { mutableStateOf(false) }

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            )

            DisposableEffect(previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !scanned) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val barcode = barcodes.firstOrNull()?.rawValue
                                    if (barcode != null && !scanned) {
                                        scanned = true
                                        onBarcodeScanned(barcode)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))

                onDispose {
                    try {
                        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                        cameraProvider.unbindAll()
                    } catch (_: Exception) {}
                }
            }

            Text(
                "Point at barcode",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    } else {
        Text(
            "Camera permission required",
            fontSize = 12.sp,
            color = Color.Red,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun CompactField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = true,
    isBold: Boolean = false,
    isHighlighted: Boolean = false,
    onValueChange: (String) -> Unit = {}
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        if (readOnly) {
            // Compact label display for read-only fields
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isHighlighted) Color(0xFF6366F1) else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = if (isBold || isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHighlighted) Color.White else Color.Black
                )
            }
        } else {
            // Editable field
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = false,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        }
    }
}

private val creditBillSaveInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

/** Format a return qty without a trailing ".0" for whole numbers (e.g. 2.0 -> "2"). */
private fun formatReturnQty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * After a credit bill is saved online, apply any item-wise returns via the
 * backend's two-step salesReturn flow (load saved bill -> POST /bill/salesReturn).
 * No-op when no cart item has returnQty > 0.
 *
 * returnType mirrors Image Bills: DAMAGE when `sendReturnItemFromImageBillsToDamagesAndroidApp`
 * is "Y", otherwise STOCK. This also matters for PURE returns (qty 0, returnQty > 0): the
 * backend rejects qty < returnQty unless returnType == DAMAGE, so pure returns require the
 * damages config to be enabled.
 */
suspend fun applyItemReturns(
    context: Context,
    token: String,
    billNo: Long,
    billType: String,
    items: List<BillItem>
) {
    val returnQtyMap = items.filter { it.returnQty > 0.0 }
        .groupBy { it.id }
        .mapValues { entry -> entry.value.sumOf { it.returnQty } }
    if (returnQtyMap.isEmpty()) return
    try {
        val sendToDamages = fetchConfig(context, token)?.sendReturnItemFromImageBillsToDamagesAndroidApp == "Y"
        val ok = com.pos.cosmos.utils.BillReturnUtils.applyReturnsIfAny(
            token = token,
            billNo = billNo.toInt(),
            billType = billType,
            returnQtyMap = returnQtyMap,
            returnType = if (sendToDamages) "DAMAGE" else "STOCK"
        )
        Log.d("CreditBillScreen", "Item returns applied for bill #$billNo (damages=$sendToDamages): success=$ok")
    } catch (e: Exception) {
        Log.e("CreditBillScreen", "Failed to apply item returns for bill #$billNo: ${e.message}", e)
    }
}

private suspend fun saveBill(
    context: Context,
    token: String,
    items: List<BillItem>,
    customer: String,
    customerId: Long,
    salesRep: String,
    salesRepId: Long,
    discount: Double,
    billType: String,
    billNumber: String,
    orderNo: Int = 0,
    memo: String = ""
): String? {
    if (!creditBillSaveInFlight.compareAndSet(false, true)) {
        Log.w("CreditBillScreen", "saveBill ignored â€” another save is already in progress")
        return null
    }
    try {
    val billNo = billNumber.toLongOrNull() ?: System.currentTimeMillis() / 1000
    val grossTotal = items.sumOf { it.amount }
    val netAmount = grossTotal - cartTotalReturn(items) - discount

    // â”€â”€ Local-first path (mirrors desktop CreditBillForm.SaveBillLocal) â”€â”€
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val localBillNo = com.pos.cosmos.local.LocalBillSaver.saveBill(
                context = context,
                billNo = 0,
                billType = billType,
                customerId = customerId,
                cashCustomerName = if (customerId <= 0 && customer.isNotEmpty()) customer else null,
                salesRefId = salesRepId,
                total = netAmount,
                discount = 0.0,
                discountValue = discount,
                cash = 0.0,
                lines = items.map {
                    com.pos.cosmos.local.LocalBillLine(
                        itemId = it.id.toLong(),
                        unitPrice = it.unitPrice,
                        qty = it.quantity.toDouble(),
                        discount = it.discount,
                        discountValue = it.discountValue,
                        freeIssueQty = it.freeIssueQty
                    )
                }
            )
            Log.d("CreditBillScreen", "Credit bill $localBillNo queued locally (offline).")
            localBillNo.toString()
        } catch (e: Exception) {
            val errorMsg = "Failed to save credit bill locally: ${e.message}"
            Log.e("CreditBillScreen", errorMsg, e)
            errorMsg
        }
    }

    return try {
        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        val billItems = items.map { item ->
            Log.d("CreditBillScreen", "Item: code=${item.code}, id=${item.id}")
            jsonObjectOf(
                "itemCode" to item.code,
                "itemName" to item.name,
                "invoiceId" to item.invoiceId,
                "supplierId" to item.supplierId,
                "itemId" to item.id,
                "qty" to item.quantity.toString(),
                "unitPrice" to item.unitPrice,
                "discount" to item.discount,
                "discountValue" to item.discountValue,
                "discountPercentage" to item.discountPercentage,
                "totalPrice" to item.amount,
                "totalQty" to item.totalQty,
                "freeIssueQty" to item.freeIssueQty,
                "isPromoFreeItem" to item.isPromoFreeItem,
                "promotionId" to item.promotionId
            )
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentDate = dateFormatter.format(Date())

        val billData = jsonObjectOf(
            "billItems" to billItems,
            "billNo" to billNo,
            "cash" to 0,
            "billType" to billType,
            "salesRefId" to salesRepId,
            "discount" to discount,
            "discountValue" to discount,
            "discountPercentage" to if (grossTotal > 0) (discount / grossTotal * 100) else 0,
            "customerId" to customerId,
            "paidAmount" to 0,
            "total" to netAmount,
            "billingDate" to currentDate,
            "id" to 0,
            // Pass customerName when customerId is 0 (for new customer creation)
            "customerName" to if (customerId <= 0 && customer.isNotEmpty()) customer else "",
            "salesRefName" to "",
            "userEvent" to jsonObjectOf(
                "event" to "Save small Credit Bill $billNo",
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "userName" to "",
                "eventTime" to "",
                "mapLocations" to null
            ),
            "paymentMethod" to "",
            "paymentDetails" to "",
            // Include orderNo to mark order as processed (from All Orders flow)
            "orderNo" to if (orderNo > 0) orderNo else null,
            "memo" to memo
        )

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBill")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = billData.toString()

        // Log the payload before sending
        Log.d("CreditBillScreen", "=== CREDIT BILL PAYLOAD ===")
        Log.d("CreditBillScreen", "CustomerId: $customerId, Customer: $customer")
        Log.d("CreditBillScreen", "SalesRepId: $salesRepId, SalesRep: $salesRep")
        Log.d("CreditBillScreen", jsonString)
        Log.d("CreditBillScreen", "==========================")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        val responseCode = connection.responseCode
        Log.d("CreditBillScreen", "Response code: $responseCode")

        if (responseCode == 200 || responseCode == 201) {
            val responseBody = connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d("CreditBillScreen", "Bill saved successfully. Response: $responseBody")
            applyItemReturns(context, token, billNo, billType, items)
            billNo.toString()  // Return bill number on success
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            val errorMsg = "Failed to save bill. Response code: $responseCode\nError: $errorResponse"
            Log.e("CreditBillScreen", errorMsg)
            errorMsg
        }
    } catch (e: Exception) {
        val errorMsg = "Exception while saving bill: ${e.message}\n${e.stackTraceToString()}"
        Log.e("CreditBillScreen", errorMsg)
        errorMsg
    }
    } finally {
        creditBillSaveInFlight.set(false)
    }
}

private suspend fun saveBillWithPayment(
    context: Context,
    token: String,
    items: List<BillItem>,
    customer: String,
    customerId: Long,
    salesRep: String,
    salesRepId: Long,
    discount: Double,
    billType: String,
    billNumber: String,
    paymentType: String,
    paymentAmount: Double,
    chequeNumber: String = "",
    chequeDate: String = "",
    bankAndBranch: String = "",
    accountNumber: String = "",
    trn: String = "",
    cashTRN: String = "",
    cardLastFourDigits: String = "",
    orderNo: Int = 0,
    memo: String = ""
): String? {
    if (!creditBillSaveInFlight.compareAndSet(false, true)) {
        Log.w("CreditBillScreen", "saveBillWithPayment ignored â€” another save is already in progress")
        return null
    }
    try {
    val billNo = billNumber.toLongOrNull() ?: System.currentTimeMillis() / 1000
    val grossTotal = items.sumOf { it.amount }
    val netAmount = grossTotal - cartTotalReturn(items) - discount

    // â”€â”€ Local-first path: queue bill + customer payment, sync later â”€â”€
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val localBillNo = com.pos.cosmos.local.LocalBillSaver.saveBill(
                context = context,
                billNo = 0,
                billType = billType,
                customerId = customerId,
                cashCustomerName = if (customerId <= 0 && customer.isNotEmpty()) customer else null,
                salesRefId = salesRepId,
                total = netAmount,
                discount = 0.0,
                discountValue = discount,
                cash = if (paymentType == "cash") paymentAmount else 0.0,
                cardPayment = if (paymentType == "card") paymentAmount else 0.0,
                lines = items.map {
                    com.pos.cosmos.local.LocalBillLine(
                        itemId = it.id.toLong(),
                        unitPrice = it.unitPrice,
                        qty = it.quantity.toDouble(),
                        discount = it.discount,
                        discountValue = it.discountValue,
                        freeIssueQty = it.freeIssueQty
                    )
                },
                payment = com.pos.cosmos.local.LocalPayment(
                    amount = paymentAmount,
                    method = paymentType,
                    note = if (paymentType == "cheque") "cheque=$chequeNumber bank=$bankAndBranch acct=$accountNumber date=$chequeDate" else null
                )
            )
            Log.d("CreditBillScreen", "Credit bill+payment $localBillNo queued locally (offline).")
            localBillNo.toString()
        } catch (e: Exception) {
            val errorMsg = "Failed to save credit bill+payment locally: ${e.message}"
            Log.e("CreditBillScreen", errorMsg, e)
            errorMsg
        }
    }

    return try {
        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        val billItems = items.map { item ->
            jsonObjectOf(
                "itemCode" to item.code,
                "itemName" to item.name,
                "invoiceId" to item.invoiceId,
                "supplierId" to item.supplierId,
                "itemId" to item.id,
                "qty" to item.quantity.toString(),
                "unitPrice" to item.unitPrice,
                "discount" to item.discount,
                "discountValue" to item.discountValue,
                "discountPercentage" to item.discountPercentage,
                "totalPrice" to item.amount,
                "totalQty" to item.totalQty,
                "freeIssueQty" to item.freeIssueQty,
                "isPromoFreeItem" to item.isPromoFreeItem,
                "promotionId" to item.promotionId
            )
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentDate = dateFormatter.format(Date())

        val billData = jsonObjectOf(
            "billItems" to billItems,
            "billNo" to billNo,
            "cash" to 0,
            "billType" to billType,
            "salesRefId" to salesRepId,
            "discount" to discount,
            "discountValue" to discount,
            "discountPercentage" to if (grossTotal > 0) (discount / grossTotal * 100) else 0,
            "customerId" to customerId,
            "paidAmount" to 0,
            "total" to netAmount,
            "billingDate" to currentDate,
            "id" to 0,
            // Pass customerName when customerId is 0 (for new customer creation)
            "customerName" to if (customerId <= 0 && customer.isNotEmpty()) customer else "",
            "salesRefName" to "",
            "userEvent" to jsonObjectOf(
                "event" to "Save small Credit Bill $billNo",
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "userName" to "",
                "eventTime" to "",
                "mapLocations" to null
            ),
            "paymentMethod" to "",
            "paymentDetails" to "",
            // Include orderNo to mark order as processed (from All Orders flow)
            "orderNo" to if (orderNo > 0) orderNo else null,
            "memo" to memo
        )

        val paymentData = jsonObjectOf(
            "id" to 0,
            "customerId" to customerId,
            "billId" to 0,
            "paidAmount" to paymentAmount,
            "type" to paymentType,
            "chequeNumber" to if (paymentType == "card") cardLastFourDigits else chequeNumber,
            "chequeDate" to if (chequeDate.isNotEmpty()) chequeDate else currentDate,
            "bankAndBranch" to bankAndBranch,
            "accountNumber" to accountNumber,
            "paymentDate" to currentDate,
            "active" to "Y",
            "salesRepId" to 0,
            "trn" to trn,
            "cashTRN" to cashTRN,
            "paymentDateStr" to "",
            // Pass customerName when customerId is 0 (for new customer creation)
            "customerName" to if (customerId <= 0 && customer.isNotEmpty()) customer else "",
            "chequeStatus" to if (paymentType == "cheque") "pending" else "",
            "chequeDateStr" to ""
        )

        val requestData = jsonObjectOf(
            "bill" to billData,
            "payment" to paymentData
        )

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBillWithPayment")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = requestData.toString()
        Log.d("CreditBillScreen", "=== SAVE BILL WITH PAYMENT ===")
        Log.d("CreditBillScreen", jsonString)
        Log.d("CreditBillScreen", "==============================")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        val responseCode = connection.responseCode
        Log.d("CreditBillScreen", "Response code: $responseCode")

        if (responseCode == 200 || responseCode == 201) {
            val responseBody = connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d("CreditBillScreen", "Bill with payment saved successfully. Response: $responseBody")
            applyItemReturns(context, token, billNo, billType, items)
            billNo.toString()
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            val errorMsg = "Failed to save bill with payment. Response code: $responseCode\nError: $errorResponse"
            Log.e("CreditBillScreen", errorMsg)
            errorMsg
        }
    } catch (e: Exception) {
        val errorMsg = "Exception while saving bill with payment: ${e.message}\n${e.stackTraceToString()}"
        Log.e("CreditBillScreen", errorMsg)
        errorMsg
    }
    } finally {
        creditBillSaveInFlight.set(false)
    }
}

private suspend fun fetchCreditBillItemsWithInvoices(context: Context, token: String): List<CreditItemOption> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.NewOrderItemFilter.sellableItems(context).map {
                CreditItemOption(
                    id = it.id.toInt(),
                    code = it.itemCode.orEmpty(),
                    name = it.name.orEmpty(),
                    sellingPrice = it.sellingPrice,
                    barcode = it.barcode.orEmpty(),
                    invoiceId = 0,
                    supplierId = 0,
                    totalQty = it.stockQty.toInt(),
                    supplierInvoiceDetails = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("CreditBillScreen", "local fetchCreditBillItemsWithInvoices failed", e)
            emptyList()
        }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllBillItems?billType=CREDIT")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("CreditBillScreen", "getAllBillItems response: $content")

            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val responseData = gson.fromJson(content, Map::class.java) as? Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val billItems = responseData?.get("billItems") as? List<Map<String, Any>> ?: emptyList()

            billItems.mapNotNull { itemMap ->
                try {
                    val itemId = (itemMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val itemCode = itemMap["code"] as? String ?: ""
                    val itemName = itemMap["name"] as? String ?: ""
                    val sellingPrice = (itemMap["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                    val itemBarcode = itemMap["barcode"] as? String ?: ""

                    // Extract ALL invoices with qty > 0 from all suppliers
                    val allInvoiceDetails = mutableListOf<SupplierInvoiceDetail>()
                    var firstInvoiceId = 0
                    var firstSupplierId = 0

                    @Suppress("UNCHECKED_CAST")
                    val billSupplierList = itemMap["billSupplierList"] as? List<Map<String, Any>>

                    if (!billSupplierList.isNullOrEmpty()) {
                        for (supplierMap in billSupplierList) {
                            val supId = (supplierMap["supplierId"] as? Number)?.toInt() ?: 0
                            @Suppress("UNCHECKED_CAST")
                            val billInvoiceList = supplierMap["billInvoiceList"] as? List<Map<String, Any>>
                            if (!billInvoiceList.isNullOrEmpty()) {
                                for (invoiceMap in billInvoiceList) {
                                    val invoiceQty = (invoiceMap["qty"] as? Number)?.toInt() ?: 0
                                    if (invoiceQty > 0) {
                                        val invId = (invoiceMap["invoiceId"] as? Number)?.toInt() ?: 0
                                        val reservedQty = (invoiceMap["reservedQty"] as? Number)?.toInt() ?: 0
                                        val invSellingPrice = (invoiceMap["sellingPrice"] as? Number)?.toDouble() ?: sellingPrice
                                        val invDefaultDiscount = (invoiceMap["defaultDiscountForInvoiceSellingPrice"] as? Number)?.toDouble() ?: 0.0
                                        allInvoiceDetails.add(
                                            SupplierInvoiceDetail(
                                                invoiceId = invId,
                                                supplierId = supId,
                                                qty = invoiceQty,
                                                sellingPrice = invSellingPrice,
                                                reservedQty = reservedQty,
                                                defaultDiscountForPrice = invDefaultDiscount
                                            )
                                        )
                                        if (firstInvoiceId == 0) {
                                            firstInvoiceId = invId
                                            firstSupplierId = supId
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val totalQty = allInvoiceDetails.sumOf { it.qty - it.reservedQty }

                    Log.d("CreditBillScreen", "Parsed item: $itemCode ($itemId) - ${allInvoiceDetails.size} invoices, totalAvailableQty=$totalQty")

                    CreditItemOption(
                        id = itemId,
                        code = itemCode,
                        name = itemName,
                        sellingPrice = sellingPrice,
                        invoiceId = firstInvoiceId,
                        supplierId = firstSupplierId,
                        totalQty = totalQty,
                        barcode = itemBarcode,
                        supplierInvoiceDetails = allInvoiceDetails
                    )
                } catch (e: Exception) {
                    Log.e("CreditBillScreen", "Error parsing item: ${e.message}")
                    null
                }
            }
        } else {
            Log.e("CreditBillScreen", "getAllBillItems request failed with response code: ${connection.responseCode}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("CreditBillScreen", "Exception in fetchCreditBillItems: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun fetchConfig(context: Context, token: String): com.pos.cosmos.models.Config? {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val rows = com.pos.cosmos.local.CosmosDatabase.get(context).billingConfigDao().all()
            val json = rows.firstOrNull { it.key == "config" }?.value ?: return null
            Gson().fromJson(json, com.pos.cosmos.models.Config::class.java)
        } catch (e: Exception) { null }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/admin/getConfigsByToken")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            gson.fromJson(content, com.pos.cosmos.models.Config::class.java)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun buildBillReceipt(
    items: List<BillItem>,
    customer: String,
    salesRep: String,
    discount: Double,
    netTotal: Double,
    cashPaid: Double
): ByteArray {
    val sb = StringBuilder()
    val dateTime = SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(System.currentTimeMillis())
    val change = if (cashPaid > 0) cashPaid - netTotal else 0.0

    // Initialize printer
    sb.append("\u001B@")

    // Center align
    sb.append("\u001B\u0061\u0001")

    // Large text
    sb.append("\u001B\u0021\u0030")
    sb.append("COSMOS POS\n")

    // Normal size
    sb.append("\u001B\u0021\u0000")

    // Left align
    sb.append("\u001B\u0061\u0000")

    sb.append("==================================================\n")
    sb.append("Invoice\n")
    sb.append("==================================================\n")

    // Bill info
    sb.append(String.format("%-20s: %-15s\n", "Date/Time", dateTime))
    sb.append(String.format("%-20s: %-15s\n", "Customer", customer.ifEmpty { "Walk-in" }))
    sb.append(String.format("%-20s: %-15s\n", "Sales Rep", salesRep.ifEmpty { "N/A" }))

    sb.append("==================================================\n")

    // Table header
    sb.append(String.format("%-20s %6s %8s %10s\n", "Item", "Qty", "Price", "Amount"))
    sb.append("==================================================\n")

    // Items
    var grossTotal = 0.0
    items.forEach { item ->
        sb.append(String.format("%-20s %6d %8.2f %10.2f\n",
            item.name.take(20),
            item.quantity,
            item.sellingPrice,
            item.amount
        ))
        grossTotal += item.amount
    }

    sb.append("\n")

    // Totals
    sb.append("==================================================\n")
    sb.append(String.format("%-30s %10.2f\n", "Gross Total:", grossTotal))
    if (discount > 0) {
        sb.append(String.format("%-30s %10.2f\n", "Discount:", discount))
    }
    sb.append(String.format("%-30s %10.2f\n", "Net Total:", netTotal))
    if (cashPaid > 0) {
        sb.append(String.format("%-30s %10.2f\n", "Cash Paid:", cashPaid))
    }
    if (change > 0) {
        sb.append(String.format("%-30s %10.2f\n", "Change:", change))
    }

    sb.append("==================================================\n")

    // Footer
    sb.append("\u001B\u0061\u0001")  // Center
    sb.append("\u001B\u0021\u0010")  // Double height

    sb.append("Thank you!\n")
    sb.append("Please visit again!\n")

    sb.append("\u001B\u0021\u0000")  // Normal
    sb.append("\u001B\u0061\u0000")  // Left

    sb.append("\n\n\n\n")

    // Cut paper
    sb.append("\u001D\u0056\u0000")

    return sb.toString().toByteArray(charset("GBK"))
}

private fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject {
    val obj = JsonObject()
    pairs.forEach { (key, value) ->
        when (value) {
            is String -> obj.addProperty(key, value)
            is Number -> obj.addProperty(key, value)
            is Boolean -> obj.addProperty(key, value)
            is JsonObject -> obj.add(key, value)
            is List<*> -> {
                val array = com.google.gson.JsonArray()
                value.forEach { item ->
                    if (item is JsonObject) array.add(item)
                    else if (item is String) array.add(item)
                    else if (item is Number) array.add(item)
                }
                obj.add(key, array)
            }
            null -> obj.addProperty(key, null as String?)
        }
    }
    return obj
}

private fun buildReceiptFromTemplate(
    template: JsonObject,
    items: List<ReceiptItem>,
    customer: String,
    salesRep: String,
    grossTotal: Double,
    discount: Double,
    netTotal: Double,
    cashPaid: Double,
    billNo: String
): ReceiptData {
    val companyName = template.get("companyName")?.asString ?: "COSMOS POS"
    val companyNameFont = template.get("companyFont")?.asInt ?: 14
    val companyGap = template.get("companyGap")?.asInt ?: 0
    val address = template.get("address")?.asString ?: ""
    val addressFont = template.get("addressFont")?.asInt ?: 12
    val addressGap = template.get("addressGap")?.asInt ?: 0
    val contact = template.get("contact")?.asString ?: ""
    val contactFont = template.get("contactFont")?.asInt ?: 12
    val contactGap = template.get("contactGap")?.asInt ?: 0
    val email = template.get("email")?.asString ?: ""
    val emailFont = template.get("emailFont")?.asInt ?: 12
    val emailGap = template.get("emailGap")?.asInt ?: 0
    val lastLine = template.get("lastLine")?.asString ?: ""
    val lastLineFont = template.get("lastLineFont")?.asInt ?: 10
    val lastLineGap = template.get("lastLineGap")?.asInt ?: 0
    val upperGap = template.get("upperGap")?.asInt ?: 0
    val leftGap = template.get("leftGap")?.asInt ?: 0
    val logoDataUrl = template.get("logoDataURL")?.asString ?: ""
    val logoLeft = template.get("logoLeft")?.asInt ?: 0
    val logoUpper = template.get("logoUpper")?.asInt ?: 0
    val logoWidth = template.get("logoWidth")?.asInt ?: 0
    val logoHeight = template.get("logoHeight")?.asInt ?: 0
    // Bill detail fields
    val billNoLabel = template.get("billNoLabel")?.asString ?: "Bill No :"
    val dateLabel = template.get("dateLabel")?.asString ?: "Date:"
    val itemHeaderLabel = template.get("itemHeader")?.asString ?: "Item"
    val priceHeaderLabel = template.get("priceHeader")?.asString ?: "Price"
    val qtyHeaderLabel = template.get("qtyHeader")?.asString ?: "Qty"
    val amountHeaderLabel = template.get("totalHeader")?.asString ?: "Amount"
    val grossTotalLabel = template.get("grossTotalLabel")?.asString ?: "Gross Total:"
    val discountLabel = template.get("billDiscountLabel")?.asString ?: "Discount:"
    val netTotalLabel = template.get("netTotalLabel")?.asString ?: "Net Total:"
    val cashLabel = template.get("cashPaidLabel")?.asString ?: "Cash:"
    val balanceLabel = template.get("balanceLabel")?.asString ?: "Balance:"
    val outstandingLabel = template.get("outstandingLabel")?.asString ?: "Outstanding:"
    val billNoFontSize = template.get("billNoFontSize")?.asFloat ?: 11f
    val dateFontSize = template.get("dateFontSize")?.asFloat ?: 11f
    val headerFontSize = template.get("itemHeaderFontSize")?.asFloat ?: 11f
    val itemFontSizeVal = template.get("itemFontSize")?.asFloat ?: 10f
    val grossTotalFontSize = template.get("grossTotalFontSize")?.asFloat ?: 11f
    val discountFontSize = template.get("billDiscountFontSize")?.asFloat ?: 11f
    val netTotalFontSize = template.get("netTotalFontSize")?.asFloat ?: 13f
    val cashFontSize = template.get("cashPaidFontSize")?.asFloat ?: 11f
    val balanceFontSize = template.get("balanceFontSize")?.asFloat ?: 11f
    val billNoGapVal = template.get("billNoGap2")?.asFloat ?: 5f
    val dateGapVal = template.get("dateGap2")?.asFloat ?: 5f
    val separatorGapVal = template.get("separatorGap")?.asFloat ?: 5f
    val headerGapVal = template.get("headerGap2")?.asFloat ?: 5f
    val itemNameGapVal = template.get("itemNameGap")?.asFloat ?: 5f
    val itemGapVal = template.get("itemGap")?.asFloat ?: 5f
    val grossTotalGapVal = template.get("grossTotalGap")?.asFloat ?: 5f
    val discountGapVal = template.get("discountGap")?.asFloat ?: 6f
    val netTotalGapVal = template.get("netTotalGap")?.asFloat ?: 5f
    val cashGapVal = template.get("cashGap")?.asFloat ?: 5f
    val balanceGapVal = template.get("balanceGap")?.asFloat ?: 5f
    val colPriceMmVal = template.get("colPriceMm")?.asFloat ?: 30f
    val colQtyMmVal = template.get("colQtyMm")?.asFloat ?: 50f
    val colAmountMmVal = template.get("colTotalMm")?.asFloat ?: 70f
    val colHeaderPriceMmVal = template.get("colHeaderPriceMm")?.asFloat ?: colPriceMmVal
    val colHeaderQtyMmVal = template.get("colHeaderQtyMm")?.asFloat ?: colQtyMmVal
    val colHeaderAmountMmVal = template.get("colHeaderAmountMm")?.asFloat ?: colAmountMmVal

    return ReceiptData(
        items = items,
        customer = customer,
        salesRep = salesRep,
        grossTotal = grossTotal,
        discount = discount,
        netTotal = netTotal,
        cashPaid = cashPaid,
        billNo = billNo,
        companyName = companyName,
        address = address,
        contact = contact,
        email = email,
        companyNameFont = companyNameFont,
        addressFont = addressFont,
        contactFont = contactFont,
        emailFont = emailFont,
        lastLineFont = lastLineFont,
        companyGap = companyGap,
        addressGap = addressGap,
        contactGap = contactGap,
        emailGap = emailGap,
        lastLineGap = lastLineGap,
        upperGap = upperGap,
        leftGap = leftGap,
        logoDataUrl = logoDataUrl,
        logoLeft = logoLeft,
        logoUpper = logoUpper,
        logoWidth = logoWidth,
        logoHeight = logoHeight,
        lastLine = lastLine,
        billNoLabel = billNoLabel,
        dateLabel = dateLabel,
        itemHeader = itemHeaderLabel,
        priceHeader = priceHeaderLabel,
        qtyHeader = qtyHeaderLabel,
        amountHeader = amountHeaderLabel,
        grossTotalLabel = grossTotalLabel,
        discountLabel = discountLabel,
        netTotalLabel = netTotalLabel,
        cashLabel = cashLabel,
        balanceLabel = balanceLabel,
        outstandingLabel = outstandingLabel,
        billNoFontSize = billNoFontSize,
        dateFontSize = dateFontSize,
        headerFontSize = headerFontSize,
        itemFontSize = itemFontSizeVal,
        grossTotalFontSize = grossTotalFontSize,
        discountFontSize = discountFontSize,
        netTotalFontSize = netTotalFontSize,
        cashFontSize = cashFontSize,
        balanceFontSize = balanceFontSize,
        billNoGap = billNoGapVal,
        dateGap = dateGapVal,
        separatorGap = separatorGapVal,
        headerGap = headerGapVal,
        itemNameGap = itemNameGapVal,
        itemGap = itemGapVal,
        grossTotalGap = grossTotalGapVal,
        discountGap = discountGapVal,
        netTotalGap = netTotalGapVal,
        cashGapMm = cashGapVal,
        balanceGap = balanceGapVal,
        colPriceMm = colPriceMmVal,
        colQtyMm = colQtyMmVal,
        colAmountMm = colAmountMmVal,
        colHeaderPriceMm = colHeaderPriceMmVal,
        colHeaderQtyMm = colHeaderQtyMmVal,
        colHeaderAmountMm = colHeaderAmountMmVal
    )
}

/**
 * Loads the credit-bill receipt template, cache-first with a network fallback. Used by the
 * background preload effect so that print-preview / share never silently break due to a
 * missing template.
 */
private suspend fun loadCreditBillTemplate(
    context: android.content.Context,
    token: String,
    billType: String
): JsonObject? {
    var template = BillTemplateCache.getTemplate(context, billType)
    if (template == null) {
        template = fetchSmallBillTemplateCredit(token, billType)
        if (template != null) {
            BillTemplateCache.saveTemplate(context, template, billType)
        }
    }
    return template
}

private suspend fun fetchSmallBillTemplateCredit(
    token: String,
    targetBillType: String = "android"
): JsonObject? {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/bill-templates")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val allTemplates = gson.fromJson(content, com.google.gson.JsonArray::class.java)

            var smallTemplate: JsonObject? = null
            if (allTemplates != null && allTemplates.size() > 0) {
                for (i in 0 until allTemplates.size()) {
                    val template = allTemplates.get(i).asJsonObject
                    val billType = template.get("billType")?.asString
                    if (billType == targetBillType) {
                        Log.d("CreditBillScreen", "Fetched '$targetBillType' bill template successfully")
                        smallTemplate = template
                        break
                    }
                }
                if (smallTemplate == null && targetBillType != "android") {
                    for (i in 0 until allTemplates.size()) {
                        val template = allTemplates.get(i).asJsonObject
                        val billType = template.get("billType")?.asString
                        if (billType == "android") {
                            Log.d("CreditBillScreen", "Falling back to 'android' template")
                            smallTemplate = template
                            break
                        }
                    }
                }
                if (smallTemplate == null) {
                    for (i in 0 until allTemplates.size()) {
                        val template = allTemplates.get(i).asJsonObject
                        val billType = template.get("billType")?.asString
                        if (billType == "small") {
                            Log.d("CreditBillScreen", "Falling back to 'small' template")
                            smallTemplate = template
                            break
                        }
                    }
                }
            }
            smallTemplate
        } else {
            Log.e("CreditBillScreen", "Failed to fetch bill templates: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("CreditBillScreen", "Error fetching bill template: ${e.message}")
        null
    }
}

/**
 * Extract a user-friendly error message from the API error response
 */
private fun extractErrorMessage(errorResult: String): String {
    return try {
        // Try to extract the message from the error response
        // Error format might be: "Failed to save bill. Response code: 400\nError: {\"message\":\"Invoice Qty Exceeded!...\"}"
        if (errorResult.contains("Error:")) {
            val errorPart = errorResult.substringAfter("Error:").trim()
            // Try to parse as JSON
            if (errorPart.startsWith("{")) {
                val jsonObject = JsonParser.parseString(errorPart).asJsonObject
                if (jsonObject.has("message")) {
                    return jsonObject.get("message").asString
                }
            }
            // Return the error part as-is if not JSON
            errorPart
        } else if (errorResult.contains("Exception")) {
            // For exceptions, extract the meaningful part
            errorResult.substringAfter("Exception while saving bill:").trim()
                .substringBefore("\n")
        } else {
            errorResult
        }
    } catch (e: Exception) {
        // If parsing fails, return original message
        errorResult
    }
}

