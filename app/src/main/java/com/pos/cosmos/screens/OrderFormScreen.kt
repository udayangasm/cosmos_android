package com.pos.cosmos.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.utils.LocationHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
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
import com.pos.cosmos.api.ApiConfig

// Data classes for Order Form
data class OrderItem(
    val itemId: Int,
    val itemCode: String,
    val itemName: String,
    val supplierId: Int,
    val supplierName: String = "",
    val invoiceId: Int,
    val invoiceName: String = "",
    val qty: Double,
    val unitPrice: Double,
    val discountPercentage: Double = 0.0,
    val discountValue: Double = 0.0,
    val totalQty: Double = 0.0,
    val unitCost: Double = 0.0,
    val freeIssueQty: Double = 0.0,
    val isPromoFreeItem: Boolean = false,
    val promotionId: Int = 0
) {
    val totalPrice: Double
        get() = qty * (unitPrice - discountValue)
}

data class CustomerOption(
    val id: Long,
    val name: String,
    val address: String = "",
    val telephone: String = ""
)

data class SalesRefOption(
    val id: Long,
    val name: String
)

data class OrderBillItem(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    val billSupplierList: List<BillSupplier> = emptyList()
)

data class BillSupplier(
    val supplierId: Int,
    val supplierName: String,
    val billInvoiceList: List<BillInvoice> = emptyList()
)

data class BillInvoice(
    val invoiceId: Int,
    val invoiceName: String,
    val qty: Double,
    val sellingPrice: Double,
    val unitCost: Double = 0.0,
    val reservedQty: Double = 0.0
)

// Payment option for order form
data class PaymentOption(
    val label: String,
    val value: String
)

// ---- Order Form draft cache (mirrors Credit Bill caching) ----
// Persists the in-progress order until it is saved, so selected item details and
// header fields survive recomposition / navigation away from the screen.
private const val ORDER_FORM_PREFS = "order_form_cache"
private const val OF_KEY_ITEMS = "order_items"
private const val OF_KEY_CUSTOMER = "customer"
private const val OF_KEY_CUSTOMER_QUERY = "customer_query"
private const val OF_KEY_SALES_REP = "sales_rep"
private const val OF_KEY_SALES_REP_QUERY = "sales_rep_query"
private const val OF_KEY_DISCOUNT_PCT = "discount_pct"
private const val OF_KEY_DISCOUNT_VAL = "discount_val"
private const val OF_KEY_MEMO = "memo"
private const val OF_KEY_DELIVERY_DATE = "delivery_date"

data class OrderFormDraft(
    val items: List<OrderItem>,
    val customer: CustomerOption?,
    val customerQuery: String,
    val salesRep: SalesRefOption?,
    val salesRepQuery: String,
    val discountPercentage: String,
    val discountValue: String,
    val memo: String,
    val deliveryDate: Long?
)

private fun saveOrderFormDraft(
    context: Context,
    items: List<OrderItem>,
    customer: CustomerOption?,
    customerQuery: String,
    salesRep: SalesRefOption?,
    salesRepQuery: String,
    discountPercentage: String,
    discountValue: String,
    memo: String,
    deliveryDate: Long?
) {
    val gson = Gson()
    context.getSharedPreferences(ORDER_FORM_PREFS, Context.MODE_PRIVATE).edit()
        .putString(OF_KEY_ITEMS, gson.toJson(items))
        .putString(OF_KEY_CUSTOMER, customer?.let { gson.toJson(it) })
        .putString(OF_KEY_CUSTOMER_QUERY, customerQuery)
        .putString(OF_KEY_SALES_REP, salesRep?.let { gson.toJson(it) })
        .putString(OF_KEY_SALES_REP_QUERY, salesRepQuery)
        .putString(OF_KEY_DISCOUNT_PCT, discountPercentage)
        .putString(OF_KEY_DISCOUNT_VAL, discountValue)
        .putString(OF_KEY_MEMO, memo)
        .putLong(OF_KEY_DELIVERY_DATE, deliveryDate ?: -1L)
        .apply()
}

private fun loadOrderFormDraft(context: Context): OrderFormDraft {
    val prefs = context.getSharedPreferences(ORDER_FORM_PREFS, Context.MODE_PRIVATE)
    val gson = Gson()
    val items = try {
        prefs.getString(OF_KEY_ITEMS, null)?.let {
            val type = object : com.google.gson.reflect.TypeToken<List<OrderItem>>() {}.type
            gson.fromJson<List<OrderItem>>(it, type)
        } ?: emptyList()
    } catch (e: Exception) { emptyList() }
    val customer = try {
        prefs.getString(OF_KEY_CUSTOMER, null)?.let { gson.fromJson(it, CustomerOption::class.java) }
    } catch (e: Exception) { null }
    val salesRep = try {
        prefs.getString(OF_KEY_SALES_REP, null)?.let { gson.fromJson(it, SalesRefOption::class.java) }
    } catch (e: Exception) { null }
    return OrderFormDraft(
        items = items,
        customer = customer,
        customerQuery = prefs.getString(OF_KEY_CUSTOMER_QUERY, "") ?: "",
        salesRep = salesRep,
        salesRepQuery = prefs.getString(OF_KEY_SALES_REP_QUERY, "") ?: "",
        discountPercentage = prefs.getString(OF_KEY_DISCOUNT_PCT, "0") ?: "0",
        discountValue = prefs.getString(OF_KEY_DISCOUNT_VAL, "0") ?: "0",
        memo = prefs.getString(OF_KEY_MEMO, "") ?: "",
        deliveryDate = prefs.getLong(OF_KEY_DELIVERY_DATE, -1L).takeIf { it >= 0L }
    )
}

