package com.pos.cosmos.screens

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.*
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.utils.formatQty
import java.io.File

// ==================== Data Classes ====================

private data class SupplierInvoice(
    val supplierId: Int,
    val supplierName: String,
    val invoiceId: Int,
    val invoiceName: String,
    val qty: Double,
    val sellingPrice: Double,
    val invoiceDate: String = ""
)

private data class ImageBillItem(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    var quantity: Double = 1.0,
    val unitPrice: Double = sellingPrice,
    val discountValue: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val invoiceId: Int = 0,
    val supplierId: Int = 0,
    val supplierName: String = "",
    val invoiceName: String = "",
    val totalQty: Double = 0.0,
    val returnQty: Double = 0.0,
    val image: String = "",
    val categoryId: Int = 0,
    val supplierInvoices: List<SupplierInvoice> = emptyList()
) {
    // For a pure return (quantity=0, returnQty>0) the bill should net to 0 — the items
    // move to damages but no money changes hands. Using effective qty = max(quantity, returnQty)
    // makes the amount 0 in that case, and behaves normally otherwise.
    val effectiveQty: Double
        get() = maxOf(quantity, returnQty)

    val amount: Double
        get() = (effectiveQty - returnQty) * (unitPrice - discountValue)
}

private data class DistributionLine(
    val supplierId: Int,
    val supplierName: String,
    val invoiceId: Int,
    val invoiceName: String,
    val newQty: Double,
    val invoiceQty: Double,
    val sellingPrice: Double
)

private fun distributeQty(
    qty: Double,
    sortedInvoices: List<SupplierInvoice>,
    allowExceeded: Boolean
): List<DistributionLine> {
    val result = mutableListOf<DistributionLine>()
    var remaining = qty
    for (inv in sortedInvoices) {
        if (remaining <= 0.0) break
        if (inv.qty <= 0.0) continue
        val take = if (inv.qty < remaining) inv.qty else remaining
        result.add(DistributionLine(inv.supplierId, inv.supplierName, inv.invoiceId, inv.invoiceName, take, inv.qty, inv.sellingPrice))
        remaining -= take
    }
    if (remaining > 0.0 && allowExceeded && sortedInvoices.isNotEmpty()) {
        val latest = sortedInvoices.maxByOrNull { it.invoiceDate } ?: sortedInvoices.last()
        val existingIdx = result.indexOfFirst { it.invoiceId == latest.invoiceId && it.supplierId == latest.supplierId }
        if (existingIdx >= 0) {
            result[existingIdx] = result[existingIdx].copy(newQty = result[existingIdx].newQty + remaining)
        } else {
            result.add(DistributionLine(latest.supplierId, latest.supplierName, latest.invoiceId, latest.invoiceName, remaining, latest.qty, latest.sellingPrice))
        }
    }
    return result
}

private data class ImageBillCategory(
    val id: Int,
    val name: String,
    val image: String = ""
)