private fun clearOrderFormDraft(context: Context) {
    context.getSharedPreferences(ORDER_FORM_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreenFull(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current

    // Cached in-progress order draft (mirrors Credit Bill caching)
    val cachedDraft = remember { loadOrderFormDraft(context) }

    // State variables
    var orderItems by remember { mutableStateOf<List<OrderItem>>(cachedDraft.items) }
    var availableItems by remember { mutableStateOf<List<OrderBillItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<OrderBillItem>>(emptyList()) }
    var customers by remember { mutableStateOf<List<CustomerOption>>(emptyList()) }
    var salesReps by remember { mutableStateOf<List<SalesRefOption>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var showItemSearch by remember { mutableStateOf(false) }
    var showItemDetailDialog by remember { mutableStateOf(false) }
    var selectedBillItem by remember { mutableStateOf<OrderBillItem?>(null) }

    var orderNo by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerOption?>(cachedDraft.customer) }
    var selectedSalesRep by remember { mutableStateOf<SalesRefOption?>(cachedDraft.salesRep) }
    var discountPercentage by remember { mutableStateOf(cachedDraft.discountPercentage) }
    var discountValue by remember { mutableStateOf(cachedDraft.discountValue) }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editingQty by remember { mutableStateOf("") }
    var editingPrice by remember { mutableStateOf("") }
    var editingDiscount by remember { mutableStateOf("") }
    var editingDiscountValue by remember { mutableStateOf("") }
    var editingFreeIssue by remember { mutableStateOf("") }

    // Customer/SalesRep search
    var customerSearchQuery by remember { mutableStateOf(cachedDraft.customerQuery) }
    var salesRepSearchQuery by remember { mutableStateOf(cachedDraft.salesRepQuery) }
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var showSalesRepDropdown by remember { mutableStateOf(false) }

    // Item dialog state
    var dialogQty by remember { mutableStateOf("1") }
    var dialogPrice by remember { mutableStateOf("") }
    var dialogDiscount by remember { mutableStateOf("0") }
    var dialogFreeIssue by remember { mutableStateOf("0") }
    var selectedSupplier by remember { mutableStateOf<BillSupplier?>(null) }
    var selectedInvoice by remember { mutableStateOf<BillInvoice?>(null) }

    // Bill template for printing
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }

    // Config for create new customer
    var createNewCustomerWhenBillingIfNotExists by remember { mutableStateOf("") }
    // Config for printing receipt
    var printReceiptWhenCreatingOrder by remember { mutableStateOf("") }
    // Config for showing free issue in order form
    var showFreeIssueInOrderForm by remember { mutableStateOf("") }
    // Config for showing customer address in order form
    var showCustomerAddressInOrderForm by remember { mutableStateOf("") }
    // Config for showing payment options in order form
    var showPaymentOptionsInOrderForm by remember { mutableStateOf("") }
    // Config for allowing orders even when stock qty is 0 or negative
    var doNotCheckStockQtyWhenMakeOrders by remember { mutableStateOf("") }
    // Config for showing memo in order form
    var showMemoInOrderFormAndCreditBill by remember { mutableStateOf("") }
    // Config for showing delivery date in order form
    var showDeliveryDateInOrderForm by remember { mutableStateOf("") }
    // Selected delivery date as epoch millis (null = not set)
    var deliveryDateMillis by remember { mutableStateOf<Long?>(cachedDraft.deliveryDate) }
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

    // Memo text state
    var memoText by remember { mutableStateOf(cachedDraft.memo) }

    // Address dropdown state
    var customerAddresses by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedAddress by remember { mutableStateOf("") }
    var showAddressDropdown by remember { mutableStateOf(false) }

    // Payment options state
    var paymentOptions by remember { mutableStateOf<List<PaymentOption>>(emptyList()) }
    var selectedPaymentOption by remember { mutableStateOf<PaymentOption?>(null) }

    // State for editing existing orders
    var orderId by remember { mutableStateOf<Long?>(null) }
    var isOrderAccepted by remember { mutableStateOf(false) }
    var isLoadingOrder by remember { mutableStateOf(false) }
    var orderNoInput by remember { mutableStateOf("") }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Calculate totals
    val grossTotal = orderItems.sumOf { it.totalPrice }
    val discountPercent = discountPercentage.toDoubleOrNull() ?: 0.0
    val discountVal = discountValue.toDoubleOrNull() ?: 0.0
    val calculatedDiscount = if (discountPercent > 0) (grossTotal * discountPercent / 100) else discountVal
    val netTotal = grossTotal - calculatedDiscount
    val noOfItems = orderItems.size
    val noOfPcs = orderItems.sumOf { it.qty }.toInt()

    // ---- Promotions (mirrors web PromotionBilling.applyOffer/removeApplied/run) ----

    /** Write an offer's free items onto the order lines. */
    fun applyOfferToLines(offer: PromotionOffer, items: List<OrderItem>): List<OrderItem> {
        if (offer.sameItem) {
            val free = offer.freeItems.firstOrNull() ?: return items
            val idx = items.indexOfFirst { !it.isPromoFreeItem && it.itemId == free.itemId }
            if (idx >= 0 && items[idx].freeIssueQty != free.qty) {
                return items.toMutableList().apply { this[idx] = this[idx].copy(freeIssueQty = free.qty) }
            }
            return items
        }
        var list = items
        for (free in offer.freeItems) {
            val idx = list.indexOfFirst {
                it.isPromoFreeItem && it.promotionId == offer.promotionId && it.itemId == free.itemId
            }
            list = if (idx >= 0) {
                if (list[idx].qty != free.qty) {
                    list.toMutableList().apply { this[idx] = this[idx].copy(qty = free.qty) }
                } else list
            } else {
                list + OrderItem(
                    itemId = free.itemId,
                    itemCode = free.itemCode ?: "",
                    itemName = free.itemName ?: "",
                    supplierId = 0,
                    invoiceId = 0,
                    qty = free.qty,
                    unitPrice = 0.0,
                    isPromoFreeItem = true,
                    promotionId = offer.promotionId
                )
            }
        }
        return list
    }

    /** Undo a previously applied promo (qty dropped below the trigger). */
    fun removeAppliedOffer(offer: PromotionOffer?, promotionId: Int, items: List<OrderItem>): List<OrderItem> {
        var list = items
        if (offer != null && offer.sameItem) {
            val free = offer.freeItems.firstOrNull()
            if (free != null) {
                val idx = list.indexOfFirst { !it.isPromoFreeItem && it.itemId == free.itemId }
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
            val lines = orderItems
                .filter { !it.isPromoFreeItem && it.itemId > 0 && it.qty > 0 }
                .map { PromoEvalLine(it.itemId, it.qty) }
            val offers = if (lines.isEmpty()) emptyList()
            else withContext(Dispatchers.IO) { evaluatePromotionsApi(tk, lines) }
                ?: return@launch // network/server issue: keep the order untouched

            val offerIds = offers.map { it.promotionId }.toSet()
            var items = orderItems

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

            if (items !== orderItems) orderItems = items
        }
    }

    // Auto-save the in-progress order draft until it is saved (mirrors Credit Bill caching).
    // Only a brand-new order is cached; editing a loaded order (orderId != null) must not
    // overwrite the new-order draft.
    LaunchedEffect(orderItems, selectedCustomer, customerSearchQuery, selectedSalesRep, salesRepSearchQuery, discountPercentage, discountValue, memoText, deliveryDateMillis, orderId) {
        if (orderId == null) {
            saveOrderFormDraft(
                context, orderItems, selectedCustomer, customerSearchQuery,
                selectedSalesRep, salesRepSearchQuery, discountPercentage, discountValue,
                memoText, deliveryDateMillis
            )
        }
    }

    // Initial data load
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                try {
                    // Fetch config
                    val config = withContext(Dispatchers.IO) { fetchOrderFormConfig(token!!) }
                    if (config != null) {
                        createNewCustomerWhenBillingIfNotExists = config.createNewCustomerWhenBillingIfNotExists ?: ""
                        printReceiptWhenCreatingOrder = config.printReceiptWhenCreatingOrder ?: ""
                        showFreeIssueInOrderForm = config.showFreeIssueInOrderForm ?: ""
                        showCustomerAddressInOrderForm = config.showCustomerAddressInOrderForm ?: ""
                        showPaymentOptionsInOrderForm = config.showPaymentOptionsInOrderForm ?: ""
                        doNotCheckStockQtyWhenMakeOrders = config.doNotCheckStockQtyWhenMakeOrders ?: ""
                        showMemoInOrderFormAndCreditBill = config.showMemoInOrderFormAndCreditBill ?: ""
                        showDeliveryDateInOrderForm = config.showDeliveryDateInOrderForm ?: ""
                        enablePromotions = config.enablePromotions ?: ""
                        editSellingPrice = config.editSellingPriceWhenBilling ?: ""
                        editSellingPriceUpward = config.editSellingPriceUpwardWhenBilling ?: ""
                        Log.d("OrderForm", "enablePromotions: $enablePromotions")
                        Log.d("OrderForm", "editSellingPrice: $editSellingPrice, upward: $editSellingPriceUpward")
                        Log.d("OrderForm", "createNewCustomerWhenBillingIfNotExists: $createNewCustomerWhenBillingIfNotExists")
                        Log.d("OrderForm", "printReceiptWhenCreatingOrder: $printReceiptWhenCreatingOrder")
                        Log.d("OrderForm", "showFreeIssueInOrderForm: $showFreeIssueInOrderForm")
                        Log.d("OrderForm", "showCustomerAddressInOrderForm: $showCustomerAddressInOrderForm")
                        Log.d("OrderForm", "showPaymentOptionsInOrderForm: $showPaymentOptionsInOrderForm")

                        // Initialize payment options if config is enabled
                        if (showPaymentOptionsInOrderForm == "Y") {
                            paymentOptions = listOf(
                                PaymentOption("Cash", "CASH"),
                                PaymentOption("Cheque", "CHEQUE"),
                                PaymentOption("Card", "CARD")
                            )
                            selectedPaymentOption = paymentOptions[0] // Default to Cash
                        }
                    }

                    // Fetch next order number
                    val nextOrderNo = withContext(Dispatchers.IO) { fetchNextOrderNo(token!!) }
                    orderNo = nextOrderNo

                    // Fetch all items
                    val items = withContext(Dispatchers.IO) { fetchOrderBillItems(token!!) }
                    availableItems = items
                    filteredItems = items

                    // Fetch customers
                    val customerList = withContext(Dispatchers.IO) { fetchCustomersList(token!!) }
                    customers = customerList

                    // Fetch sales reps
                    val salesRepList = withContext(Dispatchers.IO) { fetchSalesRepsList(token!!) }
                    salesReps = salesRepList

                    // Load bill template from cache (template is fetched and cached by PrinterScreen)
                    if (billTemplate == null) {
                        billTemplate = BillTemplateCache.getTemplate(context)
                        if (billTemplate != null) {
                            Log.d("OrderForm", "Bill template loaded from cache")
                        } else {
                            Log.w("OrderForm", "No bill template in cache - please visit Printer screen first to fetch template")
                        }
                    }

                } catch (e: Exception) {
                    Log.e("OrderForm", "Error loading data: ${e.message}")
                }
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Form", color = Color.White) },
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
            if (isLandscape) {
                // Landscape layout - side by side
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0F3460))
                ) {
                    // Left side - Items list
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) {
                        // Add Item Button
                        Button(
                            onClick = { showItemSearch = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Item")
                        }

                        // Items Table
                        OrderItemsTable(
                            items = orderItems,
                            showFreeIssue = showFreeIssueInOrderForm == "Y",
                            onEditItem = { index ->
                                editingItemIndex = index
                                editingQty = orderItems[index].qty.toString()
                                editingPrice = orderItems[index].unitPrice.toString()
                                editingDiscount = orderItems[index].discountPercentage.toString()
                                editingDiscountValue = orderItems[index].discountValue.toString()
                                editingFreeIssue = orderItems[index].freeIssueQty.toString()
                            },
                            onDeleteItem = { index ->
                                val removed = orderItems.getOrNull(index)
                                orderItems = orderItems.toMutableList().apply { removeAt(index) }
                                if (removed?.isPromoFreeItem == true) {
                                    promoState.onPromoLineDeleted(removed.promotionId)
                                }
                                schedulePromotionCheck()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Right side - Order Summary
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(8.dp)
                    ) {
                        OrderSummaryPanel(
                            orderNo = orderNo,
                            grossTotal = grossTotal,
                            discountPercentage = discountPercentage,
                            discountValue = discountValue,
                            netTotal = netTotal,
                            noOfItems = noOfItems,
                            noOfPcs = noOfPcs,
                            selectedCustomer = selectedCustomer,
                            selectedSalesRep = selectedSalesRep,
                            customers = customers,
                            salesReps = salesReps,
                            customerSearchQuery = customerSearchQuery,
                            salesRepSearchQuery = salesRepSearchQuery,
                            onOrderNoChange = { orderNo = it },
                            onDiscountPercentageChange = {
                                discountPercentage = it
                                if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                    discountValue = "0"
                                }
                            },
                            onDiscountValueChange = {
                                discountValue = it
                                if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                    discountPercentage = "0"
                                }
                            },
                            onCustomerSelected = { customer ->
                                selectedCustomer = customer
                                customerSearchQuery = customer?.name ?: ""
                                // Populate address dropdown with all addresses of customers with same name
                                if (customer != null) {
                                    val sameNameCustomers = customers.filter { it.name.equals(customer.name, ignoreCase = true) }
                                    customerAddresses = sameNameCustomers.map { it.address }.distinct()
                                    selectedAddress = customer.address
                                } else {
                                    customerAddresses = emptyList()
                                    selectedAddress = ""
                                }
                            },
                            onSalesRepSelected = { selectedSalesRep = it; salesRepSearchQuery = it?.name ?: "" },
                            onCustomerSearchChange = { customerSearchQuery = it },
                            onSalesRepSearchChange = { salesRepSearchQuery = it },
                            showCustomerAddressInOrderForm = showCustomerAddressInOrderForm == "Y",
                            customerAddresses = customerAddresses,
                            selectedAddress = selectedAddress,
                            onAddressSelected = { address ->
                                selectedAddress = address
                                // Find and select the customer with this address
                                val customerWithAddress = customers.find {
                                    it.name.equals(selectedCustomer?.name, ignoreCase = true) && it.address == address
                                }
                                if (customerWithAddress != null) {
                                    selectedCustomer = customerWithAddress
                                }
                            },
                            showPaymentOptionsInOrderForm = showPaymentOptionsInOrderForm == "Y",
                            paymentOptions = paymentOptions,
                            selectedPaymentOption = selectedPaymentOption,
                            onPaymentOptionSelected = { selectedPaymentOption = it },
                            showMemoInOrderFormAndCreditBill = showMemoInOrderFormAndCreditBill == "Y",
                            memoText = memoText,
                            onMemoTextChange = { memoText = it },
                            showDeliveryDateInOrderForm = showDeliveryDateInOrderForm == "Y",
                            deliveryDateMillis = deliveryDateMillis,
                            onDeliveryDateSelected = { deliveryDateMillis = it },
                            orderId = orderId,
                            isOrderAccepted = isOrderAccepted,
                            isLoadingOrder = isLoadingOrder,
                            onLoadOrder = {
                                val orderNoToLoad = orderNo.toIntOrNull()
                                if (orderNoToLoad != null && orderNoToLoad > 0) {
                                    isLoadingOrder = true
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                loadOrderForm(token!!, orderNoToLoad)
                                            }
                                            if (result != null) {
                                                // Populate form with order data
                                                orderId = result.id
                                                isOrderAccepted = result.isAccepted
                                                deliveryDateMillis = result.deliveryDate
                                                // Fill in missing item names/codes from availableItems
                                                orderItems = result.items.map { item ->
                                                    if (item.itemName.isEmpty() || item.itemCode.isEmpty()) {
                                                        val availableItem = availableItems.find { it.id == item.itemId }
                                                        item.copy(
                                                            itemName = if (item.itemName.isEmpty()) availableItem?.name ?: "Unknown Item" else item.itemName,
                                                            itemCode = if (item.itemCode.isEmpty()) availableItem?.code ?: "" else item.itemCode
                                                        )
                                                    } else {
                                                        item
                                                    }
                                                }
                                                discountPercentage = result.discountPercentage.toString()
                                                discountValue = result.discountValue.toString()
                                                // Set customer
                                                val customer = customers.find { it.id == result.customerId }
                                                if (customer != null) {
                                                    selectedCustomer = customer
                                                    customerSearchQuery = customer.name
                                                }
                                                // Set sales rep
                                                val salesRep = salesReps.find { it.id == result.salesRefId }
                                                if (salesRep != null) {
                                                    selectedSalesRep = salesRep
                                                    salesRepSearchQuery = salesRep.name
                                                }
                                                successMessage = "Order #$orderNoToLoad loaded successfully"
                                                showSuccessDialog = true
                                            } else {
                                                errorMessage = "Order #$orderNoToLoad not found"
                                                showErrorDialog = true
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Error loading order: ${e.message}"
                                            showErrorDialog = true
                                        }
                                        isLoadingOrder = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Create Order Button (only when no existing order loaded)
                        if (orderId == null) {
                            Button(
                                onClick = {
                                    if (orderItems.isNotEmpty()) {
                                    // Validate customer selection or allow new customer creation
                                    val customerId = selectedCustomer?.id ?: 0L
                                    val customerName = if (customerId > 0) {
                                        selectedCustomer?.name ?: ""
                                    } else {
                                        customerSearchQuery.trim()
                                    }

                                    // Check if customer is required
                                    if (customerId <= 0 && customerName.isEmpty()) {
                                        errorMessage = "Please select or enter a customer name"
                                        showErrorDialog = true
                                        return@Button
                                    }

                                    // If no customer selected but name entered, check config
                                    if (customerId <= 0 && customerName.isNotEmpty() && createNewCustomerWhenBillingIfNotExists != "Y") {
                                        errorMessage = "Please select an existing customer"
                                        showErrorDialog = true
                                        return@Button
                                    }

                                    saving = true
                                    // Capture values for printing
                                    val itemsForPrint = orderItems.toList()
                                    val customerForPrint = customerName.ifEmpty { "Walk-in" }
                                    val salesRepForPrint = selectedSalesRep?.name ?: salesRepSearchQuery
                                    val discountForPrint = calculatedDiscount
                                    val grossForPrint = grossTotal
                                    val netForPrint = netTotal
                                    val orderNoForPrint = orderNo
                                    val templateForPrint = billTemplate

                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            saveOrder(
                                                context = context,
                                                token = token!!,
                                                orderNo = orderNo,
                                                orderItems = orderItems,
                                                customerId = customerId,
                                                customerName = customerName,
                                                salesRefId = selectedSalesRep?.id ?: 0,
                                                salesRefName = selectedSalesRep?.name ?: "",
                                                discountPercentage = discountPercent,
                                                discountValue = calculatedDiscount,
                                                grossTotal = grossTotal,
                                                netTotal = netTotal,
                                                paymentOption = selectedPaymentOption?.value ?: "CASH",
                                                memo = memoText,
                                                deliveryDate = deliveryDateMillis
                                            )
                                        }
                                        saving = false
                                        if (result.startsWith("Success")) {
                                            // Print the order only if config is enabled
                                            if (printReceiptWhenCreatingOrder == "Y") {
                                                try {
                                                    val printerService = PrinterService.getInstance()
                                                    if (printerService.isConnected() && templateForPrint != null) {
                                                        val pdfGenerator = PdfReceiptGenerator(context)
                                                        val receiptData = buildOrderReceiptData(
                                                            orderItems = itemsForPrint,
                                                            customer = customerForPrint,
                                                            salesRep = salesRepForPrint,
                                                            discount = discountForPrint,
                                                            grossTotal = grossForPrint,
                                                            netTotal = netForPrint,
                                                            orderNo = orderNoForPrint,
                                                            billTemplate = templateForPrint
                                                        )
                                                        Log.d("OrderForm", "Printing order receipt...")
                                                        printerService.printPdfReceipt(pdfGenerator, receiptData)
                                                        Log.d("OrderForm", "Print completed")
                                                    } else if (!printerService.isConnected()) {
                                                        // Try to connect
                                                        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                                        if (bluetoothAdapter != null) {
                                                            val pairedDevices = bluetoothAdapter.bondedDevices.toList()
                                                            if (pairedDevices.isNotEmpty()) {
                                                                val printerDevice = pairedDevices.firstOrNull()
                                                                if (printerDevice != null) {
                                                                    printerService.connectDevice(printerDevice)
                                                                    if (printerService.isConnected() && templateForPrint != null) {
                                                                        val pdfGenerator = PdfReceiptGenerator(context)
                                                                        val receiptData = buildOrderReceiptData(
                                                                            orderItems = itemsForPrint,
                                                                            customer = customerForPrint,
                                                                            salesRep = salesRepForPrint,
                                                                            discount = discountForPrint,
                                                                            grossTotal = grossForPrint,
                                                                            netTotal = netForPrint,
                                                                            orderNo = orderNoForPrint,
                                                                            billTemplate = templateForPrint
                                                                        )
                                                                        printerService.printPdfReceipt(pdfGenerator, receiptData)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        Log.d("OrderForm", "Printer not connected")
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("OrderForm", "Print error: ${e.message}")
                                                }
                                            }

                                            successMessage = result
                                            showSuccessDialog = true
                                            // Reset form
                                            orderItems = emptyList()
                                            promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                            selectedCustomer = null
                                            selectedSalesRep = null
                                            customerSearchQuery = ""
                                            salesRepSearchQuery = ""
                                            discountPercentage = "0"
                                            discountValue = "0"
                                            memoText = ""
                                            deliveryDateMillis = null
                                            // Get next order number
                                            val nextNo = withContext(Dispatchers.IO) { fetchNextOrderNo(token!!) }
                                            orderNo = nextNo
                                        } else {
                                            errorMessage = result
                                            showErrorDialog = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            enabled = orderItems.isNotEmpty() && !saving
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text("Create Order", fontWeight = FontWeight.Bold)
                            }
                        }
                        }

                        // Print Order Button (only when existing order loaded)
                        if (orderId != null) {
                            Button(
                                onClick = {
                                    if (orderItems.isNotEmpty()) {
                                        saving = true
                                        scope.launch {
                                            // Print order as TRUE COPY
                                            val itemsForPrint = orderItems.toList()
                                            val customerForPrint = selectedCustomer?.name ?: customerSearchQuery.ifEmpty { "Walk-in" }
                                            val salesRepForPrint = selectedSalesRep?.name ?: salesRepSearchQuery
                                            val templateForPrint = billTemplate

                                            withContext(Dispatchers.IO) {
                                                printOrderTrueCopy(
                                                    context = context,
                                                    orderNo = orderNo,
                                                    orderItems = itemsForPrint,
                                                    customerName = customerForPrint,
                                                    salesRepName = salesRepForPrint,
                                                    grossTotal = grossTotal,
                                                    discount = calculatedDiscount,
                                                    netTotal = netTotal
                                                )
                                            }
                                            saving = false
                                            successMessage = "Order printed successfully"
                                            showSuccessDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                enabled = orderItems.isNotEmpty() && !saving
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text("Print Order", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Update Order Button (only shown when editing existing order)
                        if (orderId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (orderItems.isNotEmpty() && orderId != null) {
                                        saving = true
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                updateOrder(
                                                    context = context,
                                                    token = token!!,
                                                    orderId = orderId!!,
                                                    orderNo = orderNo,
                                                    orderItems = orderItems,
                                                    customerId = selectedCustomer?.id ?: 0,
                                                    customerName = selectedCustomer?.name ?: customerSearchQuery,
                                                    salesRefId = selectedSalesRep?.id ?: 0,
                                                    salesRefName = selectedSalesRep?.name ?: "",
                                                    discountPercentage = discountPercent,
                                                    discountValue = calculatedDiscount,
                                                    grossTotal = grossTotal,
                                                    netTotal = netTotal,
                                                    paymentOption = selectedPaymentOption?.value ?: "CASH",
                                                    memo = memoText,
                                                    deliveryDate = deliveryDateMillis
                                                )
                                            }
                                            saving = false
                                            if (result.startsWith("Success")) {
                                                successMessage = result
                                                showSuccessDialog = true
                                            } else {
                                                errorMessage = result
                                                showErrorDialog = true
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                enabled = orderItems.isNotEmpty() && !saving && orderId != null
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text("Update Order", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Create Bill Button (shown when order is loaded with items)
                        if (orderId != null && orderItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (orderItems.isNotEmpty() && orderId != null) {
                                        saving = true
                                        scope.launch {
                                            val itemsForPrint = orderItems.toList()
                                            val customerForPrint = selectedCustomer
                                            val salesRepForPrint = selectedSalesRep
                                            val result = withContext(Dispatchers.IO) {
                                                createBillByAcceptedOrder(
                                                    context = context,
                                                    token = token!!,
                                                    orderId = orderId!!,
                                                    orderNo = orderNo,
                                                    orderItems = orderItems,
                                                    customerId = selectedCustomer?.id ?: 0,
                                                    salesRefId = selectedSalesRep?.id ?: 0,
                                                    discountPercentage = discountPercent,
                                                    discountValue = calculatedDiscount
                                                )
                                            }

                                            if (result.success && result.billNo != null) {
                                                // Print the bill
                                                val printResult = withContext(Dispatchers.IO) {
                                                    printBillFromOrder(
                                                        context = context,
                                                        billNo = result.billNo,
                                                        orderItems = itemsForPrint,
                                                        customerName = customerForPrint?.name ?: "Walk-in",
                                                        salesRepName = salesRepForPrint?.name ?: "",
                                                        grossTotal = grossTotal,
                                                        discount = calculatedDiscount,
                                                        netTotal = netTotal
                                                    )
                                                }
                                                saving = false

                                                if (printResult) {
                                                    successMessage = "Bill #${result.billNo} created and printed successfully"
                                                } else {
                                                    successMessage = "Bill #${result.billNo} created but printing failed"
                                                }
                                                showSuccessDialog = true

                                                // Reset form after creating bill
                                                orderId = null
                                                isOrderAccepted = false
                                                orderItems = emptyList()
                                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                                selectedCustomer = null
                                                selectedSalesRep = null
                                                customerSearchQuery = ""
                                                salesRepSearchQuery = ""
                                                discountPercentage = "0"
                                                discountValue = "0"
                                                memoText = ""
                                                deliveryDateMillis = null
                                                // Get next order number
                                                val nextNo = withContext(Dispatchers.IO) { fetchNextOrderNo(token!!) }
                                                orderNo = nextNo
                                            } else {
                                                saving = false
                                                errorMessage = result.message
                                                showErrorDialog = true
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                enabled = !saving
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Text("Create Invoice & Print", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                    }
                }
            } else {
                // Portrait layout - stacked
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0F3460))
                        .padding(8.dp)
                ) {
                    // Add Item Button
                    item {
                        Button(
                            onClick = { showItemSearch = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Item")
                        }
                    }

                    // Order Items
                    itemsIndexed(orderItems) { index, item ->
                        OrderItemCard(
                            item = item,
                            index = index,
                            onEdit = {
                                editingItemIndex = index
                                editingQty = item.qty.toString()
                                editingPrice = item.unitPrice.toString()
                                editingDiscount = item.discountPercentage.toString()
                                editingDiscountValue = item.discountValue.toString()
                                editingFreeIssue = item.freeIssueQty.toString()
                            },
                            onDelete = {
                                val removed = orderItems.getOrNull(index)
                                orderItems = orderItems.toMutableList().apply { removeAt(index) }
                                if (removed?.isPromoFreeItem == true) {
                                    promoState.onPromoLineDeleted(removed.promotionId)
                                }
                                schedulePromotionCheck()
                            }
                        )
                    }

                    // Order Summary
                    item {
                        OrderSummaryCard(
                            orderNo = orderNo,
                            grossTotal = grossTotal,
                            discountPercentage = discountPercentage,
                            discountValue = discountValue,
                            netTotal = netTotal,
                            noOfItems = noOfItems,
                            noOfPcs = noOfPcs,
                            onOrderNoChange = { orderNo = it },
                            onDiscountPercentageChange = {
                                discountPercentage = it
                                if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                    discountValue = "0"
                                }
                            },
                            onDiscountValueChange = {
                                discountValue = it
                                if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                    discountPercentage = "0"
                                }
                            },
                            orderId = orderId,
                            isOrderAccepted = isOrderAccepted,
                            isLoadingOrder = isLoadingOrder,
                            onLoadOrder = {
                                val orderNoInt = orderNo.toIntOrNull()
                                if (orderNoInt != null && orderNoInt > 0 && token != null) {
                                    isLoadingOrder = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            loadOrderForm(token!!, orderNoInt)
                                        }
                                        isLoadingOrder = false
                                        if (result != null) {
                                            orderId = result.id
                                            orderNo = result.orderNo.toString()
                                            isOrderAccepted = result.isAccepted
                                            deliveryDateMillis = result.deliveryDate
                                            discountPercentage = result.discountPercentage.toString()
                                            discountValue = result.discountValue.toString()
                                            // Set customer
                                            val customer = customers.find { it.id == result.customerId }
                                            selectedCustomer = customer
                                            customerSearchQuery = customer?.name ?: ""
                                            // Set sales rep
                                            val salesRef = salesReps.find { it.id == result.salesRefId }
                                            selectedSalesRep = salesRef
                                            salesRepSearchQuery = salesRef?.name ?: ""
                                            // Set items - fill in missing names/codes from availableItems
                                            orderItems = result.items.map { item ->
                                                if (item.itemName.isEmpty() || item.itemCode.isEmpty()) {
                                                    val availableItem = availableItems.find { it.id == item.itemId }
                                                    item.copy(
                                                        itemName = if (item.itemName.isEmpty()) availableItem?.name ?: "Unknown Item" else item.itemName,
                                                        itemCode = if (item.itemCode.isEmpty()) availableItem?.code ?: "" else item.itemCode
                                                    )
                                                } else {
                                                    item
                                                }
                                            }
                                        } else {
                                            errorMessage = "Order not found"
                                            showErrorDialog = true
                                        }
                                    }
                                }
                            }
                        )
                    }

                    // Customer & Sales Rep Selection
                    item {
                        CustomerSalesRepCard(
                            selectedCustomer = selectedCustomer,
                            selectedSalesRep = selectedSalesRep,
                            customers = customers,
                            salesReps = salesReps,
                            customerSearchQuery = customerSearchQuery,
                            salesRepSearchQuery = salesRepSearchQuery,
                            onCustomerSelected = { customer ->
                                selectedCustomer = customer
                                customerSearchQuery = customer?.name ?: ""
                                // Populate address dropdown with all addresses of customers with same name
                                if (customer != null) {
                                    val sameNameCustomers = customers.filter { it.name.equals(customer.name, ignoreCase = true) }
                                    customerAddresses = sameNameCustomers.map { it.address }.distinct()
                                    selectedAddress = customer.address
                                } else {
                                    customerAddresses = emptyList()
                                    selectedAddress = ""
                                }
                            },
                            onSalesRepSelected = { selectedSalesRep = it; salesRepSearchQuery = it?.name ?: "" },
                            onCustomerSearchChange = { customerSearchQuery = it },
                            onSalesRepSearchChange = { salesRepSearchQuery = it },
                            showCustomerAddressInOrderForm = showCustomerAddressInOrderForm == "Y",
                            customerAddresses = customerAddresses,
                            selectedAddress = selectedAddress,
                            onAddressSelected = { address ->
                                selectedAddress = address
                                // Find and select the customer with this address
                                val customerWithAddress = customers.find {
                                    it.name.equals(selectedCustomer?.name, ignoreCase = true) && it.address == address
                                }
                                if (customerWithAddress != null) {
                                    selectedCustomer = customerWithAddress
                                }
                            },
                            showPaymentOptionsInOrderForm = showPaymentOptionsInOrderForm == "Y",
                            paymentOptions = paymentOptions,
                            selectedPaymentOption = selectedPaymentOption,
                            onPaymentOptionSelected = { selectedPaymentOption = it },
                            showMemoInOrderFormAndCreditBill = showMemoInOrderFormAndCreditBill == "Y",
                            memoText = memoText,
                            onMemoTextChange = { memoText = it },
                            showDeliveryDateInOrderForm = showDeliveryDateInOrderForm == "Y",
                            deliveryDateMillis = deliveryDateMillis,
                            onDeliveryDateSelected = { deliveryDateMillis = it }
                        )
                    }

                    // Order Action Buttons
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            // Create Order Button (only when no existing order loaded)
                            if (orderId == null) {
                                Button(
                                    onClick = {
                                        if (orderItems.isNotEmpty()) {
                                            // Validate customer selection or allow new customer creation
                                            val customerId = selectedCustomer?.id ?: 0L
                                            val customerName = if (customerId > 0) {
                                                selectedCustomer?.name ?: ""
                                            } else {
                                                customerSearchQuery.trim()
                                            }

                                            // Check if customer is required
                                            if (customerId <= 0 && customerName.isEmpty()) {
                                                errorMessage = "Please select or enter a customer name"
                                                showErrorDialog = true
                                                return@Button
                                            }

                                            // If no customer selected but name entered, check config
                                            if (customerId <= 0 && customerName.isNotEmpty() && createNewCustomerWhenBillingIfNotExists != "Y") {
                                                errorMessage = "Please select an existing customer"
                                                showErrorDialog = true
                                                return@Button
                                            }

                                            saving = true
                                            // Capture values for printing
                                            val itemsForPrint = orderItems.toList()
                                            val customerForPrint = customerName.ifEmpty { "Walk-in" }
                                            val salesRepForPrint = selectedSalesRep?.name ?: salesRepSearchQuery
                                            val discountForPrint = calculatedDiscount
                                            val grossForPrint = grossTotal
                                            val netForPrint = netTotal
                                            val orderNoForPrint = orderNo
                                            val templateForPrint = billTemplate

                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    saveOrder(
                                                        context = context,
                                                        token = token!!,
                                                        orderNo = orderNo,
                                                        orderItems = orderItems,
                                                        customerId = customerId,
                                                        customerName = customerName,
                                                        salesRefId = selectedSalesRep?.id ?: 0,
                                                        salesRefName = selectedSalesRep?.name ?: "",
                                                        discountPercentage = discountPercent,
                                                        discountValue = calculatedDiscount,
                                                        grossTotal = grossTotal,
                                                        netTotal = netTotal,
                                                        paymentOption = selectedPaymentOption?.value ?: "CASH",
                                                        memo = memoText,
                                                        deliveryDate = deliveryDateMillis
                                                    )
                                                }
                                                saving = false
                                                if (result.startsWith("Success")) {
                                                    // Print the order only if config is enabled
                                                    if (printReceiptWhenCreatingOrder == "Y") {
                                                        try {
                                                            val printerService = PrinterService.getInstance()
                                                            if (printerService.isConnected() && templateForPrint != null) {
                                                                val pdfGenerator = PdfReceiptGenerator(context)
                                                                val receiptData = buildOrderReceiptData(
                                                                    orderItems = itemsForPrint,
                                                                    customer = customerForPrint,
                                                                    salesRep = salesRepForPrint,
                                                                    discount = discountForPrint,
                                                                    grossTotal = grossForPrint,
                                                                    netTotal = netForPrint,
                                                                    orderNo = orderNoForPrint,
                                                                    billTemplate = templateForPrint
                                                                )
                                                                Log.d("OrderForm", "Printing order receipt...")
                                                                printerService.printPdfReceipt(pdfGenerator, receiptData)
                                                                Log.d("OrderForm", "Print completed")
                                                            } else if (!printerService.isConnected()) {
                                                                // Try to connect
                                                                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                                                if (bluetoothAdapter != null) {
                                                                    val pairedDevices = bluetoothAdapter.bondedDevices.toList()
                                                                    if (pairedDevices.isNotEmpty()) {
                                                                        val printerDevice = pairedDevices.firstOrNull()
                                                                        if (printerDevice != null) {
                                                                            printerService.connectDevice(printerDevice)
                                                                            if (printerService.isConnected() && templateForPrint != null) {
                                                                                val pdfGenerator = PdfReceiptGenerator(context)
                                                                                val receiptData = buildOrderReceiptData(
                                                                                    orderItems = itemsForPrint,
                                                                                    customer = customerForPrint,
                                                                                    salesRep = salesRepForPrint,
                                                                                    discount = discountForPrint,
                                                                                    grossTotal = grossForPrint,
                                                                                    netTotal = netForPrint,
                                                                                    orderNo = orderNoForPrint,
                                                                                    billTemplate = templateForPrint
                                                                                )
                                                                                printerService.printPdfReceipt(pdfGenerator, receiptData)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                Log.d("OrderForm", "Printer not connected")
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("OrderForm", "Print error: ${e.message}")
                                                        }
                                                    }

                                                    successMessage = result
                                                    showSuccessDialog = true
                                                    orderItems = emptyList()
                                                    promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                                    selectedCustomer = null
                                                    selectedSalesRep = null
                                                    customerSearchQuery = ""
                                                    salesRepSearchQuery = ""
                                                    discountPercentage = "0"
                                                    discountValue = "0"
                                                    memoText = ""
                                                    deliveryDateMillis = null
                                                    orderId = null
                                                    isOrderAccepted = false
                                                    val nextNo = withContext(Dispatchers.IO) { fetchNextOrderNo(token!!) }
                                                    orderNo = nextNo
                                                } else {
                                                    errorMessage = result
                                                    showErrorDialog = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    enabled = orderItems.isNotEmpty() && !saving
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text("Create Order", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }

                            // Print Order Button (only when existing order loaded)
                            if (orderId != null) {
                                Button(
                                    onClick = {
                                        if (orderItems.isNotEmpty()) {
                                            saving = true
                                            scope.launch {
                                                // Print order as TRUE COPY
                                                val itemsForPrint = orderItems.toList()
                                                val customerForPrint = selectedCustomer?.name ?: customerSearchQuery.ifEmpty { "Walk-in" }
                                                val salesRepForPrint = selectedSalesRep?.name ?: salesRepSearchQuery

                                                withContext(Dispatchers.IO) {
                                                    printOrderTrueCopy(
                                                        context = context,
                                                        orderNo = orderNo,
                                                        orderItems = itemsForPrint,
                                                        customerName = customerForPrint,
                                                        salesRepName = salesRepForPrint,
                                                        grossTotal = grossTotal,
                                                        discount = calculatedDiscount,
                                                        netTotal = netTotal
                                                    )
                                                }
                                                saving = false
                                                successMessage = "Order printed successfully"
                                                showSuccessDialog = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                    enabled = orderItems.isNotEmpty() && !saving
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text("Print Order", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Update Order Button (when editing existing order)
                            if (orderId != null) {
                                Button(
                                    onClick = {
                                        if (orderItems.isNotEmpty() && orderId != null) {
                                            saving = true
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    updateOrder(
                                                        context = context,
                                                        token = token!!,
                                                        orderId = orderId!!,
                                                        orderNo = orderNo,
                                                        orderItems = orderItems,
                                                        customerId = selectedCustomer?.id ?: 0,
                                                        customerName = selectedCustomer?.name ?: customerSearchQuery,
                                                        salesRefId = selectedSalesRep?.id ?: 0,
                                                        salesRefName = selectedSalesRep?.name ?: salesRepSearchQuery,
                                                        discountPercentage = discountPercent,
                                                        discountValue = calculatedDiscount,
                                                        grossTotal = grossTotal,
                                                        netTotal = netTotal,
                                                        paymentOption = selectedPaymentOption?.value ?: "CASH",
                                                        memo = memoText,
                                                        deliveryDate = deliveryDateMillis
                                                    )
                                                }
                                                saving = false
                                                if (result.startsWith("Success")) {
                                                    successMessage = result
                                                    showSuccessDialog = true
                                                } else {
                                                    errorMessage = result
                                                    showErrorDialog = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                    enabled = orderItems.isNotEmpty() && !saving
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text("Update Order", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Create Bill & Print Button (shown when order is loaded with items)
                            if (orderId != null && orderItems.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        saving = true
                                        scope.launch {
                                            val itemsForPrint = orderItems.toList()
                                            val customerForPrint = selectedCustomer
                                            val salesRepForPrint = selectedSalesRep
                                            val result = withContext(Dispatchers.IO) {
                                                createBillByAcceptedOrder(
                                                    context = context,
                                                    token = token!!,
                                                    orderId = orderId!!,
                                                    orderNo = orderNo,
                                                    orderItems = orderItems,
                                                    customerId = selectedCustomer?.id ?: 0,
                                                    salesRefId = selectedSalesRep?.id ?: 0,
                                                    discountPercentage = discountPercent,
                                                    discountValue = calculatedDiscount
                                                )
                                            }

                                            if (result.success && result.billNo != null) {
                                                // Print the bill
                                                val printResult = withContext(Dispatchers.IO) {
                                                    printBillFromOrder(
                                                        context = context,
                                                        billNo = result.billNo,
                                                        orderItems = itemsForPrint,
                                                        customerName = customerForPrint?.name ?: "Walk-in",
                                                        salesRepName = salesRepForPrint?.name ?: "",
                                                        grossTotal = grossTotal,
                                                        discount = calculatedDiscount,
                                                        netTotal = netTotal
                                                    )
                                                }
                                                saving = false

                                                if (printResult) {
                                                    successMessage = "Bill #${result.billNo} created and printed successfully"
                                                } else {
                                                    successMessage = "Bill #${result.billNo} created but printing failed"
                                                }
                                                showSuccessDialog = true

                                                // Reset form after creating bill
                                                orderId = null
                                                isOrderAccepted = false
                                                orderItems = emptyList()
                                                promoState.reset(); promoOffers = emptyList(); showPromoDialog = false
                                                selectedCustomer = null
                                                selectedSalesRep = null
                                                customerSearchQuery = ""
                                                salesRepSearchQuery = ""
                                                discountPercentage = "0"
                                                discountValue = "0"
                                                memoText = ""
                                                deliveryDateMillis = null
                                                // Get next order number
                                                val nextNo = withContext(Dispatchers.IO) { fetchNextOrderNo(token!!) }
                                                orderNo = nextNo
                                            } else {
                                                saving = false
                                                errorMessage = result.message
                                                showErrorDialog = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                                    enabled = !saving
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text("Create Invoice & Print", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Item Search Dialog
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
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredItems.take(30)) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedBillItem = item
                                        dialogPrice = item.sellingPrice.toString()
                                        dialogQty = "1"
                                        dialogDiscount = "0"
                                        dialogFreeIssue = "0"
                                        selectedSupplier = item.billSupplierList.firstOrNull()
                                        selectedInvoice = if (doNotCheckStockQtyWhenMakeOrders == "Y") {
                                            selectedSupplier?.billInvoiceList?.firstOrNull()
                                        } else {
                                            selectedSupplier?.billInvoiceList?.firstOrNull { it.qty > 0 }
                                        }
                                        showItemSearch = false
                                        showItemDetailDialog = true
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${item.code} - Rs. ${String.format("%.2f", item.sellingPrice)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showItemSearch = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Item Detail Dialog (for selecting supplier, invoice, qty)
    if (showItemDetailDialog && selectedBillItem != null) {
        AlertDialog(
            onDismissRequest = { showItemDetailDialog = false },
            title = { Text("Add ${selectedBillItem!!.name}") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Item info
                    Text("Code: ${selectedBillItem!!.code}", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Supplier selection (if multiple)
                    if (selectedBillItem!!.billSupplierList.size > 1) {
                        Text("Supplier", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        selectedBillItem!!.billSupplierList.forEach { supplier ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSupplier = supplier
                                        selectedInvoice = if (doNotCheckStockQtyWhenMakeOrders == "Y") {
                                            supplier.billInvoiceList.firstOrNull()
                                        } else {
                                            supplier.billInvoiceList.firstOrNull { it.qty > 0 }
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSupplier?.supplierId == supplier.supplierId,
                                    onClick = {
                                        selectedSupplier = supplier
                                        selectedInvoice = if (doNotCheckStockQtyWhenMakeOrders == "Y") {
                                            supplier.billInvoiceList.firstOrNull()
                                        } else {
                                            supplier.billInvoiceList.firstOrNull { it.qty > 0 }
                                        }
                                    }
                                )
                                Text(supplier.supplierName, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Invoice selection (if multiple)
                    val invoicesToShow = if (doNotCheckStockQtyWhenMakeOrders == "Y") {
                        selectedSupplier?.billInvoiceList ?: emptyList()
                    } else {
                        selectedSupplier?.billInvoiceList?.filter { it.qty > 0 } ?: emptyList()
                    }
                    if (selectedSupplier != null && invoicesToShow.size > 1) {
                        Text("Invoice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        invoicesToShow.forEach { invoice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedInvoice = invoice }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedInvoice?.invoiceId == invoice.invoiceId,
                                    onClick = { selectedInvoice = invoice }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(invoice.invoiceName)
                                    Text(
                                        "Qty: ${invoice.qty.toInt()} | Price: ${String.format("%.2f", invoice.sellingPrice)}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Available Qty display
                    if (selectedInvoice != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                        ) {
                            Text(
                                "Available Qty: ${selectedInvoice!!.qty.toInt()}",
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1976D2)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Quantity
                    OutlinedTextField(
                        value = dialogQty,
                        onValueChange = { dialogQty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Selling Price (locked unless edit selling price configs allow it)
                    OutlinedTextField(
                        value = dialogPrice,
                        onValueChange = { dialogPrice = it },
                        label = { Text("Selling Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = canEditSellingPrice
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Discount %
                    OutlinedTextField(
                        value = dialogDiscount,
                        onValueChange = { dialogDiscount = it },
                        label = { Text("Discount %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Free Issue Qty (only show if config is enabled)
                    if (showFreeIssueInOrderForm == "Y") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dialogFreeIssue,
                            onValueChange = { dialogFreeIssue = it },
                            label = { Text("Free Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = dialogQty.toDoubleOrNull() ?: 0.0
                        val price = dialogPrice.toDoubleOrNull() ?: selectedBillItem!!.sellingPrice
                        val discount = dialogDiscount.toDoubleOrNull() ?: 0.0
                        val freeIssue = dialogFreeIssue.toDoubleOrNull() ?: 0.0
                        val discountVal = (discount * price) / 100

                        // Upward-only price edits: never below the original selling price
                        val basePrice = selectedInvoice?.sellingPrice ?: selectedBillItem!!.sellingPrice
                        if (sellingPriceUpwardOnly && price < basePrice) {
                            errorMessage = "Selling Price can not be less than ${String.format("%.2f", basePrice)}"
                            showErrorDialog = true
                            return@Button
                        }

                        if (qty > 0 && selectedSupplier != null && selectedInvoice != null) {
                            // Check for duplicate
                            val existingIndex = orderItems.indexOfFirst {
                                it.itemId == selectedBillItem!!.id &&
                                it.supplierId == selectedSupplier!!.supplierId &&
                                it.invoiceId == selectedInvoice!!.invoiceId
                            }

                            if (existingIndex >= 0) {
                                // Update existing item quantity
                                orderItems = orderItems.toMutableList().apply {
                                    val existing = this[existingIndex]
                                    this[existingIndex] = existing.copy(
                                        qty = existing.qty + qty,
                                        freeIssueQty = existing.freeIssueQty + freeIssue
                                    )
                                }
                            } else {
                                // Add new item
                                val newItem = OrderItem(
                                    itemId = selectedBillItem!!.id,
                                    itemCode = selectedBillItem!!.code,
                                    itemName = selectedBillItem!!.name,
                                    supplierId = selectedSupplier!!.supplierId,
                                    supplierName = selectedSupplier!!.supplierName,
                                    invoiceId = selectedInvoice!!.invoiceId,
                                    invoiceName = selectedInvoice!!.invoiceName,
                                    qty = qty,
                                    unitPrice = price,
                                    discountPercentage = discount,
                                    discountValue = discountVal,
                                    totalQty = selectedInvoice!!.qty,
                                    unitCost = selectedInvoice!!.unitCost,
                                    freeIssueQty = freeIssue
                                )
                                orderItems = orderItems + newItem
                            }
                            schedulePromotionCheck()
                        }
                        showItemDetailDialog = false
                        selectedBillItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showItemDetailDialog = false
                    selectedBillItem = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Item Dialog
    if (editingItemIndex != null) {
        val item = orderItems[editingItemIndex!!]
        AlertDialog(
            onDismissRequest = { editingItemIndex = null },
            title = { Text("Edit ${item.itemName}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingQty,
                        onValueChange = { editingQty = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        value = editingDiscount,
                        onValueChange = {
                            editingDiscount = it
                            if (it.isNotEmpty() && (it.toDoubleOrNull() ?: 0.0) > 0) {
                                editingDiscountValue = "0"
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
                                editingDiscount = "0"
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newQty = editingQty.toDoubleOrNull() ?: item.qty
                        val newPrice = editingPrice.toDoubleOrNull() ?: item.unitPrice
                        val newDiscountPercent = editingDiscount.toDoubleOrNull() ?: 0.0
                        val newDiscountVal = editingDiscountValue.toDoubleOrNull() ?: 0.0
                        val newFreeIssue = editingFreeIssue.toDoubleOrNull() ?: item.freeIssueQty

                        // Upward-only price edits: never below the current selling price
                        if (sellingPriceUpwardOnly && newPrice < item.unitPrice) {
                            errorMessage = "Selling Price can not be less than ${String.format("%.2f", item.unitPrice)}"
                            showErrorDialog = true
                            return@Button
                        }

                        // Calculate final discount values based on which field has a value
                        val (finalDiscountPercentage, finalDiscountValue) = if (newDiscountPercent > 0) {
                            Pair(newDiscountPercent, (newDiscountPercent * newPrice) / 100)
                        } else if (newDiscountVal > 0) {
                            val calcPercent = if (newPrice > 0) (newDiscountVal / newPrice) * 100 else 0.0
                            Pair(calcPercent, newDiscountVal)
                        } else {
                            Pair(0.0, 0.0)
                        }

                        orderItems = orderItems.toMutableList().apply {
                            this[editingItemIndex!!] = item.copy(
                                qty = newQty,
                                unitPrice = newPrice,
                                discountPercentage = finalDiscountPercentage,
                                discountValue = finalDiscountValue,
                                freeIssueQty = newFreeIssue
                            )
                        }
                        schedulePromotionCheck()
                        editingItemIndex = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItemIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Promotion Offer Dialog
    if (showPromoDialog && promoOffers.isNotEmpty()) {
        PromotionOfferDialog(
            offers = promoOffers,
            onApply = { offer ->
                orderItems = applyOfferToLines(offer, orderItems)
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

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error", color = Color.Red) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success", color = Color(0xFF4CAF50)) },
            text = { Text(successMessage) },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun OrderItemsTable(
    items: List<OrderItem>,
    showFreeIssue: Boolean = false,
    onEditItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007BFF))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Code", modifier = Modifier.weight(0.8f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Name", modifier = Modifier.weight(if (showFreeIssue) 1.0f else 1.2f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text("Price", modifier = Modifier.weight(0.7f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                Text("Qty", modifier = Modifier.weight(0.5f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                if (showFreeIssue) {
                    Text("Free", modifier = Modifier.weight(0.5f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                }
                Text("Total", modifier = Modifier.weight(0.7f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                Spacer(modifier = Modifier.width(60.dp))
            }

            // Table Body
            LazyColumn {
                itemsIndexed(items) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color(0xFF1A1A2E) else Color(0xFF252547))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.itemCode, modifier = Modifier.weight(0.8f), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.isPromoFreeItem) "${item.itemName} (FREE)" else item.itemName,
                            modifier = Modifier.weight(if (showFreeIssue) 1.0f else 1.2f),
                            color = if (item.isPromoFreeItem) Color(0xFF66BB6A) else Color.White,
                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(String.format("%.2f", item.unitPrice), modifier = Modifier.weight(0.7f), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.End)
                        Text(item.qty.toInt().toString(), modifier = Modifier.weight(0.5f), color = Color.White, fontSize = 11.sp, textAlign = TextAlign.End)
                        if (showFreeIssue) {
                            Text(item.freeIssueQty.toInt().toString(), modifier = Modifier.weight(0.5f), color = Color(0xFFFF9800), fontSize = 11.sp, textAlign = TextAlign.End)
                        }
                        Text(String.format("%.2f", item.totalPrice), modifier = Modifier.weight(0.7f), color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Row(modifier = Modifier.width(60.dp)) {
                            // Promotion free lines are managed by the promo engine - delete only
                            if (!item.isPromoFreeItem) {
                                IconButton(onClick = { onEditItem(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { onDeleteItem(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE94560), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderItemCard(
    item: OrderItem,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (item.isPromoFreeItem) "${item.itemName} (FREE)" else item.itemName,
                    color = if (item.isPromoFreeItem) Color(0xFF66BB6A) else Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Text("${item.itemCode} | Qty: ${item.qty.toInt()} x ${String.format("%.2f", item.unitPrice)}", color = Color.Gray, fontSize = 12.sp)
                if (item.discountPercentage > 0) {
                    Text("Discount: ${item.discountPercentage}%", color = Color(0xFFFF9800), fontSize = 11.sp)
                }
                if (item.freeIssueQty > 0) {
                    Text("Free: ${item.freeIssueQty.toInt()}", color = Color(0xFFFF9800), fontSize = 11.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format("Rs. %.2f", item.totalPrice), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row {
                    // Promotion free lines are managed by the promo engine - delete only
                    if (!item.isPromoFreeItem) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2196F3), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE94560), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderSummaryPanel(
    orderNo: String,
    grossTotal: Double,
    discountPercentage: String,
    discountValue: String,
    netTotal: Double,
    noOfItems: Int,
    noOfPcs: Int,
    selectedCustomer: CustomerOption?,
    selectedSalesRep: SalesRefOption?,
    customers: List<CustomerOption>,
    salesReps: List<SalesRefOption>,
    customerSearchQuery: String,
    salesRepSearchQuery: String,
    onOrderNoChange: (String) -> Unit,
    onDiscountPercentageChange: (String) -> Unit,
    onDiscountValueChange: (String) -> Unit,
    onCustomerSelected: (CustomerOption?) -> Unit,
    onSalesRepSelected: (SalesRefOption?) -> Unit,
    onCustomerSearchChange: (String) -> Unit,
    onSalesRepSearchChange: (String) -> Unit,
    // New parameters for order editing
    orderId: Long? = null,
    isOrderAccepted: Boolean = false,
    isLoadingOrder: Boolean = false,
    onLoadOrder: () -> Unit = {},
    // Address dropdown parameters
    showCustomerAddressInOrderForm: Boolean = false,
    customerAddresses: List<String> = emptyList(),
    selectedAddress: String = "",
    onAddressSelected: (String) -> Unit = {},
    // Payment options dropdown parameters
    showPaymentOptionsInOrderForm: Boolean = false,
    paymentOptions: List<PaymentOption> = emptyList(),
    selectedPaymentOption: PaymentOption? = null,
    onPaymentOptionSelected: (PaymentOption) -> Unit = {},
    // Memo parameters
    showMemoInOrderFormAndCreditBill: Boolean = false,
    memoText: String = "",
    onMemoTextChange: (String) -> Unit = {},
    // Delivery date parameters
    showDeliveryDateInOrderForm: Boolean = false,
    deliveryDateMillis: Long? = null,
    onDeliveryDateSelected: (Long?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0))
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Order No with load on Enter
            OutlinedTextField(
                value = orderNo,
                onValueChange = onOrderNoChange,
                label = { Text("Order No. (Press Enter to load)", color = Color.White, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onLoadOrder() }
                ),
                trailingIcon = {
                    if (isLoadingOrder) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (orderId != null) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editing",
                            tint = Color.Yellow
                        )
                    }
                }
            )

            // Show order status if editing
            if (orderId != null) {
                Text(
                    text = if (isOrderAccepted) "Status: Accepted âœ“" else "Status: Pending",
                    color = if (isOrderAccepted) Color(0xFF4CAF50) else Color.Yellow,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gross Total
            SummaryRow("Gross Total", String.format("%.2f", grossTotal))

            Spacer(modifier = Modifier.height(8.dp))

            // Discount % and Value
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = discountPercentage,
                    onValueChange = onDiscountPercentageChange,
                    label = { Text("Disc %", color = Color.White, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = discountValue,
                    onValueChange = onDiscountValueChange,
                    label = { Text("Disc Val", color = Color.White, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Net Total
            SummaryRow("Net Total", String.format("%.2f", netTotal), isHighlighted = true)

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.3f))

            // Customer autocomplete
            AutoCompleteField(
                value = customerSearchQuery,
                onValueChange = onCustomerSearchChange,
                label = "Customer",
                suggestions = customers.filter {
                    customerSearchQuery.isEmpty() ||
                    it.name.contains(customerSearchQuery, ignoreCase = true)
                },
                onSuggestionSelected = { onCustomerSelected(it as CustomerOption) },
                displayText = { (it as CustomerOption).name }
            )

            // Address dropdown - show only after customer is selected, and if config enabled or multiple customers have same name
            if (selectedCustomer != null && (showCustomerAddressInOrderForm || customerAddresses.size > 1) && customerAddresses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                var addressExpanded by remember { mutableStateOf(false) }

                Column {
                    OutlinedTextField(
                        value = selectedAddress,
                        onValueChange = { },
                        label = { Text("Customer Address", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addressExpanded = !addressExpanded },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.White.copy(alpha = 0.5f),
                            disabledLabelColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = if (addressExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Expand",
                                tint = Color.White,
                                modifier = Modifier.clickable { addressExpanded = !addressExpanded }
                            )
                        }
                    )

                    if (addressExpanded) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn {
                                items(customerAddresses) { address ->
                                    Text(
                                        text = address.ifEmpty { "(No address)" },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onAddressSelected(address)
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

            // Payment options dropdown - show if config enabled
            if (showPaymentOptionsInOrderForm && paymentOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                var paymentExpanded by remember { mutableStateOf(false) }

                Column {
                    OutlinedTextField(
                        value = selectedPaymentOption?.label ?: "",
                        onValueChange = { },
                        label = { Text("Payment", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paymentExpanded = !paymentExpanded },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.White.copy(alpha = 0.5f),
                            disabledLabelColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = if (paymentExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Expand",
                                tint = Color.White,
                                modifier = Modifier.clickable { paymentExpanded = !paymentExpanded }
                            )
                        }
                    )

                    if (paymentExpanded) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn {
                                items(paymentOptions) { payment ->
                                    Text(
                                        text = payment.label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onPaymentOptionSelected(payment)
                                                paymentExpanded = false
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
            if (showMemoInOrderFormAndCreditBill) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = memoText,
                    onValueChange = onMemoTextChange,
                    label = { Text("Memo", color = Color.White) },
                    placeholder = { Text("Enter memo", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }

            // Delivery date - show if config enabled
            if (showDeliveryDateInOrderForm) {
                Spacer(modifier = Modifier.height(8.dp))
                var showDeliveryDatePicker by remember { mutableStateOf(false) }
                val deliveryDateText = deliveryDateMillis?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
                } ?: ""
                OutlinedTextField(
                    value = deliveryDateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Delivery Date", color = Color.White) },
                    placeholder = { Text("Select delivery date", color = Color.White.copy(alpha = 0.5f)) },
                    trailingIcon = {
                        IconButton(onClick = { showDeliveryDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select delivery date", tint = Color.White)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeliveryDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                if (showDeliveryDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = deliveryDateMillis ?: System.currentTimeMillis()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDeliveryDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                onDeliveryDateSelected(datePickerState.selectedDateMillis)
                                showDeliveryDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeliveryDatePicker = false }) { Text("Cancel") }
                        }
                    ) { DatePicker(state = datePickerState) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sales Rep autocomplete
            AutoCompleteField(
                value = salesRepSearchQuery,
                onValueChange = onSalesRepSearchChange,
                label = "Sales Rep",
                suggestions = salesReps.filter {
                    salesRepSearchQuery.isEmpty() ||
                    it.name.contains(salesRepSearchQuery, ignoreCase = true)
                },
                onSuggestionSelected = { onSalesRepSelected(it as SalesRefOption) },
                displayText = { (it as SalesRefOption).name }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Items & Pieces count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Items: $noOfItems", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Pieces: $noOfPcs", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun <T> AutoCompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<T>,
    onSuggestionSelected: (T) -> Unit,
    displayText: (T) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.isNotEmpty()
            },
            label = { Text(label, color = Color.White) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            singleLine = true
        )

        if (expanded && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                LazyColumn {
                    items(suggestions.take(10)) { item ->
                        Text(
                            text = displayText(item),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSuggestionSelected(item)
                                    expanded = false
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

@Composable
fun SummaryRow(label: String, value: String, isHighlighted: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isHighlighted) 16.sp else 14.sp
        )
        Text(
            value,
            color = if (isHighlighted) Color(0xFF4CAF50) else Color.White,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isHighlighted) 18.sp else 14.sp
        )
    }
}

@Composable
fun OrderSummaryCard(
    orderNo: String,
    grossTotal: Double,
    discountPercentage: String,
    discountValue: String,
    netTotal: Double,
    noOfItems: Int,
    noOfPcs: Int,
    onOrderNoChange: (String) -> Unit,
    onDiscountPercentageChange: (String) -> Unit,
    onDiscountValueChange: (String) -> Unit,
    orderId: Long? = null,
    isOrderAccepted: Boolean = false,
    isLoadingOrder: Boolean = false,
    onLoadOrder: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = orderNo,
                    onValueChange = onOrderNoChange,
                    label = { Text("Order No.", color = Color.White) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { onLoadOrder() }
                    )
                )
                if (isLoadingOrder) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SummaryRow("Gross Total", String.format("Rs. %.2f", grossTotal))

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = discountPercentage,
                    onValueChange = onDiscountPercentageChange,
                    label = { Text("Discount %", color = Color.White, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = discountValue,
                    onValueChange = onDiscountValueChange,
                    label = { Text("Discount Value", color = Color.White, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SummaryRow("Net Total", String.format("Rs. %.2f", netTotal), isHighlighted = true)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("No. of Items: $noOfItems", color = Color.White)
                Text("No. of Pieces: $noOfPcs", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerSalesRepCard(
    selectedCustomer: CustomerOption?,
    selectedSalesRep: SalesRefOption?,
    customers: List<CustomerOption>,
    salesReps: List<SalesRefOption>,
    customerSearchQuery: String,
    salesRepSearchQuery: String,
    onCustomerSelected: (CustomerOption?) -> Unit,
    onSalesRepSelected: (SalesRefOption?) -> Unit,
    onCustomerSearchChange: (String) -> Unit,
    onSalesRepSearchChange: (String) -> Unit,
    // Address dropdown parameters
    showCustomerAddressInOrderForm: Boolean = false,
    customerAddresses: List<String> = emptyList(),
    selectedAddress: String = "",
    onAddressSelected: (String) -> Unit = {},
    // Payment options dropdown parameters
    showPaymentOptionsInOrderForm: Boolean = false,
    paymentOptions: List<PaymentOption> = emptyList(),
    selectedPaymentOption: PaymentOption? = null,
    onPaymentOptionSelected: (PaymentOption) -> Unit = {},
    // Memo parameters
    showMemoInOrderFormAndCreditBill: Boolean = false,
    memoText: String = "",
    onMemoTextChange: (String) -> Unit = {},
    // Delivery date parameters
    showDeliveryDateInOrderForm: Boolean = false,
    deliveryDateMillis: Long? = null,
    onDeliveryDateSelected: (Long?) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Customer & Sales Rep", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))

            AutoCompleteField(
                value = customerSearchQuery,
                onValueChange = onCustomerSearchChange,
                label = "Customer",
                suggestions = customers.filter {
                    customerSearchQuery.isEmpty() ||
                    it.name.contains(customerSearchQuery, ignoreCase = true)
                },
                onSuggestionSelected = { onCustomerSelected(it as CustomerOption) },
                displayText = { (it as CustomerOption).name }
            )

            // Address dropdown - show only after customer is selected, and if config enabled or multiple customers have same name
            if (selectedCustomer != null && (showCustomerAddressInOrderForm || customerAddresses.size > 1) && customerAddresses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                var addressExpanded by remember { mutableStateOf(false) }

                Column {
                    OutlinedTextField(
                        value = selectedAddress,
                        onValueChange = { },
                        label = { Text("Customer Address", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addressExpanded = !addressExpanded },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.White.copy(alpha = 0.5f),
                            disabledLabelColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = if (addressExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Expand",
                                tint = Color.White,
                                modifier = Modifier.clickable { addressExpanded = !addressExpanded }
                            )
                        }
                    )

                    if (addressExpanded) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn {
                                items(customerAddresses) { address ->
                                    Text(
                                        text = address.ifEmpty { "(No address)" },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onAddressSelected(address)
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

            // Payment options dropdown - show if config enabled
            if (showPaymentOptionsInOrderForm && paymentOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                var paymentExpanded by remember { mutableStateOf(false) }

                Column {
                    OutlinedTextField(
                        value = selectedPaymentOption?.label ?: "",
                        onValueChange = { },
                        label = { Text("Payment", color = Color.White) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { paymentExpanded = !paymentExpanded },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.White.copy(alpha = 0.5f),
                            disabledLabelColor = Color.White
                        ),
                        singleLine = true,
                        trailingIcon = {
                            Icon(
                                imageVector = if (paymentExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Expand",
                                tint = Color.White,
                                modifier = Modifier.clickable { paymentExpanded = !paymentExpanded }
                            )
                        }
                    )

                    if (paymentExpanded) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            LazyColumn {
                                items(paymentOptions) { payment ->
                                    Text(
                                        text = payment.label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onPaymentOptionSelected(payment)
                                                paymentExpanded = false
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
            if (showMemoInOrderFormAndCreditBill) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = memoText,
                    onValueChange = onMemoTextChange,
                    label = { Text("Memo", color = Color.White) },
                    placeholder = { Text("Enter memo", color = Color.White.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }

            // Delivery date - show if config enabled
            if (showDeliveryDateInOrderForm) {
                Spacer(modifier = Modifier.height(12.dp))
                var showDeliveryDatePicker by remember { mutableStateOf(false) }
                val deliveryDateText = deliveryDateMillis?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
                } ?: ""
                OutlinedTextField(
                    value = deliveryDateText,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Delivery Date", color = Color.White) },
                    placeholder = { Text("Select delivery date", color = Color.White.copy(alpha = 0.5f)) },
                    trailingIcon = {
                        IconButton(onClick = { showDeliveryDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select delivery date", tint = Color.White)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeliveryDatePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
                if (showDeliveryDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = deliveryDateMillis ?: System.currentTimeMillis()
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDeliveryDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                onDeliveryDateSelected(datePickerState.selectedDateMillis)
                                showDeliveryDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeliveryDatePicker = false }) { Text("Cancel") }
                        }
                    ) { DatePicker(state = datePickerState) }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AutoCompleteField(
                value = salesRepSearchQuery,
                onValueChange = onSalesRepSearchChange,
                label = "Sales Rep",
                suggestions = salesReps.filter {
                    salesRepSearchQuery.isEmpty() ||
                    it.name.contains(salesRepSearchQuery, ignoreCase = true)
                },
                onSuggestionSelected = { onSalesRepSelected(it as SalesRefOption) },
                displayText = { (it as SalesRefOption).name }
            )
        }
    }
}

// API Functions
private suspend fun fetchNextOrderNo(token: String): String {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/order/getNextOrderNo")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            content.trim().replace("\"", "")
        } else {
            Log.e("OrderForm", "Failed to fetch order no: ${connection.responseCode}")
            ""
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error fetching order no: ${e.message}")
        ""
    }
}

private suspend fun fetchOrderBillItems(token: String): List<OrderBillItem> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllBillItems?billType=ORDER")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
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

                    @Suppress("UNCHECKED_CAST")
                    val supplierList = (itemMap["billSupplierList"] as? List<Map<String, Any>>)?.map { supplierMap ->
                        val supplierId = (supplierMap["supplierId"] as? Number)?.toInt() ?: 0
                        val supplierName = supplierMap["supplierName"] as? String ?: ""

                        @Suppress("UNCHECKED_CAST")
                        val invoiceList = (supplierMap["billInvoiceList"] as? List<Map<String, Any>>)?.map { invoiceMap ->
                            BillInvoice(
                                invoiceId = (invoiceMap["invoiceId"] as? Number)?.toInt() ?: 0,
                                invoiceName = invoiceMap["invoiceName"] as? String ?: "",
                                qty = (invoiceMap["qty"] as? Number)?.toDouble() ?: 0.0,
                                sellingPrice = (invoiceMap["sellingPrice"] as? Number)?.toDouble() ?: sellingPrice,
                                unitCost = (invoiceMap["unitCost"] as? Number)?.toDouble() ?: 0.0,
                                reservedQty = (invoiceMap["reservedQty"] as? Number)?.toDouble() ?: 0.0
                            )
                        } ?: emptyList()

                        BillSupplier(
                            supplierId = supplierId,
                            supplierName = supplierName,
                            billInvoiceList = invoiceList
                        )
                    } ?: emptyList()

                    OrderBillItem(
                        id = itemId,
                        code = itemCode,
                        name = itemName,
                        sellingPrice = sellingPrice,
                        billSupplierList = supplierList
                    )
                } catch (e: Exception) {
                    Log.e("OrderForm", "Error parsing item: ${e.message}")
                    null
                }
            }
        } else {
            Log.e("OrderForm", "Failed to fetch items: ${connection.responseCode}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error fetching items: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchCustomersList(token: String): List<CustomerOption> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()

            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>

            list?.mapNotNull { customerMap ->
                val id = (customerMap["id"] as? Number)?.toLong() ?: return@mapNotNull null
                val name = customerMap["name"] as? String ?: return@mapNotNull null
                val address = customerMap["address"] as? String ?: ""
                val telephone = customerMap["telephone"] as? String ?: ""

                CustomerOption(
                    id = id,
                    name = name,
                    address = address,
                    telephone = telephone
                )
            } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error fetching customers: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchSalesRepsList(token: String): List<SalesRefOption> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/salesRef/getAllSalesRef")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()

            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>

            list?.mapNotNull { repMap ->
                val id = (repMap["id"] as? Number)?.toLong() ?: return@mapNotNull null
                val name = repMap["name"] as? String ?: return@mapNotNull null

                SalesRefOption(id = id, name = name)
            } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error fetching sales reps: ${e.message}")
        emptyList()
    }
}

private suspend fun saveOrder(
    context: Context,
    token: String,
    orderNo: String,
    orderItems: List<OrderItem>,
    customerId: Long,
    customerName: String,
    salesRefId: Long,
    salesRefName: String,
    discountPercentage: Double,
    discountValue: Double,
    grossTotal: Double,
    netTotal: Double,
    paymentOption: String = "CASH",
    memo: String = "",
    deliveryDate: Long? = null
): String {
    return try {
        // Build the order object
        val orderObject = JsonObject().apply {
            val itemsArray = JsonArray()
            orderItems.forEach { item ->
                val itemObj = JsonObject().apply {
                    addProperty("itemCode", item.itemCode)
                    addProperty("itemName", item.itemName)
                    addProperty("invoiceId", item.invoiceId)
                    addProperty("supplierId", item.supplierId)
                    addProperty("itemId", item.itemId)
                    addProperty("qty", item.qty.toInt().toString())
                    addProperty("unitPrice", item.unitPrice)
                    addProperty("discount", item.discountValue)
                    addProperty("discountValue", item.discountValue)
                    addProperty("discountPercentage", item.discountPercentage)
                    addProperty("totalPrice", item.totalPrice)
                    addProperty("totalQty", item.totalQty.toInt())
                    addProperty("freeIssueQty", item.freeIssueQty.toInt())
                    addProperty("isPromoFreeItem", item.isPromoFreeItem)
                    addProperty("promotionId", item.promotionId)
                }
                itemsArray.add(itemObj)
            }
            add("orderItems", itemsArray)
            addProperty("orderNo", orderNo.toIntOrNull() ?: 0)
            addProperty("salesRefId", salesRefId)
            addProperty("orderDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()))
            addProperty("customerId", customerId)
            // Pass customerName when customerId is 0 (for new customer creation)
            if (customerId <= 0 && customerName.isNotEmpty()) {
                addProperty("customerName", customerName)
            }
            addProperty("discountValue", discountValue)
            addProperty("discountPercentage", discountPercentage)
            addProperty("paymentOption", paymentOption)
            addProperty("memo", memo)
            if (deliveryDate != null) {
                addProperty("deliveryDate", deliveryDate)
            }
        }

        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        // Build the full payload with userEvent, order, and creditCustomerList
        val payload = JsonObject().apply {
            // User event for audit
            add("userEvent", JsonObject().apply {
                addProperty("event", "Save Order, order no : [$orderNo]")
                addProperty("latitude", location.latitude.toString())
                addProperty("longitude", location.longitude.toString())
                addProperty("userName", "")
                addProperty("eventTime", "")
                add("mapLocations", null)
            })
            add("order", orderObject)
            add("creditCustomerList", JsonArray())
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/order")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = payload.toString()
        Log.d("OrderForm", "Saving order: $jsonString")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("OrderForm", "Order saved successfully: $responseBody")

            // Parse notification result from response
            var notificationMessage = ""
            try {
                val gson = Gson()
                val responseJson = gson.fromJson(responseBody, JsonObject::class.java)
                val notificationResult = responseJson.getAsJsonObject("notificationResult")
                if (notificationResult != null) {
                    val sent = notificationResult.get("sent")?.asBoolean ?: false
                    val channel = notificationResult.get("channel")?.asString
                    val recipient = notificationResult.get("recipient")?.asString

                    if (sent && channel != null) {
                        notificationMessage = when (channel) {
                            "WHATSAPP" -> "\nðŸ“± WhatsApp sent to $recipient"
                            "SMS" -> "\nðŸ“± SMS sent to $recipient"
                            else -> ""
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OrderForm", "Error parsing notification result: ${e.message}")
            }

            "Success: Order $orderNo created successfully$notificationMessage"
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("OrderForm", "Failed to save order: ${connection.responseCode} - $errorResponse")
            "Failed to save order: $errorResponse"
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Exception saving order: ${e.message}")
        "Error: ${e.message}"
    }
}

private suspend fun fetchOrderFormConfig(token: String): com.pos.cosmos.models.Config? {
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
            Log.e("OrderForm", "Failed to fetch config: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error fetching config: ${e.message}")
        null
    }
}

// Note: Bill template is fetched and cached by PrinterScreen only
// This screen uses BillTemplateCache.getTemplate() to get the cached template

private fun buildOrderReceiptData(
    orderItems: List<OrderItem>,
    customer: String,
    salesRep: String,
    discount: Double,
    grossTotal: Double,
    netTotal: Double,
    orderNo: String,
    billTemplate: JsonObject
): ReceiptData {
    val companyName = billTemplate.get("companyName")?.asString ?: "COSMOS POS"
    val companyNameFont = billTemplate.get("companyFont")?.asInt ?: 14
    val companyGap = billTemplate.get("companyGap")?.asInt ?: 0

    val address = billTemplate.get("address")?.asString ?: ""
    val addressFont = billTemplate.get("addressFont")?.asInt ?: 12
    val addressGap = billTemplate.get("addressGap")?.asInt ?: 0

    val contact = billTemplate.get("contact")?.asString ?: ""
    val contactFont = billTemplate.get("contactFont")?.asInt ?: 12
    val contactGap = billTemplate.get("contactGap")?.asInt ?: 0

    val email = billTemplate.get("email")?.asString ?: ""
    val emailFont = billTemplate.get("emailFont")?.asInt ?: 12
    val emailGap = billTemplate.get("emailGap")?.asInt ?: 0

    val lastLine = billTemplate.get("lastLine")?.asString ?: ""
    val lastLineFont = billTemplate.get("lastLineFont")?.asInt ?: 10
    val lastLineGap = billTemplate.get("lastLineGap")?.asInt ?: 0

    val upperGap = billTemplate.get("upperGap")?.asInt ?: 0
    val leftGap = billTemplate.get("leftGap")?.asInt ?: 0

    val logoDataUrl = billTemplate.get("logoDataURL")?.asString ?: ""
    val logoLeft = billTemplate.get("logoLeft")?.asInt ?: 0
    val logoUpper = billTemplate.get("logoUpper")?.asInt ?: 0
    val logoWidth = billTemplate.get("logoWidth")?.asInt ?: 0
    val logoHeight = billTemplate.get("logoHeight")?.asInt ?: 0

    // Build items list including free issue as separate items
    val receiptItems = mutableListOf<ReceiptItem>()
    for (item in orderItems) {
        // Add main item (promotion free lines print as their own 0.00 rows)
        receiptItems.add(ReceiptItem(
            name = if (item.isPromoFreeItem) "${item.itemName} (FREE)" else item.itemName,
            quantity = item.qty.toInt(),
            price = item.unitPrice,
            amount = item.totalPrice
        ))
        // Add free issue as separate item if freeIssueQty > 0
        if (item.freeIssueQty > 0) {
            receiptItems.add(ReceiptItem(
                name = "${item.itemName} (Free)",
                quantity = item.freeIssueQty.toInt(),
                price = 0.0,
                amount = 0.0
            ))
        }
    }

    return ReceiptData(
        items = receiptItems,
        customer = customer.ifEmpty { "Walk-in" },
        salesRep = salesRep.ifEmpty { "N/A" },
        grossTotal = grossTotal,
        discount = discount,
        netTotal = netTotal,
        cashPaid = 0.0,
        billNo = orderNo,
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
        isOrder = true
    )
}

// Data class for loaded order
data class LoadedOrderResult(
    val id: Long,
    val orderNo: Int,
    val customerId: Long,
    val salesRefId: Long,
    val discountPercentage: Double,
    val discountValue: Double,
    val isAccepted: Boolean,
    val deliveryDate: Long? = null,
    val items: List<OrderItem>
)

// Result class for createBillByAcceptedOrder
data class CreateBillResult(
    val success: Boolean,
    val message: String,
    val billNo: Int? = null
)

private suspend fun loadOrderForm(token: String, orderNo: Int): LoadedOrderResult? {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/order/loadOrderForm?orderId=$orderNo")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("OrderForm", "Load order response: $content")
            val gson = Gson()
            val response = gson.fromJson(content, JsonObject::class.java)

            val order = response.getAsJsonObject("order")
            if (order != null) {
                val id = order.get("id")?.asLong ?: 0L
                val customerId = order.get("customerId")?.asLong ?: 0L
                val salesRefId = order.get("salesRefId")?.asLong ?: 0L
                val discountPercentage = order.get("discountPercentage")?.asDouble ?: 0.0
                val discountValue = order.get("discountValue")?.asDouble ?: 0.0
                val status = order.get("status")?.asString ?: ""
                val isAccepted = status.equals("ACCEPTED", ignoreCase = true)
                val deliveryDate = try {
                    order.get("deliveryDate")?.takeIf { !it.isJsonNull }?.asLong
                } catch (e: Exception) { null }

                // Parse order items
                val orderItemsArray = order.getAsJsonArray("orderItems")
                val items = mutableListOf<OrderItem>()

                if (orderItemsArray != null) {
                    for (i in 0 until orderItemsArray.size()) {
                        val itemJson = orderItemsArray.get(i).asJsonObject
                        val itemId = itemJson.get("itemId")?.asInt ?: 0
                        // Try multiple field names for itemName
                        var itemName = itemJson.get("itemName")?.asString
                            ?: itemJson.get("name")?.asString
                            ?: ""
                        // Try multiple field names for itemCode
                        var itemCode = itemJson.get("itemCode")?.asString
                            ?: itemJson.get("code")?.asString
                            ?: ""

                        // Log what we got from API
                        Log.d("OrderForm", "Item from API - id: $itemId, code: '$itemCode', name: '$itemName'")

                        val item = OrderItem(
                            itemId = itemId,
                            itemCode = itemCode,
                            itemName = itemName,
                            supplierId = itemJson.get("supplierId")?.asInt ?: 0,
                            invoiceId = itemJson.get("invoiceId")?.asInt ?: 0,
                            qty = itemJson.get("qty")?.asString?.toDoubleOrNull()
                                ?: itemJson.get("qty")?.asDouble
                                ?: 0.0,
                            unitPrice = itemJson.get("unitPrice")?.asDouble ?: 0.0,
                            discountPercentage = itemJson.get("discountPercentage")?.asDouble ?: 0.0,
                            discountValue = itemJson.get("discountValue")?.asDouble ?: 0.0,
                            totalQty = itemJson.get("totalQty")?.asDouble ?: 0.0,
                            freeIssueQty = itemJson.get("freeIssueQty")?.asDouble ?: 0.0,
                            isPromoFreeItem = try { itemJson.get("isPromoFreeItem")?.asBoolean ?: false } catch (e: Exception) { false },
                            promotionId = try { itemJson.get("promotionId")?.asInt ?: 0 } catch (e: Exception) { 0 }
                        )
                        items.add(item)
                    }
                }

                LoadedOrderResult(
                    id = id,
                    orderNo = orderNo,
                    customerId = customerId,
                    salesRefId = salesRefId,
                    discountPercentage = discountPercentage,
                    discountValue = discountValue,
                    isAccepted = isAccepted,
                    deliveryDate = deliveryDate,
                    items = items
                )
            } else {
                null
            }
        } else {
            Log.e("OrderForm", "Failed to load order: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Error loading order: ${e.message}")
        null
    }
}

private suspend fun updateOrder(
    context: Context,
    token: String,
    orderId: Long,
    orderNo: String,
    orderItems: List<OrderItem>,
    customerId: Long,
    customerName: String,
    salesRefId: Long,
    salesRefName: String,
    discountPercentage: Double,
    discountValue: Double,
    grossTotal: Double,
    netTotal: Double,
    paymentOption: String = "CASH",
    memo: String = "",
    deliveryDate: Long? = null
): String {
    return try {
        // Build the order object
        val orderObject = JsonObject().apply {
            val itemsArray = JsonArray()
            orderItems.forEach { item ->
                val itemObj = JsonObject().apply {
                    addProperty("itemCode", item.itemCode)
                    addProperty("itemName", item.itemName)
                    addProperty("invoiceId", item.invoiceId)
                    addProperty("supplierId", item.supplierId)
                    addProperty("itemId", item.itemId)
                    addProperty("qty", item.qty.toInt().toString())
                    addProperty("unitPrice", item.unitPrice)
                    addProperty("discount", item.discountValue)
                    addProperty("discountValue", item.discountValue)
                    addProperty("discountPercentage", item.discountPercentage)
                    addProperty("totalPrice", item.totalPrice)
                    addProperty("totalQty", item.totalQty.toInt())
                    addProperty("freeIssueQty", item.freeIssueQty.toInt())
                    addProperty("isPromoFreeItem", item.isPromoFreeItem)
                    addProperty("promotionId", item.promotionId)
                }
                itemsArray.add(itemObj)
            }
            add("orderItems", itemsArray)
            addProperty("id", orderId)
            addProperty("orderNo", orderNo.toIntOrNull() ?: 0)
            addProperty("salesRefId", salesRefId)
            addProperty("orderDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()))
            addProperty("customerId", customerId)
            if (customerId <= 0 && customerName.isNotEmpty()) {
                addProperty("customerName", customerName)
            }
            addProperty("discountValue", discountValue)
            addProperty("discountPercentage", discountPercentage)
            addProperty("paymentOption", paymentOption)
            addProperty("memo", memo)
            if (deliveryDate != null) {
                addProperty("deliveryDate", deliveryDate)
            }
        }

        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        // Build the full payload
        val payload = JsonObject().apply {
            add("userEvent", JsonObject().apply {
                addProperty("event", "Update Order, order no : [$orderNo]")
                addProperty("latitude", location.latitude.toString())
                addProperty("longitude", location.longitude.toString())
                addProperty("userName", "")
                addProperty("eventTime", "")
                add("mapLocations", null)
            })
            add("order", orderObject)
            add("creditCustomerList", JsonArray())
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/order")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = payload.toString()
        Log.d("OrderForm", "Updating order: $jsonString")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("OrderForm", "Order updated successfully: $responseBody")
            "Success: Order $orderNo updated successfully"
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("OrderForm", "Failed to update order: ${connection.responseCode} - $errorResponse")
            "Failed to update order: $errorResponse"
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Exception updating order: ${e.message}")
        "Error: ${e.message}"
    }
}

private suspend fun createBillByAcceptedOrder(
    context: Context,
    token: String,
    orderId: Long,
    orderNo: String,
    orderItems: List<OrderItem>,
    customerId: Long,
    salesRefId: Long,
    discountPercentage: Double,
    discountValue: Double
): CreateBillResult {
    return try {
        // Build the order object
        val orderObject = JsonObject().apply {
            val itemsArray = JsonArray()
            orderItems.forEach { item ->
                val itemObj = JsonObject().apply {
                    addProperty("itemCode", item.itemCode)
                    addProperty("itemName", item.itemName)
                    addProperty("invoiceId", item.invoiceId)
                    addProperty("supplierId", item.supplierId)
                    addProperty("itemId", item.itemId)
                    addProperty("qty", item.qty.toInt().toString())
                    addProperty("unitPrice", item.unitPrice)
                    addProperty("discount", item.discountValue)
                    addProperty("discountValue", item.discountValue)
                    addProperty("discountPercentage", item.discountPercentage)
                    addProperty("totalPrice", item.totalPrice)
                    addProperty("totalQty", item.totalQty.toInt())
                    addProperty("freeIssueQty", item.freeIssueQty.toInt())
                    addProperty("isPromoFreeItem", item.isPromoFreeItem)
                    addProperty("promotionId", item.promotionId)
                }
                itemsArray.add(itemObj)
            }
            add("orderItems", itemsArray)
            addProperty("id", orderId)
            addProperty("orderNo", orderNo.toIntOrNull() ?: 0)
            addProperty("salesRefId", salesRefId)
            addProperty("orderDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()))
            addProperty("customerId", customerId)
            addProperty("discountValue", discountValue)
            addProperty("discountPercentage", discountPercentage)
        }

        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        // Build the full payload
        val payload = JsonObject().apply {
            add("userEvent", JsonObject().apply {
                addProperty("event", "Create Bill from Accepted Order, order no : [$orderNo]")
                addProperty("latitude", location.latitude.toString())
                addProperty("longitude", location.longitude.toString())
                addProperty("userName", "")
                addProperty("eventTime", "")
                add("mapLocations", null)
            })
            add("order", orderObject)
            add("creditCustomerList", JsonArray())
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/order/createBillByAcceptedOrder")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = payload.toString()
        Log.d("OrderForm", "Creating bill from accepted order: $jsonString")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("OrderForm", "Bill created successfully: $responseBody")
            // Parse the response to get the bill number
            val gson = Gson()
            val response = gson.fromJson(responseBody, JsonObject::class.java)
            val billNo = response.get("billNo")?.asInt
            CreateBillResult(
                success = true,
                message = "Bill #$billNo created from Order #$orderNo",
                billNo = billNo
            )
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("OrderForm", "Failed to create bill: ${connection.responseCode} - $errorResponse")
            CreateBillResult(
                success = false,
                message = "Failed to create bill: $errorResponse"
            )
        }
    } catch (e: Exception) {
        Log.e("OrderForm", "Exception creating bill: ${e.message}")
        CreateBillResult(
            success = false,
            message = "Error: ${e.message}"
        )
    }
}

/**
 * Print bill from order data
 */
private fun printBillFromOrder(
    context: Context,
    billNo: Int,
    orderItems: List<OrderItem>,
    customerName: String,
    salesRepName: String,
    grossTotal: Double,
    discount: Double,
    netTotal: Double
): Boolean {
    return try {
        Log.d("OrderForm", "Starting print for Bill #$billNo")

        val printerService = PrinterService.getInstance()

        // Check if printer is connected, if not try to connect
        if (!printerService.isConnected()) {
            Log.d("OrderForm", "Printer not connected, attempting to connect...")

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.w("OrderForm", "Bluetooth is not enabled")
                return false
            }

            // Try to connect to first paired printer
            val pairedDevices = bluetoothAdapter.bondedDevices
            val printerDevice = pairedDevices?.find { device ->
                device.name?.contains("printer", ignoreCase = true) == true ||
                device.name?.contains("POS", ignoreCase = true) == true ||
                device.name?.contains("thermal", ignoreCase = true) == true ||
                device.bluetoothClass?.majorDeviceClass == 1536 // Imaging device class
            } ?: pairedDevices?.firstOrNull()

            if (printerDevice == null) {
                Log.w("OrderForm", "No paired printer found")
                return false
            }

            Log.d("OrderForm", "Connecting to printer: ${printerDevice.name}")
            val connected = printerService.connectDevice(printerDevice)
            if (!connected) {
                Log.w("OrderForm", "Failed to connect to printer: ${printerDevice.name}")
                return false
            }
        }

        // Get bill template from cache
        val template = BillTemplateCache.getTemplate(context)
        if (template == null) {
            Log.w("OrderForm", "Bill template not available")
            return false
        }

        val pdfGenerator = PdfReceiptGenerator(context)
        val receiptData = ReceiptData(
            items = orderItems.map { item ->
                ReceiptItem(
                    name = item.itemName,
                    quantity = item.qty.toInt(),
                    price = item.unitPrice,
                    amount = item.totalPrice
                )
            },
            customer = customerName,
            salesRep = salesRepName,
            grossTotal = grossTotal,
            discount = discount,
            netTotal = netTotal,
            billNo = billNo.toString(),
            companyName = template.get("companyName")?.asString ?: "COSMOS POS",
            address = template.get("address")?.asString ?: "",
            contact = template.get("contact")?.asString ?: "",
            email = template.get("email")?.asString ?: "",
            companyNameFont = template.get("companyFont")?.asInt ?: 14,
            addressFont = template.get("addressFont")?.asInt ?: 12,
            contactFont = template.get("contactFont")?.asInt ?: 12,
            emailFont = template.get("emailFont")?.asInt ?: 12,
            lastLineFont = template.get("lastLineFont")?.asInt ?: 10,
            companyGap = template.get("companyGap")?.asInt ?: 0,
            addressGap = template.get("addressGap")?.asInt ?: 0,
            contactGap = template.get("contactGap")?.asInt ?: 0,
            emailGap = template.get("emailGap")?.asInt ?: 0,
            lastLineGap = template.get("lastLineGap")?.asInt ?: 0,
            upperGap = template.get("upperGap")?.asInt ?: 0,
            leftGap = template.get("leftGap")?.asInt ?: 0,
            logoDataUrl = template.get("logoDataURL")?.asString ?: "",
            logoLeft = template.get("logoLeft")?.asInt ?: 0,
            logoUpper = template.get("logoUpper")?.asInt ?: 0,
            logoWidth = template.get("logoWidth")?.asInt ?: 0,
            logoHeight = template.get("logoHeight")?.asInt ?: 0,
            lastLine = template.get("lastLine")?.asString ?: "",
            isOrder = false
        )

        val printSuccess = printerService.printPdfReceipt(pdfGenerator, receiptData)
        if (printSuccess) {
            Log.d("OrderForm", "Bill #$billNo printed successfully")
        } else {
            Log.e("OrderForm", "Print failed for Bill #$billNo")
        }
        printSuccess
    } catch (e: Exception) {
        Log.e("OrderForm", "Error printing bill: ${e.message}")
        false
    }
}

/**
 * Print order as TRUE COPY
 */
private fun printOrderTrueCopy(
    context: Context,
    orderNo: String,
    orderItems: List<OrderItem>,
    customerName: String,
    salesRepName: String,
    grossTotal: Double,
    discount: Double,
    netTotal: Double
): Boolean {
    return try {
        Log.d("OrderForm", "Starting print for Order #$orderNo TRUE COPY")

        val printerService = PrinterService.getInstance()

        // Check if printer is connected, if not try to connect
        if (!printerService.isConnected()) {
            Log.d("OrderForm", "Printer not connected, attempting to connect...")

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                Log.w("OrderForm", "Bluetooth is not enabled")
                return false
            }

            // Try to connect to first paired printer
            val pairedDevices = bluetoothAdapter.bondedDevices
            val printerDevice = pairedDevices?.find { device ->
                device.name?.contains("printer", ignoreCase = true) == true ||
                device.name?.contains("POS", ignoreCase = true) == true ||
                device.name?.contains("thermal", ignoreCase = true) == true ||
                device.bluetoothClass?.majorDeviceClass == 1536 // Imaging device class
            } ?: pairedDevices?.firstOrNull()

            if (printerDevice == null) {
                Log.w("OrderForm", "No paired printer found")
                return false
            }

            Log.d("OrderForm", "Connecting to printer: ${printerDevice.name}")
            val connected = printerService.connectDevice(printerDevice)
            if (!connected) {
                Log.w("OrderForm", "Failed to connect to printer: ${printerDevice.name}")
                return false
            }
        }

        // Get bill template from cache
        val template = BillTemplateCache.getTemplate(context)
        if (template == null) {
            Log.w("OrderForm", "Bill template not available")
            return false
        }

        val pdfGenerator = PdfReceiptGenerator(context)

        // Build items list including free issue as separate items
        val receiptItems = mutableListOf<ReceiptItem>()
        for (item in orderItems) {
            // Add main item
            receiptItems.add(ReceiptItem(
                name = item.itemName,
                quantity = item.qty.toInt(),
                price = item.unitPrice,
                amount = item.totalPrice
            ))
            // Add free issue as separate item if freeIssueQty > 0
            if (item.freeIssueQty > 0) {
                receiptItems.add(ReceiptItem(
                    name = "${item.itemName} (Free)",
                    quantity = item.freeIssueQty.toInt(),
                    price = 0.0,
                    amount = 0.0
                ))
            }
        }

        // Use template's lastLine
        val templateLastLine = template.get("lastLine")?.asString ?: ""

        val receiptData = ReceiptData(
            items = receiptItems,
            customer = customerName,
            salesRep = salesRepName,
            grossTotal = grossTotal,
            discount = discount,
            netTotal = netTotal,
            billNo = "ORD-$orderNo",
            companyName = template.get("companyName")?.asString ?: "COSMOS POS",
            address = template.get("address")?.asString ?: "",
            contact = template.get("contact")?.asString ?: "",
            email = template.get("email")?.asString ?: "",
            companyNameFont = template.get("companyFont")?.asInt ?: 14,
            addressFont = template.get("addressFont")?.asInt ?: 12,
            contactFont = template.get("contactFont")?.asInt ?: 12,
            emailFont = template.get("emailFont")?.asInt ?: 12,
            lastLineFont = template.get("lastLineFont")?.asInt ?: 10,
            companyGap = template.get("companyGap")?.asInt ?: 0,
            addressGap = template.get("addressGap")?.asInt ?: 0,
            contactGap = template.get("contactGap")?.asInt ?: 0,
            emailGap = template.get("emailGap")?.asInt ?: 0,
            lastLineGap = template.get("lastLineGap")?.asInt ?: 0,
            upperGap = template.get("upperGap")?.asInt ?: 0,
            leftGap = template.get("leftGap")?.asInt ?: 0,
            logoDataUrl = template.get("logoDataURL")?.asString ?: "",
            logoLeft = template.get("logoLeft")?.asInt ?: 0,
            logoUpper = template.get("logoUpper")?.asInt ?: 0,
            logoWidth = template.get("logoWidth")?.asInt ?: 0,
            logoHeight = template.get("logoHeight")?.asInt ?: 0,
            lastLine = templateLastLine,
            isOrder = true
        )

        val printSuccess = printerService.printPdfReceipt(pdfGenerator, receiptData)
        if (printSuccess) {
            Log.d("OrderForm", "Order #$orderNo TRUE COPY printed successfully")
        } else {
            Log.e("OrderForm", "Print failed for Order #$orderNo TRUE COPY")
        }
        printSuccess
    } catch (e: Exception) {
        Log.e("OrderForm", "Error printing order TRUE COPY: ${e.message}")
        false
    }
}