private data class ImageBillCustomer(
    val id: Int,
    val name: String,
    val telephone: String = "",
    val whatsappNo: String = "",
    val address: String = ""
)

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageBillScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Data state
    var allItems by remember { mutableStateOf<List<ImageBillItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<ImageBillCategory>>(emptyList()) }
    var customers by remember { mutableStateOf<List<ImageBillCustomer>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Bill template & printer state
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }
    var paperWidthMm by remember { mutableStateOf(80f) }

    // Bill state - restore from ViewModel for persistence across navigation
    var billItems by remember { mutableStateOf<List<ImageBillItem>>(
        appViewModel.imageBillCart.value.map { m ->
            ImageBillItem(
                id = (m["id"] as? Number)?.toInt() ?: 0,
                code = m["code"] as? String ?: "",
                name = m["name"] as? String ?: "",
                sellingPrice = (m["sellingPrice"] as? Number)?.toDouble() ?: 0.0,
                quantity = (m["quantity"] as? Number)?.toDouble() ?: 1.0,
                unitPrice = (m["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                discountValue = (m["discountValue"] as? Number)?.toDouble() ?: 0.0,
                discountPercentage = (m["discountPercentage"] as? Number)?.toDouble() ?: 0.0,
                returnQty = (m["returnQty"] as? Number)?.toDouble() ?: 0.0,
                invoiceId = (m["invoiceId"] as? Number)?.toInt() ?: 0,
                supplierId = (m["supplierId"] as? Number)?.toInt() ?: 0,
                supplierName = m["supplierName"] as? String ?: "",
                invoiceName = m["invoiceName"] as? String ?: "",
                totalQty = (m["totalQty"] as? Number)?.toDouble() ?: 0.0,
                image = m["image"] as? String ?: "",
                categoryId = (m["categoryId"] as? Number)?.toInt() ?: 0
            )
        }
    ) }
    var discountPercentage by remember { mutableStateOf(appViewModel.imageBillDiscount.value) }
    var discountValue by remember { mutableStateOf(appViewModel.imageBillDiscountValue.value) }
    var cashPaid by remember { mutableStateOf(appViewModel.imageBillCashPaid.value) }

    // Customer state - restore from ViewModel
    val savedCustomer = appViewModel.imageBillCustomer.value
    var customerQuery by remember { mutableStateOf(savedCustomer?.get("name") as? String ?: "") }
    var selectedCustomer by remember { mutableStateOf(
        if (savedCustomer != null) ImageBillCustomer(
            id = (savedCustomer["id"] as? Number)?.toInt() ?: 0,
            name = savedCustomer["name"] as? String ?: "",
            telephone = savedCustomer["telephone"] as? String ?: "",
            whatsappNo = savedCustomer["whatsappNo"] as? String ?: "",
            address = savedCustomer["address"] as? String ?: ""
        ) else null
    ) }
    var whatsAppNo by remember { mutableStateOf(savedCustomer?.get("whatsappNo") as? String ?: "") }
    var showCustomerSuggestions by remember { mutableStateOf(false) }

    // UI state
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var lastReceiptPdf by remember { mutableStateOf<File?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var showCartSheet by remember { mutableStateOf(false) }
    var showLocationWarning by remember { mutableStateOf(false) }
    var skipLocationWarning by remember { mutableStateOf(false) }
    var showPrinterWarning by remember { mutableStateOf(false) }
    var skipPrinterWarning by remember { mutableStateOf(false) }

    // Location permission
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var pendingSaveBill by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!locationPermissionGranted && pendingSaveBill) {
            errorMessage = "Location permission denied. Bill will be saved without location data. Please grant location permission in Settings for accurate tracking."
            showErrorDialog = true
        }
        pendingSaveBill = false
    }
    var showItemDialog by remember { mutableStateOf(false) }
    var dialogItem by remember { mutableStateOf<ImageBillItem?>(null) }
    var dialogPrice by remember { mutableStateOf("") }
    var dialogQty by remember { mutableStateOf("") }
    var dialogReturnQty by remember { mutableStateOf("") }

    // Edit item dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ImageBillItem?>(null) }
    var editQty by remember { mutableStateOf("") }
    var editPrice by remember { mutableStateOf("") }
    var editDiscPct by remember { mutableStateOf("") }
    var editDiscVal by remember { mutableStateOf("") }
    var editSupplierId by remember { mutableStateOf(0) }
    var editSupplierName by remember { mutableStateOf("") }
    var editInvoiceId by remember { mutableStateOf(0) }
    var editInvoiceName by remember { mutableStateOf("") }
    var editTotalQty by remember { mutableStateOf(0.0) }

    // Computed values
    //
    // grossTotal uses item.amount (effectiveQty-based) — this is what shows in the cart UI
    // and represents sale value. trueNetTotal uses the receipt-style formula
    // (quantity*price - returnQty*price - discount) so it can go *negative* when the
    // return value exceeds the sale value. That negative is what we send to the
    // backend (so the WhatsApp message reflects reality) and what triggers the
    // owner→customer refund payment + 2-copy receipt print.
    val grossTotal = billItems.sumOf { it.amount }
    val discPct = discountPercentage.toDoubleOrNull() ?: 0.0
    val discVal = discountValue.toDoubleOrNull() ?: 0.0
    val totalDiscount = if (discPct > 0) grossTotal * discPct / 100.0 else discVal
    val saleTotal = billItems.sumOf { it.quantity * it.unitPrice }
    val returnTotal = billItems.sumOf { it.returnQty * it.unitPrice }
    val trueNetTotal = saleTotal - returnTotal - totalDiscount
    val netTotal = trueNetTotal.coerceAtLeast(0.0)
    val isRefund = trueNetTotal < 0.0
    val refundAmount = if (isRefund) -trueNetTotal else 0.0
    val cash = cashPaid.toDoubleOrNull() ?: 0.0
    val balance = cash - netTotal
    val totalItems = billItems.sumOf { it.quantity } + billItems.sumOf { it.returnQty }
    val hasCustomer = selectedCustomer != null || (customerQuery.isNotBlank())
    val isCreditBill = hasCustomer && (cash < netTotal)

    val showCustomerField = config?.showCustomerNameInCashBill == "Y"
    val showWhatsAppField = config?.showWhatsAppNoInCashBills == "Y"
    val showReturnQtyField = config?.showReturnQtyItemWiseInImageBillsAndroidApp == "Y"
    // Price editable only when one of the selling-price configs allows it (mirrors web image bill)
    val canEditSellingPrice = config?.showSellingPriceInImageBill == "Y" ||
        config?.editSellingPriceWhenBilling == "Y" ||
        config?.editSellingPriceUpwardWhenBilling == "Y"

    // Filtered items
    val filteredItems = remember(selectedCategoryId, searchQuery, allItems) {
        when {
            searchQuery.isNotBlank() -> allItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.code.contains(searchQuery, ignoreCase = true)
            }
            selectedCategoryId != null && selectedCategoryId != 0 ->
                allItems.filter { it.categoryId == selectedCategoryId }
            else -> allItems
        }
    }

    val filteredCustomers = remember(customerQuery, customers) {
        if (customerQuery.isBlank()) emptyList()
        else customers.filter { it.name.contains(customerQuery, ignoreCase = true) }.take(10)
    }

    // Request location permission on screen load
    LaunchedEffect(Unit) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Load data
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    // Always fetch fresh config
                    val fetchedConfig = fetchImageBillConfig(context, token!!)
                    if (fetchedConfig != null) {
                        appViewModel.setConfig(fetchedConfig)
                    }

                    categories = fetchImageBillCategories(context, token!!)
                    allItems = fetchImageBillItems(context, token!!)
                    customers = fetchImageBillCustomers(context, token!!)

                    // Paper width comes from config. The receipt template (logo + details) is
                    // loaded separately in the background (see the template-preload
                    // LaunchedEffect below) so the network fetch never blocks this initial
                    // page load.
                    val use58mm = appViewModel.config.value?.use58mmBillTemplateForAndroidApp == "Y"
                    paperWidthMm = if (use58mm) 58f else 80f
                }
                loading = false
            }
        }
    }

    // Preload the receipt/bill template in the background as soon as the page opens, so it
    // is ready by the time the user saves a bill. This runs independently of the `loading`
    // block above, so it never delays the initial render. Network-first (to keep the logo
    // and company details fresh) with a cache fallback.
    LaunchedEffect(token, config) {
        val t = token ?: return@LaunchedEffect
        if (billTemplate != null) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
            val billType = if (use58mm) "android58" else "android"
            val loaded = loadImageBillTemplate(context, t, billType)
            if (loaded != null && billTemplate == null) {
                billTemplate = loaded
                Log.d("ImageBillScreen", "Bill template preloaded (type=$billType)")
            } else if (loaded == null) {
                Log.w("ImageBillScreen", "Bill template preload failed (type=$billType)")
            }
        }
    }

    // After permission result, update state
    LaunchedEffect(locationPermissionGranted) {
        // Re-check permission in case it changed
        locationPermissionGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Persist cart to ViewModel whenever it changes
    LaunchedEffect(billItems, discountPercentage, discountValue, cashPaid, selectedCustomer) {
        appViewModel.setImageBillCart(billItems.map { item ->
            mapOf<String, Any>(
                "id" to item.id, "code" to item.code, "name" to item.name,
                "sellingPrice" to item.sellingPrice, "quantity" to item.quantity,
                "unitPrice" to item.unitPrice, "discountValue" to item.discountValue,
                "discountPercentage" to item.discountPercentage, "returnQty" to item.returnQty,
                "invoiceId" to item.invoiceId, "supplierId" to item.supplierId,
                "supplierName" to item.supplierName, "invoiceName" to item.invoiceName,
                "totalQty" to item.totalQty, "image" to item.image, "categoryId" to item.categoryId
            )
        })
        appViewModel.setImageBillDiscount(discountPercentage)
        appViewModel.setImageBillDiscountValue(discountValue)
        appViewModel.setImageBillCashPaid(cashPaid)
        if (selectedCustomer != null) {
            appViewModel.setImageBillCustomer(mapOf(
                "id" to (selectedCustomer!!.id as Any),
                "name" to (selectedCustomer!!.name as Any),
                "telephone" to (selectedCustomer!!.telephone as Any),
                "whatsappNo" to (selectedCustomer!!.whatsappNo as Any),
                "address" to (selectedCustomer!!.address as Any)
            ))
        } else {
            appViewModel.setImageBillCustomer(null)
        }
    }

    fun openItemDialog(item: ImageBillItem) {
        val existing = billItems.find { it.id == item.id }
        dialogItem = item
        dialogPrice = (existing?.unitPrice ?: item.sellingPrice).toString()
        dialogQty = formatQty(existing?.quantity ?: 1.0)
        dialogReturnQty = formatQty(existing?.returnQty ?: 0.0)
        showItemDialog = true
    }

    fun confirmItemDialog() {
        val item = dialogItem ?: return
        val price = dialogPrice.toDoubleOrNull() ?: item.sellingPrice
        val qty = dialogQty.toDoubleOrNull() ?: 1.0
        val retQty = dialogReturnQty.toDoubleOrNull() ?: 0.0
        if (qty <= 0.0 && retQty <= 0.0) {
            billItems = billItems.filter { it.id != item.id }
        } else {
            val existing = billItems.find { it.id == item.id }
            billItems = if (existing != null) {
                billItems.map {
                    if (it.id == item.id) it.copy(quantity = qty, unitPrice = price, returnQty = retQty) else it
                }
            } else {
                billItems + item.copy(quantity = qty, unitPrice = price, returnQty = retQty)
            }
        }
        showItemDialog = false
        dialogItem = null
    }

    fun openEditDialog(item: ImageBillItem) {
        // Find the original item data (with supplierInvoices) from allItems
        val original = allItems.find { it.id == item.id }
        editItem = item
        editQty = formatQty(item.quantity)
        editPrice = item.unitPrice.toString()
        editDiscPct = if (item.discountPercentage > 0) item.discountPercentage.toString() else ""
        editDiscVal = if (item.discountValue > 0) item.discountValue.toString() else ""
        editSupplierId = item.supplierId
        editSupplierName = item.supplierName
        editInvoiceId = item.invoiceId
        editInvoiceName = item.invoiceName
        editTotalQty = item.totalQty
        showEditDialog = true
    }

    fun applyEditDialog() {
        val item = editItem ?: return
        val qty = editQty.toDoubleOrNull() ?: 1.0
        val price = editPrice.toDoubleOrNull() ?: item.unitPrice
        val dPct = editDiscPct.toDoubleOrNull() ?: 0.0
        val dVal = if (dPct > 0) price * dPct / 100.0 else (editDiscVal.toDoubleOrNull() ?: 0.0)
        val existingRet = billItems.find { it.id == item.id }?.returnQty ?: 0.0

        if (qty <= 0.0 && existingRet <= 0.0) {
            billItems = billItems.filter { it.id != item.id }
        } else {
            billItems = billItems.map {
                if (it.id == item.id) it.copy(
                    quantity = qty,
                    unitPrice = price,
                    discountPercentage = dPct,
                    discountValue = dVal,
                    supplierId = editSupplierId,
                    supplierName = editSupplierName,
                    invoiceId = editInvoiceId,
                    invoiceName = editInvoiceName,
                    totalQty = editTotalQty
                ) else it
            }
        }
        showEditDialog = false
        editItem = null
    }

    fun removeItem(itemId: Int) {
        billItems = billItems.filter { it.id != itemId }
    }

    fun updateQuantity(itemId: Int, qty: Double) {
        val item = billItems.find { it.id == itemId }
        val ret = item?.returnQty ?: 0.0
        if (qty <= 0.0 && ret <= 0.0) {
            removeItem(itemId)
        } else {
            val newQty = maxOf(qty, 0.0)
            billItems = billItems.map {
                if (it.id == itemId) it.copy(quantity = newQty) else it
            }
        }
    }

    fun clearBill() {
        billItems = emptyList()
        discountPercentage = ""
        discountValue = ""
        cashPaid = ""
        customerQuery = ""
        selectedCustomer = null
        whatsAppNo = ""
        appViewModel.clearImageBillCart()
    }

    fun saveBill() {
        Log.d("ImageBillScreen", "saveBill called: items=${billItems.size}, hasCustomer=$hasCustomer, cash=$cash, netTotal=$netTotal, token=${token != null}")

        // Check if location services are enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled && !skipLocationWarning) {
            showLocationWarning = true
            return
        }
        if (billItems.isEmpty()) {
            errorMessage = "Please add items"
            showErrorDialog = true
            return
        }
        if (!hasCustomer && cash < netTotal && cash > 0) {
            errorMessage = "Insufficient payment. Select a customer for credit billing."
            showErrorDialog = true
            return
        }
        if (!hasCustomer && cash <= 0.0 && netTotal > 0.0) {
            Log.d("ImageBillScreen", "No customer and no cash - showing error")
            errorMessage = "Please enter cash amount or select a customer"
            showErrorDialog = true
            return
        }

        val allowExceeded = config?.allowBillingStockQtyExceeded == "Y"
        if (!allowExceeded) {
            // Only validate stock for items that actually deduct stock (quantity > 0).
            // Pure returns (quantity=0, returnQty>0) don't touch stock.
            val firstExceeded = billItems.firstOrNull {
                it.quantity > 0.0 && it.quantity > it.supplierInvoices.sumOf { si -> si.qty }
            }
            if (firstExceeded != null) {
                val available = firstExceeded.supplierInvoices.sumOf { si -> si.qty }
                errorMessage = "Stock Qty Exceeded for [${firstExceeded.name}]. Available: ${formatQty(available)}, Requested: ${formatQty(firstExceeded.quantity)}"
                showErrorDialog = true
                return
            }
        }

        // Check if printer is connected
        val printerService = PrinterService.getInstance()
        if (!printerService.isConnected() && !skipPrinterWarning) {
            showPrinterWarning = true
            return
        }

        // Capture values before clearing
        val printItems = billItems.toList()
        val printCustomer = selectedCustomer?.name ?: customerQuery
        val printGrossTotal = grossTotal
        val printDiscount = totalDiscount
        // Backend / receipt use the true net (can be negative on refund).
        // netTotal (clamped) is only used for credit-bill detection and the cart UI.
        val printTrueNetTotal = trueNetTotal
        val printNetTotal = printTrueNetTotal
        val printCash = cash
        val printIsRefund = isRefund
        val printRefundAmount = refundAmount

        isSaving = true
        val saveBillType = if (isCreditBill) "CREDIT" else "CASH"
        val saveCustomerId = selectedCustomer?.id ?: 0
        val saveWhatsApp = whatsAppNo

        scope.launch {
            // Get location on Main thread (FusedLocationProvider requires it)
            val location = LocationHelper.getCurrentLocation(context)
            Log.d("ImageBillScreen", "Location: ${location.latitude}, ${location.longitude}")

            val hasReturnItems = printItems.any { it.returnQty > 0.0 }

            val result = withContext(Dispatchers.IO) {
                // Fetch next bill number from API
                val nextBillNo = fetchNextBillNo(context, token!!, saveBillType)
                Log.d("ImageBillScreen", "Next bill no for $saveBillType: $nextBillNo")

                saveImageBill(
                    context = context,
                    token = token!!,
                    items = printItems,
                    customerName = printCustomer,
                    customerId = saveCustomerId,
                    whatsApp = saveWhatsApp,
                    discount = printDiscount,
                    cashPaid = printCash,
                    isCreditBill = isCreditBill,
                    netTotal = printNetTotal,
                    billNo = nextBillNo,
                    location = location,
                    allowBillingStockQtyExceeded = allowExceeded,
                    // Defer WhatsApp until salesReturn has applied; we resend below.
                    skipWhatsAppNotification = hasReturnItems
                )
            }

            if (result != null && !result.startsWith("Failed") && !result.startsWith("Exception")) {
                val savedBillNo = result

                // Step 2: Submit sales return if any items have returnQty > 0
                if (hasReturnItems) {
                    try {
                        val billNoInt = savedBillNo.toIntOrNull() ?: 0
                        if (billNoInt > 0) {
                            // Load the saved bill to get server-side item details
                            val loadedBill = withContext(Dispatchers.IO) {
                                loadSavedBill(token!!, billNoInt, saveBillType)
                            }
                            if (loadedBill != null) {
                                val returnQtyMap: Map<Int, Double> = printItems.filter { it.returnQty > 0.0 }
                                    .associate { it.id to it.returnQty }
                                val sendToDamages = config?.sendReturnItemFromImageBillsToDamagesAndroidApp == "Y"
                                val returnSuccess = withContext(Dispatchers.IO) {
                                    submitImageBillReturn(
                                        token = token!!,
                                        billNo = billNoInt,
                                        billType = saveBillType,
                                        loadedBillItems = loadedBill,
                                        returnQtyMap = returnQtyMap,
                                        returnType = if (sendToDamages) "DAMAGE" else "STOCK"
                                    )
                                }
                                if (returnSuccess) {
                                    Log.d("ImageBillScreen", "Sales return submitted for bill #$savedBillNo")
                                } else {
                                    Log.w("ImageBillScreen", "Sales return failed for bill #$savedBillNo")
                                }
                                // Now that returnQty is persisted, trigger the deferred
                                // WhatsApp so the PDF reflects the final bill state
                                // (returns shown, correct totals).
                                withContext(Dispatchers.IO) {
                                    triggerBillWhatsApp(
                                        token = token!!,
                                        billNo = billNoInt,
                                        billType = saveBillType,
                                        cashWhatsApp = saveWhatsApp,
                                        paidAmount = printCash
                                    )
                                }
                            } else {
                                Log.e("ImageBillScreen", "Failed to load saved bill #$savedBillNo for return")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ImageBillScreen", "Sales return error: ${e.message}", e)
                    }
                }

                // Last-chance template load: it normally preloads in the background, but if
                // the user saved before that finished (or it failed), load it now so print /
                // preview / share are never silently skipped.
                if (billTemplate == null) {
                    val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
                    val billType = if (use58mm) "android58" else "android"
                    billTemplate = loadImageBillTemplate(context, token!!, billType)
                }

                // Print receipt
                try {
                    val printerService = PrinterService.getInstance()
                    if (printerService.isConnected() && billTemplate != null) {
                        val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                        val receiptItems = printItems.filter { it.quantity > 0.0 }.map {
                            ReceiptItem(it.name, it.quantity, it.unitPrice, it.quantity * it.unitPrice)
                        }
                        val receiptReturnItems = printItems.filter { it.returnQty > 0.0 }.map {
                            ReceiptItem(it.name, it.returnQty, it.unitPrice, it.returnQty * it.unitPrice)
                        }
                        val totalReturn = receiptReturnItems.sumOf { it.amount }
                        val receiptGrossTotal = printItems.sumOf { it.quantity * it.unitPrice }
                        // Allow negative net on refund (return > sale). The receipt's
                        // balance line flips to "Outstanding:" via buildImageBillReceipt
                        // when net > cashPaid.
                        val receiptNetTotal = receiptGrossTotal - totalReturn - printDiscount
                        val receiptData = buildImageBillReceipt(
                            billTemplate!!,
                            receiptItems,
                            receiptReturnItems,
                            totalReturn,
                            printCustomer,
                            receiptGrossTotal,
                            printDiscount,
                            receiptNetTotal,
                            printCash,
                            savedBillNo
                        )
                        val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                        if (printedFile != null) {
                            lastReceiptPdf = printedFile
                            if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                previewPdfFile = printedFile
                            }
                        }
                        Log.d("ImageBillScreen", "Receipt printed for bill #$savedBillNo")
                        // Print second copy if config is set
                        // print2ReceiptsOnlyForCreditBillsAndroidApp has higher priority:
                        // if enabled, print 2 receipts only for credit bills.
                        // Refund bills (negative net) always force a 2nd copy — one for
                        // the shop, one for the customer — regardless of config.
                        val configWantsSecondCopy = if (config?.print2ReceiptsOnlyForCreditBillsAndroidApp == "Y") {
                            isCreditBill
                        } else {
                            config?.print2ReceiptsWhenPrintBillAndroidApp == "Y"
                        }
                        val shouldPrintSecondCopy = configWantsSecondCopy || printIsRefund
                        if (shouldPrintSecondCopy) {
                            kotlinx.coroutines.delay(10000)
                            printerService.printPdfReceipt(pdfGenerator, receiptData)
                            Log.d("ImageBillScreen", "Second receipt printed for bill #$savedBillNo")
                        }
                    } else {
                        Log.w("ImageBillScreen", "Printer not connected or template not loaded, skipping print")
                        // Even without a printer, generate PDF for preview/share if enabled
                        val showPreview = config?.showReceiptPrintPreviewAndroidApp == "Y"
                        val enableShare = config?.enableShareAndroidApp == "Y"
                        if (billTemplate != null && (showPreview || enableShare)) {
                            val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                            val receiptItems = printItems.filter { it.quantity > 0.0 }.map {
                                ReceiptItem(it.name, it.quantity, it.unitPrice, it.quantity * it.unitPrice)
                            }
                            val receiptReturnItems = printItems.filter { it.returnQty > 0.0 }.map {
                                ReceiptItem(it.name, it.returnQty, it.unitPrice, it.returnQty * it.unitPrice)
                            }
                            val totalReturn = receiptReturnItems.sumOf { it.amount }
                            val receiptGrossTotal = printItems.sumOf { it.quantity * it.unitPrice }
                            val receiptNetTotal = receiptGrossTotal - totalReturn - printDiscount
                            val receiptData = buildImageBillReceipt(
                                billTemplate!!,
                                receiptItems,
                                receiptReturnItems,
                                totalReturn,
                                printCustomer,
                                receiptGrossTotal,
                                printDiscount,
                                receiptNetTotal,
                                printCash,
                                savedBillNo
                            )
                            val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                            lastReceiptPdf = pdfFile
                            if (showPreview) {
                                previewPdfFile = pdfFile
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ImageBillScreen", "Print error: ${e.message}", e)
                }

                // On refund (return > sale), record the amount the owner paid back
                // to the customer in owner_customer_payments. Walk-in refunds (no
                // selected customer) are still recorded — customerId=0 with the
                // typed-in name in customerName so the row stays attributable.
                if (printIsRefund) {
                    try {
                        val billNoForPayment = savedBillNo.toLongOrNull() ?: 0L
                        recordOwnerCustomerPayment(
                            context = context,
                            token = token!!,
                            customerId = saveCustomerId.toLong(),
                            customerName = printCustomer,
                            billId = billNoForPayment,
                            billType = saveBillType,
                            amount = printRefundAmount,
                            note = "Refund for bill #$savedBillNo"
                        )
                    } catch (e: Exception) {
                        Log.e("ImageBillScreen", "Owner→customer payment error: ${e.message}", e)
                    }
                }

                isSaving = false
                successMessage = if (printIsRefund)
                    "Bill #$savedBillNo saved. Refund: ${String.format("%.2f", printRefundAmount)}"
                else
                    "Bill #$savedBillNo saved successfully!"
                showSuccessDialog = true
                clearBill()
            } else {
                isSaving = false
                errorMessage = result ?: "Failed to save bill"
                showErrorDialog = true
            }
        }
    }

    // ==================== UI ====================

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Image Bill", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (totalItems > 0.0) {
                            Text("${formatQty(totalItems)} items", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Cart badge
                    if (totalItems > 0.0) {
                        BadgedBox(badge = {
                            Badge(containerColor = Color.Red) {
                                Text(formatQty(totalItems), color = Color.White, fontSize = 10.sp)
                            }
                        }) {
                            IconButton(onClick = { showCartSheet = true }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = Color.White)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A237E))
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1A237E))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5))
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search items...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Categories
                if (categories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryId == null || selectedCategoryId == 0,
                                onClick = { selectedCategoryId = null },
                                label = { Text("All", fontSize = 12.sp) }
                            )
                        }
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name, fontSize = 12.sp, maxLines = 1) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Items grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val inCart = billItems.any { it.id == item.id }
                        val cartQty = billItems.find { it.id == item.id }?.quantity ?: 0.0

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { openItemDialog(item) },
                            shape = RoundedCornerShape(10.dp),
                            border = if (inCart) BorderStroke(2.dp, Color(0xFF1A237E)) else null,
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    val imageModel = com.pos.cosmos.local.LocalImageStore
                                        .localOrRemote(context, item.id.toLong(), item.image)
                                    if (imageModel != null) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = item.name,
                                            modifier = Modifier.fillMaxWidth().height(90.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp)
                                                .background(Color(0xFFE8EAF6)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF9FA8DA), modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    if (inCart) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF1A237E)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(formatQty(cartQty), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    item.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                Text(
                                    String.format("%.2f", item.sellingPrice),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A237E),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom bar with totals and pay button
                if (billItems.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = Color(0xFF1A1A2E)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val inputColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color(0xFFE0E0E0),
                                focusedBorderColor = Color(0xFF4FC3F7),
                                unfocusedBorderColor = Color(0xFF90A4AE),
                                focusedLabelColor = Color(0xFF4FC3F7),
                                unfocusedLabelColor = Color(0xFF90A4AE),
                                cursorColor = Color.White
                            )

                            // Totals row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gross: ${String.format("%.2f", grossTotal)}", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                                if (totalDiscount > 0) {
                                    Text("Disc: -${String.format("%.2f", totalDiscount)}", fontSize = 13.sp, color = Color(0xFFEF5350))
                                }
                                Text("Net: ${String.format("%.2f", netTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4FC3F7))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Discount row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = discountPercentage,
                                    onValueChange = { discountPercentage = it; discountValue = "" },
                                    label = { Text("Disc %", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    colors = inputColors
                                )
                                OutlinedTextField(
                                    value = discountValue,
                                    onValueChange = { discountValue = it; discountPercentage = "" },
                                    label = { Text("Disc Val", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    colors = inputColors
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Customer + WhatsApp row
                            if (showCustomerField) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = customerQuery,
                                            onValueChange = {
                                                customerQuery = it
                                                showCustomerSuggestions = it.isNotBlank()
                                                if (selectedCustomer != null && selectedCustomer!!.name != it) {
                                                    selectedCustomer = null
                                                    whatsAppNo = ""
                                                }
                                            },
                                            label = { Text("Customer", fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            colors = inputColors
                                        )
                                        if (showCustomerSuggestions && filteredCustomers.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 48.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                            ) {
                                                Column(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                                                    filteredCustomers.forEach { cust ->
                                                        Text(
                                                            cust.name,
                                                            fontSize = 13.sp,
                                                            color = Color(0xFF212121),
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    customerQuery = cust.name
                                                                    selectedCustomer = cust
                                                                    whatsAppNo = cust.whatsappNo
                                                                    showCustomerSuggestions = false
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                                        )
                                                        HorizontalDivider()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (showWhatsAppField) {
                                        OutlinedTextField(
                                            value = whatsAppNo,
                                            onValueChange = { whatsAppNo = it },
                                            label = { Text("WhatsApp", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            singleLine = true,
                                            colors = inputColors
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            // Cash + Balance row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = cashPaid,
                                    onValueChange = { cashPaid = it },
                                    label = { Text("Cash", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    colors = inputColors
                                )
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                    if (cash > 0 && balance >= 0) {
                                        Text("Balance: ${String.format("%.2f", balance)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    } else if (isCreditBill) {
                                        val outstanding = if (cash > 0) netTotal - cash else netTotal
                                        Text("Outstanding: ${String.format("%.2f", outstanding)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pay button
                            Button(
                                onClick = { saveBill() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCreditBill) Color(0xFFE65100) else Color(0xFF1A237E)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isCreditBill) "Save Credit Bill" else "Pay Bill",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Cart bottom sheet dialog
    if (showCartSheet && billItems.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showCartSheet = false },
            title = { Text("Cart (${billItems.size} items)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    billItems.filter { it.quantity > 0.0 }.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showCartSheet = false
                                        openEditDialog(item)
                                    }
                            ) {
                                Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4FC3F7))
                                Text("${String.format("%.2f", item.unitPrice)} x ${formatQty(item.quantity)} = ${String.format("%.2f", item.unitPrice * item.quantity)}", fontSize = 11.sp, color = Color.Gray)
                                if (item.discountValue > 0) {
                                    Text("Disc: -${String.format("%.2f", item.discountValue * item.quantity)}", fontSize = 10.sp, color = Color.Red)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { updateQuantity(item.id, item.quantity - 1.0) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                                Text(formatQty(item.quantity), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                IconButton(onClick = { updateQuantity(item.id, item.quantity + 1.0) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { removeItem(item.id) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    // Return items section
                    val returnItems = billItems.filter { it.returnQty > 0 }
                    if (showReturnQtyField && returnItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Return Items",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        HorizontalDivider(color = Color(0xFFE53935))
                        returnItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE53935))
                                    Text(
                                        "Return: ${formatQty(item.returnQty)} x ${String.format("%.2f", item.unitPrice)} = -${String.format("%.2f", item.returnQty * item.unitPrice)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE53935)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCartSheet = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { clearBill(); showCartSheet = false }) {
                    Text("Clear All", color = Color.Red)
                }
            }
        )
    }

    // Success dialog (auto-dismisses after 5s)
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
            confirmButton = { TextButton(onClick = { showSuccessDialog = false }) { Text("OK") } },
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

    // Printer warning dialog
    if (showPrinterWarning) {
        AlertDialog(
            onDismissRequest = { showPrinterWarning = false },
            title = { Text("Printer Not Connected") },
            text = { Text("No printer is connected. The bill will be saved but will not be printed. Please connect a printer from the Printer screen.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPrinterWarning = false
                        skipPrinterWarning = true
                        saveBill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Text("Save Without Printing", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrinterWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Location warning dialog
    if (showLocationWarning) {
        AlertDialog(
            onDismissRequest = { showLocationWarning = false },
            title = { Text("Location Services Disabled") },
            text = { Text("Location tracking is turned off. The bill will be saved without location data. Please enable Location Services in device settings for accurate tracking.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationWarning = false
                        skipLocationWarning = true
                        saveBill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) {
                    Text("Continue Without Location", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } }
        )
    }

    // Item price/qty dialog
    if (showItemDialog && dialogItem != null) {
        val item = dialogItem!!
        val inCart = billItems.any { it.id == item.id }

        AlertDialog(
            onDismissRequest = { showItemDialog = false; dialogItem = null },
            title = {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = dialogPrice,
                        onValueChange = { dialogPrice = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEditSellingPrice
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val q = (dialogQty.toDoubleOrNull() ?: 1.0) - 1.0
                                if (q >= 0.0) dialogQty = formatQty(q)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        OutlinedTextField(
                            value = dialogQty,
                            onValueChange = { dialogQty = it },
                            label = { Text("Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val q = (dialogQty.toDoubleOrNull() ?: 0.0) + 1.0
                                dialogQty = formatQty(q)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                    if (showReturnQtyField) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val rq = (dialogReturnQty.toDoubleOrNull() ?: 0.0) - 1.0
                                    if (rq >= 0.0) dialogReturnQty = formatQty(rq)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease Return", tint = Color.White)
                            }
                            OutlinedTextField(
                                value = dialogReturnQty,
                                onValueChange = { dialogReturnQty = it },
                                label = { Text("Return Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val rq = (dialogReturnQty.toDoubleOrNull() ?: 0.0) + 1.0
                                    dialogReturnQty = formatQty(rq)
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase Return", tint = Color.White)
                            }
                        }
                    }
                    val price = dialogPrice.toDoubleOrNull() ?: 0.0
                    val qty = dialogQty.toDoubleOrNull() ?: 0.0
                    val retQty = dialogReturnQty.toDoubleOrNull() ?: 0.0
                    val subtotal = (qty - retQty) * price
                    if (price > 0 && (qty > 0.0 || retQty > 0.0)) {
                        Text(
                            "Subtotal: ${String.format("%.2f", subtotal)}",
                            fontWeight = FontWeight.Bold,
                            color = if (subtotal >= 0) Color(0xFF4CAF50) else Color(0xFFE53935),
                            fontSize = 15.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmItemDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text(if (inCart) "Update" else "Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (inCart) {
                    TextButton(onClick = {
                        billItems = billItems.filter { it.id != item.id }
                        showItemDialog = false
                        dialogItem = null
                    }) {
                        Text("Remove", color = Color.Red)
                    }
                } else {
                    TextButton(onClick = { showItemDialog = false; dialogItem = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Edit item dialog (full details - supplier, invoice, discount)
    if (showEditDialog && editItem != null) {
        val item = editItem!!
        val original = allItems.find { it.id == item.id }
        val invoices = original?.supplierInvoices ?: emptyList()

        // Unique suppliers and invoices for autocomplete filtering
        val uniqueSuppliers = remember(invoices) { invoices.map { it.supplierName }.distinct() }
        var supplierQuery by remember(editItem) { mutableStateOf(editSupplierName) }
        var showSupplierDropdown by remember { mutableStateOf(false) }
        val filteredSuppliers = remember(supplierQuery, uniqueSuppliers) {
            if (supplierQuery.isBlank()) uniqueSuppliers
            else uniqueSuppliers.filter { it.contains(supplierQuery, ignoreCase = true) }
        }

        val invoicesForSupplier = remember(editSupplierName, invoices) {
            invoices.filter { it.supplierName == editSupplierName }
        }
        var invoiceQuery by remember(editItem) { mutableStateOf(editInvoiceName) }
        var showInvoiceDropdown by remember { mutableStateOf(false) }
        val filteredInvoices = remember(invoiceQuery, invoicesForSupplier) {
            if (invoiceQuery.isBlank()) invoicesForSupplier
            else invoicesForSupplier.filter { it.invoiceName.contains(invoiceQuery, ignoreCase = true) }
        }

        val labelColor = Color(0xFF1565C0)
        val valueColor = Color(0xFF000000)

        AlertDialog(
            onDismissRequest = { showEditDialog = false; editItem = null },
            title = {
                Text("Edit Item", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF4FC3F7))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Item Code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Item Code", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(90.dp), color = labelColor)
                        Text(item.code, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
                    }

                    // Item Name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Item Name", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(90.dp), color = labelColor)
                        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
                    }

                    // Supplier (autocomplete)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = supplierQuery,
                            onValueChange = {
                                supplierQuery = it
                                showSupplierDropdown = it.isNotBlank()
                            },
                            label = { Text("Supplier") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showSupplierDropdown = !showSupplierDropdown }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = showSupplierDropdown && filteredSuppliers.isNotEmpty(),
                            onDismissRequest = { showSupplierDropdown = false }
                        ) {
                            filteredSuppliers.forEach { sName ->
                                DropdownMenuItem(
                                    text = { Text(sName, fontSize = 13.sp) },
                                    onClick = {
                                        supplierQuery = sName
                                        editSupplierName = sName
                                        showSupplierDropdown = false
                                        // Auto-select first invoice of this supplier
                                        val firstInv = invoices.firstOrNull { it.supplierName == sName }
                                        if (firstInv != null) {
                                            editSupplierId = firstInv.supplierId
                                            editInvoiceId = firstInv.invoiceId
                                            editInvoiceName = firstInv.invoiceName
                                            invoiceQuery = firstInv.invoiceName
                                            editTotalQty = firstInv.qty
                                            editPrice = firstInv.sellingPrice.toString()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Invoice + Invoice Qty on same row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Invoice (autocomplete)
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = invoiceQuery,
                                onValueChange = {
                                    invoiceQuery = it
                                    showInvoiceDropdown = it.isNotBlank()
                                },
                                label = { Text("Invoice") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { showInvoiceDropdown = !showInvoiceDropdown }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = showInvoiceDropdown && filteredInvoices.isNotEmpty(),
                                onDismissRequest = { showInvoiceDropdown = false }
                            ) {
                                filteredInvoices.forEach { si ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${si.invoiceName}  (Qty: ${si.qty})", fontSize = 13.sp)
                                        },
                                        onClick = {
                                            invoiceQuery = si.invoiceName
                                            editInvoiceId = si.invoiceId
                                            editInvoiceName = si.invoiceName
                                            editTotalQty = si.qty
                                            editPrice = si.sellingPrice.toString()
                                            showInvoiceDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Invoice Qty (read-only)
                        Column(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Inv. Qty", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                formatQty(editTotalQty),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Qty with +/- buttons + Total Qty
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val q = (editQty.toDoubleOrNull() ?: 1.0) - 1.0
                                if (q >= 0.0) editQty = formatQty(q)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFE53935), CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        OutlinedTextField(
                            value = editQty,
                            onValueChange = { editQty = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val q = (editQty.toDoubleOrNull() ?: 0.0) + 1.0
                                editQty = formatQty(q)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                        Column(
                            modifier = Modifier.width(70.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Total Qty", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                formatQty(editTotalQty),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    // Selling Price (locked unless selling-price configs allow editing)
                    OutlinedTextField(
                        value = editPrice,
                        onValueChange = { editPrice = it },
                        label = { Text("Selling Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canEditSellingPrice
                    )

                    // Discount Percentage + Discount Value on same row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editDiscPct,
                            onValueChange = {
                                editDiscPct = it
                                val pct = it.toDoubleOrNull() ?: 0.0
                                val price = editPrice.toDoubleOrNull() ?: 0.0
                                if (pct > 0) editDiscVal = String.format("%.2f", price * pct / 100.0)
                            },
                            label = { Text("Discount %") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editDiscVal,
                            onValueChange = {
                                editDiscVal = it
                                editDiscPct = ""
                            },
                            label = { Text("Discount Val") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { applyEditDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        billItems = billItems.filter { it.id != item.id }
                        showEditDialog = false
                        editItem = null
                    }) {
                        Text("Delete", color = Color.Red)
                    }
                    TextButton(onClick = { showEditDialog = false; editItem = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

// ==================== API Functions ====================

private suspend fun fetchImageBillCategories(context: Context, token: String): List<ImageBillCategory> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.CosmosDatabase.get(context).categoryDao().all().map {
                ImageBillCategory(id = it.id.toInt(), name = it.name.orEmpty(), image = "")
            }
        } catch (e: Exception) { emptyList() }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/category/getAllCategories")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val categories = gson.fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()

            categories.mapNotNull { catMap ->
                try {
                    val id = (catMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val name = catMap["name"] as? String ?: return@mapNotNull null
                    val image = catMap["image"] as? String ?: ""
                    ImageBillCategory(id = id, name = name, image = image)
                } catch (e: Exception) { null }
            }
        } else emptyList()
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchCategories error: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchImageBillItems(context: Context, token: String): List<ImageBillItem> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.CosmosDatabase.get(context).itemDao().all()
                .filter { (it.sellingPrice > 0.0) && !it.name.isNullOrBlank() }
                .map {
                    ImageBillItem(
                        id = it.id.toInt(),
                        code = it.itemCode.orEmpty(),
                        name = it.name.orEmpty(),
                        sellingPrice = it.sellingPrice,
                        image = it.imageUrl.orEmpty(),
                        categoryId = it.categoryId?.toInt() ?: 0,
                        invoiceId = 0,
                        supplierId = 0,
                        supplierName = "",
                        invoiceName = "",
                        totalQty = it.stockQty,
                        supplierInvoices = emptyList()
                    )
                }
        } catch (e: Exception) {
            Log.e("ImageBillScreen", "local fetchImageBillItems failed", e)
            emptyList()
        }
    }
    return try {
        // Fetch item images
        val imageMap = mutableMapOf<Int, String>()
        try {
            val itemsConn = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
                .openConnection() as HttpURLConnection
            itemsConn.requestMethod = "GET"
            itemsConn.setRequestProperty("Authorization", "Bearer $token")
            itemsConn.connectTimeout = 30000
            itemsConn.readTimeout = 30000

            if (itemsConn.responseCode == 200) {
                val content = itemsConn.inputStream.bufferedReader().use { it.readText() }
                @Suppress("UNCHECKED_CAST")
                val allItems = Gson().fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()
                allItems.forEach { item ->
                    val id = (item["id"] as? Number)?.toInt()
                    val image = item["image"] as? String
                    if (id != null && !image.isNullOrBlank()) imageMap[id] = image
                }
            }
        } catch (e: Exception) {
            Log.e("ImageBillScreen", "Error loading images: ${e.message}")
        }

        // Fetch bill items
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllBillItems?billType=CASH")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val response = Gson().fromJson(content, Map::class.java) as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val billItems = response?.get("billItems") as? List<Map<String, Any>> ?: emptyList()

            billItems.mapNotNull { itemMap ->
                try {
                    val itemId = (itemMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val code = itemMap["code"] as? String ?: ""
                    val name = itemMap["name"] as? String ?: ""
                    val price = (itemMap["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                    val categoryId = (itemMap["categoryId"] as? Number)?.toInt() ?: 0

                    if (price <= 0.0 || name.isBlank()) return@mapNotNull null

                    // Parse supplier/invoice list
                    var firstInvoiceId = 0
                    var firstSupplierId = 0
                    var firstSupplierName = ""
                    var firstInvoiceName = ""
                    var firstTotalQty = 0.0
                    val supplierInvoices = mutableListOf<SupplierInvoice>()

                    @Suppress("UNCHECKED_CAST")
                    val billSupplierList = itemMap["billSupplierList"] as? List<Map<String, Any>>
                    if (!billSupplierList.isNullOrEmpty()) {
                        for (supplierMap in billSupplierList) {
                            val sId = (supplierMap["supplierId"] as? Number)?.toInt() ?: 0
                            val sName = supplierMap["supplierName"] as? String ?: ""
                            @Suppress("UNCHECKED_CAST")
                            val invoiceList = supplierMap["billInvoiceList"] as? List<Map<String, Any>>
                            if (!invoiceList.isNullOrEmpty()) {
                                for (inv in invoiceList) {
                                    val invId = (inv["invoiceId"] as? Number)?.toInt() ?: 0
                                    val invName = inv["invoiceName"] as? String ?: ""
                                    val invQty = (inv["qty"] as? Number)?.toDouble() ?: 0.0
                                    val invPrice = (inv["sellingPrice"] as? Number)?.toDouble() ?: price
                                    val invDate = inv["invoiceDate"] as? String ?: ""
                                    supplierInvoices.add(SupplierInvoice(sId, sName, invId, invName, invQty, invPrice, invDate))
                                }
                            }
                        }
                    }

                    val sortedInvoices = supplierInvoices.sortedBy { it.invoiceDate }
                    val firstWithStock = sortedInvoices.firstOrNull { it.qty > 0.0 }
                    if (firstWithStock != null) {
                        firstInvoiceId = firstWithStock.invoiceId
                        firstSupplierId = firstWithStock.supplierId
                        firstSupplierName = firstWithStock.supplierName
                        firstInvoiceName = firstWithStock.invoiceName
                    }
                    firstTotalQty = sortedInvoices.sumOf { it.qty }

                    ImageBillItem(
                        id = itemId, code = code, name = name,
                        sellingPrice = price, image = imageMap[itemId] ?: "",
                        categoryId = categoryId,
                        invoiceId = firstInvoiceId,
                        supplierId = firstSupplierId,
                        supplierName = firstSupplierName,
                        invoiceName = firstInvoiceName,
                        totalQty = firstTotalQty,
                        supplierInvoices = sortedInvoices
                    )
                } catch (e: Exception) { null }
            }
        } else emptyList()
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchItems error: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchImageBillCustomers(context: Context, token: String): List<ImageBillCustomer> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.CosmosDatabase.get(context).customerDao().all().map {
                ImageBillCustomer(
                    id = it.id.toInt(),
                    name = it.name.orEmpty(),
                    telephone = it.telephone.orEmpty(),
                    whatsappNo = it.whatsapp.orEmpty(),
                    address = it.address.orEmpty()
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            @Suppress("UNCHECKED_CAST")
            val list = Gson().fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()
            list.mapNotNull { map ->
                try {
                    ImageBillCustomer(
                        id = (map["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                        name = map["name"] as? String ?: return@mapNotNull null,
                        telephone = map["telephone"] as? String ?: "",
                        whatsappNo = map["whatsappNo"] as? String ?: "",
                        address = map["address"] as? String ?: ""
                    )
                } catch (e: Exception) { null }
            }
        } else emptyList()
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchCustomers error: ${e.message}")
        emptyList()
    }
}

private val imageBillSaveInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

private suspend fun saveImageBill(
    context: Context,
    token: String,
    items: List<ImageBillItem>,
    customerName: String,
    customerId: Int,
    whatsApp: String,
    discount: Double,
    cashPaid: Double,
    isCreditBill: Boolean,
    netTotal: Double,
    billNo: Long = 0,
    location: UserLocation = UserLocation(0.0, 0.0),
    allowBillingStockQtyExceeded: Boolean = false,
    skipWhatsAppNotification: Boolean = false
): String? {
    if (!imageBillSaveInFlight.compareAndSet(false, true)) {
        Log.w("ImageBillScreen", "saveImageBill ignored — another save is already in progress")
        return null
    }
    try {
    val billType = if (isCreditBill) "CREDIT" else "CASH"

    // ── Local-first path (mirrors desktop ImageBillForm.SaveBillLocal) ──
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val localBillNo = com.pos.cosmos.local.LocalBillSaver.saveBill(
                context = context,
                billNo = 0,
                billType = billType,
                customerId = customerId.toLong(),
                cashCustomerName = if (customerId == 0 && customerName.isNotBlank()) customerName.trim() else null,
                salesRefId = 0,
                total = netTotal,
                discount = 0.0,
                discountValue = discount,
                cash = cashPaid,
                lines = items.map {
                    com.pos.cosmos.local.LocalBillLine(
                        itemId = it.id.toLong(),
                        unitPrice = it.unitPrice,
                        qty = it.quantity,
                        discount = 0.0,
                        discountValue = it.discountValue,
                        freeIssueQty = 0.0
                    )
                }
            )
            Log.d("ImageBillScreen", "Image bill $localBillNo queued locally (offline).")
            localBillNo.toString()
        } catch (e: Exception) {
            Log.e("ImageBillScreen", "Failed to save image bill locally", e)
            "Exception: ${e.message}"
        }
    }

    return try {
        val billData = JsonObject().apply {
            val itemsArray = com.google.gson.JsonArray()
            items.forEach { item ->
                // For pure returns (quantity=0, returnQty>0) we send qty=0 so backend's saveBill
                // doesn't deduct stock. The customer_item_return record is then created via the
                // separate salesReturn call (backend now allows qty < returnQty when returnType=DAMAGE).
                val invoices = item.supplierInvoices.sortedBy { it.invoiceDate }
                val distribution = if (item.quantity > 0.0 && invoices.isNotEmpty()) {
                    distributeQty(item.quantity, invoices, allowBillingStockQtyExceeded)
                } else emptyList()

                val lines = if (distribution.isNotEmpty()) distribution
                else listOf(DistributionLine(item.supplierId, item.supplierName, item.invoiceId, item.invoiceName, item.quantity, item.totalQty, item.unitPrice))

                lines.forEachIndexed { idx, line ->
                    val obj = JsonObject().apply {
                        addProperty("itemCode", item.code)
                        addProperty("itemName", item.name)
                        addProperty("invoiceId", line.invoiceId)
                        addProperty("supplierId", line.supplierId)
                        addProperty("invoiceName", line.invoiceName)
                        addProperty("supplierName", line.supplierName)
                        addProperty("itemId", item.id)
                        addProperty("qty", line.newQty)
                        addProperty("unitPrice", item.unitPrice)
                        addProperty("discount", 0.0)
                        addProperty("discountValue", item.discountValue)
                        addProperty("discountPercentage", item.discountPercentage)
                        addProperty("totalPrice", line.newQty * (item.unitPrice - item.discountValue))
                        addProperty("totalQty", line.invoiceQty)
                        addProperty("returnQty", 0.0)
                        addProperty("isBox", false)
                    }
                    itemsArray.add(obj)
                }
            }
            add("billItems", itemsArray)
            addProperty("billNo", billNo)
            addProperty("cash", cashPaid)
            addProperty("billType", billType)
            addProperty("salesRefId", 0)
            addProperty("discount", discount)
            addProperty("discountValue", discount)
            addProperty("discountPercentage", 0.0)
            addProperty("customerId", customerId)
            addProperty("paidAmount", cashPaid)
            addProperty("total", netTotal)
            // Intentionally do NOT send billingDate or dateStr: backend's saveBill falls back
            // to `new Date()` (server time) when both are null, which avoids tablet-clock /
            // timezone drift in the saved bill timestamp.
            addProperty("id", 0)
            addProperty("customerName", if (customerId == 0 && customerName.isNotBlank()) customerName.trim() else "")
            addProperty("salesRefName", "")
            val eventObj = JsonObject().apply {
                addProperty("event", "Save $billType Image Bill $billNo")
                addProperty("latitude", location.latitude.toString())
                addProperty("longitude", location.longitude.toString())
                addProperty("userName", "")
                addProperty("eventTime", "")
                add("mapLocations", null)
            }
            add("userEvent", eventObj)
            addProperty("paymentMethod", "CASH")
            addProperty("paymentDetails", "")
            if (whatsApp.isNotBlank()) {
                addProperty("cashCustomerWhatsApp", whatsApp.trim())
            }
            // Defer WhatsApp until salesReturn has finished updating the bill's
            // returnQty values; the caller then triggers /bill/sendWhatsApp so the
            // PDF reflects the final state.
            if (skipWhatsAppNotification) {
                addProperty("skipNotification", true)
            }
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBill")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = billData.toString()
        Log.d("ImageBillScreen", "Saving bill: $jsonString")
        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            try {
                val response = Gson().fromJson(responseBody, JsonObject::class.java)
                val bill = response.getAsJsonObject("bill")
                bill?.get("billNo")?.asString ?: billNo.toString()
            } catch (e: Exception) {
                billNo.toString()
            }
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            "Failed: ${connection.responseCode} - $errorResponse"
        }
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "saveImageBill error: ${e.message}", e)
        "Exception: ${e.message}"
    }
    } finally {
        imageBillSaveInFlight.set(false)
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun loadSavedBill(token: String, billNo: Int, billType: String): JsonObject? {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/loadBill?billId=$billNo&billType=$billType")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        if (conn.responseCode in 200..299) {
            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d("ImageBillScreen", "Loaded bill #$billNo: ${responseBody.take(500)}")
            Gson().fromJson(responseBody, JsonObject::class.java)
        } else {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
            Log.e("ImageBillScreen", "loadSavedBill failed: ${conn.responseCode} - $error")
            null
        }
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "loadSavedBill error: ${e.message}", e)
        null
    }
}

private suspend fun submitImageBillReturn(
    token: String,
    billNo: Int,
    billType: String,
    loadedBillItems: JsonObject,
    returnQtyMap: Map<Int, Double>,
    returnType: String = "STOCK"
): Boolean {
    return try {
        // Get billItems array from the loaded bill response
        val serverItems = loadedBillItems.getAsJsonArray("billItems")
            ?: loadedBillItems.getAsJsonObject("bill")?.getAsJsonArray("billItems")
        if (serverItems == null) {
            Log.e("ImageBillScreen", "No billItems found in loaded bill response")
            return false
        }

        // Pick, per itemId, the single server line with the largest qty — that's where
        // we'll attach the user's returnQty (so the backend's qty >= returnQty check passes
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
            val itemsArray = com.google.gson.JsonArray()
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
        Log.d("ImageBillScreen", "Submitting sales return: $jsonString")
        conn.outputStream.bufferedWriter().use { it.write(jsonString) }

        val responseCode = conn.responseCode
        Log.d("ImageBillScreen", "Sales return response code: $responseCode")
        val success = responseCode in 200..299
        if (!success) {
            val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("ImageBillScreen", "Sales return failed: $responseCode - $errorResponse")
        } else {
            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            Log.d("ImageBillScreen", "Sales return success response: $responseBody")
        }
        success
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "submitImageBillReturn error: ${e.message}", e)
        false
    }
}

// Record an owner → customer payment (refund). When useLocalDatabase is enabled,
// inserts into the local Room table first so the user gets immediate persistence
// even offline; the row is marked unsynced and later pushed via SyncRepository.
// Otherwise POSTs directly to the backend.
private suspend fun recordOwnerCustomerPayment(
    context: Context,
    token: String,
    customerId: Long,
    customerName: String,
    billId: Long,
    billType: String,
    amount: Double,
    note: String
) = withContext(Dispatchers.IO) {
    val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
    val effectiveName = customerName.trim().ifBlank { "Walk-in" }

    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        try {
            val dao = com.pos.cosmos.local.CosmosDatabase.get(context).ownerCustomerPaymentDao()
            val localId = dao.insert(
                com.pos.cosmos.local.entities.OwnerCustomerPaymentEntity(
                    customerId = customerId,
                    customerName = effectiveName,
                    billId = billId,
                    billType = billType,
                    paymentDate = isoDate,
                    amount = amount,
                    method = "CASH",
                    note = note,
                    isSynced = false
                )
            )
            Log.d("ImageBillScreen", "Owner→customer payment queued locally id=$localId amount=$amount name=$effectiveName")
        } catch (e: Exception) {
            Log.e("ImageBillScreen", "Local owner-payment insert failed: ${e.message}", e)
        }
        return@withContext
    }

    try {
        val payload = JsonObject().apply {
            addProperty("customerId", customerId)
            addProperty("customerName", effectiveName)
            addProperty("billId", billId)
            addProperty("billType", billType)
            addProperty("amount", amount)
            addProperty("method", "CASH")
            addProperty("note", note)
            addProperty("active", "Y")
        }
        val conn = URL("${ApiConfig.BASE_URL_8080}/owner-customer/payment/add")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

        if (conn.responseCode in 200..299) {
            Log.d("ImageBillScreen", "Owner→customer payment posted: bill=$billId amount=$amount")
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
            Log.e("ImageBillScreen", "Owner→customer payment failed: ${conn.responseCode} - $err")
        }
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "Owner→customer payment exception: ${e.message}", e)
    }
}

// Re-send the WhatsApp notification for a bill that was saved with
// skipNotification=true. Used after a sales return finishes updating the
// bill's returnQty so the WhatsApp PDF includes the returns and the correct
// (possibly negative) total.
private suspend fun triggerBillWhatsApp(
    token: String,
    billNo: Int,
    billType: String,
    cashWhatsApp: String,
    paidAmount: Double
) = withContext(Dispatchers.IO) {
    try {
        val urlBuilder = StringBuilder("${ApiConfig.BASE_URL_8080}/bill/sendWhatsApp?billNo=$billNo&billType=$billType")
        urlBuilder.append("&paidAmount=$paidAmount")
        if (cashWhatsApp.isNotBlank()) {
            urlBuilder.append("&cashWhatsApp=").append(java.net.URLEncoder.encode(cashWhatsApp.trim(), "UTF-8"))
        }
        val conn = URL(urlBuilder.toString()).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        val code = conn.responseCode
        if (code in 200..299) {
            Log.d("ImageBillScreen", "WhatsApp resend triggered for bill #$billNo")
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
            Log.w("ImageBillScreen", "WhatsApp resend failed for bill #$billNo: $code - $err")
        }
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "WhatsApp resend exception for bill #$billNo: ${e.message}", e)
    }
}

/**
 * Loads the image-bill receipt template, network-first (to keep the logo and company
 * details fresh) with a cache fallback. Used by the background preload effect and as a
 * last-chance load in the save flow so print-preview / share never silently break.
 */
private suspend fun loadImageBillTemplate(
    context: android.content.Context,
    token: String,
    billType: String
): JsonObject? {
    val fresh = fetchImageBillTemplate(token, billType)
    if (fresh != null) {
        BillTemplateCache.saveTemplate(context, fresh, billType)
        return fresh
    }
    return BillTemplateCache.getTemplate(context, billType)
}

private suspend fun fetchImageBillTemplate(token: String, targetBillType: String = "android"): JsonObject? {
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
            val allTemplates = Gson().fromJson(content, com.google.gson.JsonArray::class.java)

            var result: JsonObject? = null
            if (allTemplates != null && allTemplates.size() > 0) {
                // Try exact match
                for (i in 0 until allTemplates.size()) {
                    val t = allTemplates.get(i).asJsonObject
                    if (t.get("billType")?.asString == targetBillType) { result = t; break }
                }
                // Fallback to "android"
                if (result == null && targetBillType != "android") {
                    for (i in 0 until allTemplates.size()) {
                        val t = allTemplates.get(i).asJsonObject
                        if (t.get("billType")?.asString == "android") { result = t; break }
                    }
                }
                // Fallback to "small"
                if (result == null) {
                    for (i in 0 until allTemplates.size()) {
                        val t = allTemplates.get(i).asJsonObject
                        if (t.get("billType")?.asString == "small") { result = t; break }
                    }
                }
            }
            result
        } else null
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchTemplate error: ${e.message}")
        null
    }
}

private fun buildImageBillReceipt(
    template: JsonObject,
    items: List<ReceiptItem>,
    returnItems: List<ReceiptItem> = emptyList(),
    totalReturn: Double = 0.0,
    customer: String,
    grossTotal: Double,
    discount: Double,
    netTotal: Double,
    cashPaid: Double,
    billNo: String
): ReceiptData {
    val balance = cashPaid - netTotal
    val balanceLabelText = if (balance < 0) "Outstanding:" else (template.get("balanceLabel")?.asString ?: "Balance:")
    val balanceValue = if (balance < 0) netTotal - cashPaid else balance
    val effectiveCashLabel = if (cashPaid > 0) (template.get("cashPaidLabel")?.asString ?: "Cash:") else ""

    return ReceiptData(
        items = items,
        returnItems = returnItems,
        totalReturn = totalReturn,
        customer = customer.ifBlank { "Walk-in" },
        salesRep = "",
        grossTotal = grossTotal,
        discount = discount,
        netTotal = netTotal,
        cashPaid = if (cashPaid > 0) cashPaid else 0.0,
        billNo = billNo,
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
        billNoLabel = template.get("billNoLabel")?.asString ?: "Bill No :",
        dateLabel = template.get("dateLabel")?.asString ?: "Date:",
        itemHeader = template.get("itemHeader")?.asString ?: "Item",
        priceHeader = template.get("priceHeader")?.asString ?: "Price",
        qtyHeader = template.get("qtyHeader")?.asString ?: "Qty",
        amountHeader = template.get("totalHeader")?.asString ?: "Amount",
        grossTotalLabel = template.get("grossTotalLabel")?.asString ?: "Gross Total:",
        discountLabel = template.get("billDiscountLabel")?.asString ?: "Discount:",
        netTotalLabel = template.get("netTotalLabel")?.asString ?: "Net Total:",
        cashLabel = effectiveCashLabel,
        balanceLabel = balanceLabelText,
        billNoFontSize = template.get("billNoFontSize")?.asFloat ?: 11f,
        dateFontSize = template.get("dateFontSize")?.asFloat ?: 11f,
        headerFontSize = template.get("itemHeaderFontSize")?.asFloat ?: 11f,
        itemFontSize = template.get("itemFontSize")?.asFloat ?: 10f,
        grossTotalFontSize = template.get("grossTotalFontSize")?.asFloat ?: 11f,
        discountFontSize = template.get("billDiscountFontSize")?.asFloat ?: 11f,
        netTotalFontSize = template.get("netTotalFontSize")?.asFloat ?: 13f,
        cashFontSize = template.get("cashPaidFontSize")?.asFloat ?: 11f,
        balanceFontSize = template.get("balanceFontSize")?.asFloat ?: 11f,
        billNoGap = template.get("billNoGap2")?.asFloat ?: 5f,
        dateGap = template.get("dateGap2")?.asFloat ?: 5f,
        separatorGap = template.get("separatorGap")?.asFloat ?: 5f,
        headerGap = template.get("headerGap2")?.asFloat ?: 5f,
        itemNameGap = template.get("itemNameGap")?.asFloat ?: 5f,
        itemGap = template.get("itemGap")?.asFloat ?: 5f,
        grossTotalGap = template.get("grossTotalGap")?.asFloat ?: 5f,
        discountGap = template.get("discountGap")?.asFloat ?: 6f,
        netTotalGap = template.get("netTotalGap")?.asFloat ?: 5f,
        cashGapMm = template.get("cashGap")?.asFloat ?: 5f,
        balanceGap = template.get("balanceGap")?.asFloat ?: 5f,
        colPriceMm = template.get("colPriceMm")?.asFloat ?: 30f,
        colQtyMm = template.get("colQtyMm")?.asFloat ?: 50f,
        colAmountMm = template.get("colTotalMm")?.asFloat ?: 70f,
        colHeaderPriceMm = template.get("colHeaderPriceMm")?.asFloat ?: (template.get("colPriceMm")?.asFloat ?: 30f),
        colHeaderQtyMm = template.get("colHeaderQtyMm")?.asFloat ?: (template.get("colQtyMm")?.asFloat ?: 50f),
        colHeaderAmountMm = template.get("colHeaderAmountMm")?.asFloat ?: (template.get("colTotalMm")?.asFloat ?: 70f)
    )
}

private suspend fun fetchImageBillConfig(context: Context, token: String): com.pos.cosmos.models.Config? {
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
            Gson().fromJson(content, com.pos.cosmos.models.Config::class.java)
        } else null
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchConfig error: ${e.message}")
        null
    }
}

private suspend fun fetchNextBillNo(context: Context, token: String, billType: String): Long {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            // Image bills are saved as CASH locally — share the cash sequence.
            com.pos.cosmos.local.LocalBillSaver.nextBillNoForType(context, "CASH")
        } catch (e: Exception) {
            Log.e("ImageBillScreen", "local nextBillNo failed", e)
            0L
        }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getNextBillNo?billType=$billType")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            content.trim().toLongOrNull() ?: 0L
        } else {
            Log.e("ImageBillScreen", "fetchNextBillNo failed: ${connection.responseCode}")
            0L
        }
    } catch (e: Exception) {
        Log.e("ImageBillScreen", "fetchNextBillNo error: ${e.message}")
        0L
    }
}
