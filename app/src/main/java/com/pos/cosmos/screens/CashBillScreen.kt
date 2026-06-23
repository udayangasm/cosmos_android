package com.pos.cosmos.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.utils.LocationHelper
import com.pos.cosmos.utils.ReceiptShareUtils
import com.pos.cosmos.utils.ReceiptPreviewDialog
import java.io.File
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.util.Log

data class BillItem(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    var quantity: Int = 1,
    val unitPrice: Double = sellingPrice,
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val invoiceId: Int = 0,
    val supplierId: Int = 0,
    val totalQty: Int = 0,
    val freeIssueQty: Double = 0.0,
    val isPromoFreeItem: Boolean = false,
    val promotionId: Int = 0,
    // Item-wise returned quantity (credit bills). Kept separate from `amount`,
    // which stays the GROSS line value; returns are netted out at the bill totals
    // and shown as a separate section on the receipt.
    val returnQty: Double = 0.0
) {
    val amount: Double
        get() = quantity * (sellingPrice - discountValue)

    /** Money value of the returned units on this line. */
    val returnAmount: Double
        get() = returnQty * (sellingPrice - discountValue)

    companion object {
        fun fromItemOption(item: ItemOption): BillItem {
            return BillItem(
                id = item.id,
                code = item.code,
                name = item.name,
                sellingPrice = item.sellingPrice,
                invoiceId = item.invoiceId,
                supplierId = item.supplierId,
                totalQty = item.totalQty
            )
        }
    }
}

data class ItemOption(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    val invoiceId: Int = 0,
    val supplierId: Int = 0,
    val totalQty: Int = 0,
    val barcode: String = "",
    val defaultDiscountForPrice: Double = 0.0
)

data class SalesRepData(
    val id: Long,
    val name: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun CashBillScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current

    var cartItems by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var availableItems by remember { mutableStateOf<List<ItemOption>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<ItemOption>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showItemSearch by remember { mutableStateOf(false) }
    var selectedCustomer by remember { mutableStateOf("") }
    var selectedSalesRep by remember { mutableStateOf("") }
    var discountValue by remember { mutableStateOf("0") }
    var cashValue by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var customers by remember { mutableStateOf<List<String>>(emptyList()) }
    var salesReps by remember { mutableStateOf<List<String>>(emptyList()) }
    var editingItemId by remember { mutableStateOf<Int?>(null) }
    var editingQuantity by remember { mutableStateOf("") }
    var hideSalesPersonField by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var lastReceiptPdf by remember { mutableStateOf<File?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }
    var paperWidthMm by remember { mutableFloatStateOf(80f) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var expandedSalesRep by remember { mutableStateOf(false) }
    var filteredSalesReps by remember { mutableStateOf<List<String>>(emptyList()) }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val scope = rememberCoroutineScope()

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
                    }
                }
                withContext(Dispatchers.Default) {
                    val items = fetchAllItems(context, token!!)
                    availableItems = items
                    filteredItems = items
                    customers = fetchCustomers(context, token!!)
                    val reps = fetchSalesReps(context, token!!)
                    salesReps = reps
                    filteredSalesReps = reps

                    // Paper width comes from config. The receipt template itself is loaded
                    // separately in the background (see the template-preload LaunchedEffect
                    // below) so the (potentially network-bound) template fetch never slows
                    // down this initial page load.
                    val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
                    paperWidthMm = if (use58mm) 58f else 80f
                }
                loading = false
            }
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
            val loaded = loadCashBillTemplate(context, t, billType)
            if (loaded != null && billTemplate == null) {
                billTemplate = loaded
                Log.d("CashBillScreen", "Bill template preloaded (type=$billType)")
            } else if (loaded == null) {
                Log.w("CashBillScreen", "Bill template preload failed (type=$billType)")
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
    val netTotal = grossTotal - discount
    val change = (cashValue.toDoubleOrNull() ?: 0.0) - netTotal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cash Bill", color = Color.White) },
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
                    // Search + Scan section (mirrors CreditBillScreen)
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
                                            val matched = availableItems.find {
                                                it.barcode == barcode || it.code == barcode
                                            }
                                            if (matched != null) {
                                                val existingItem = cartItems.find { it.id == matched.id }
                                                cartItems = if (existingItem != null) {
                                                    cartItems.map {
                                                        if (it.id == matched.id) it.copy(quantity = it.quantity + 1)
                                                        else it
                                                    }
                                                } else {
                                                    val applyDefault = config?.setDefaultDiscountForPrice == "Y" &&
                                                        matched.defaultDiscountForPrice > 0 && matched.sellingPrice > 0
                                                    val dv = if (applyDefault) matched.defaultDiscountForPrice else 0.0
                                                    val dp = if (applyDefault) (dv / matched.sellingPrice) * 100.0 else 0.0
                                                    cartItems + BillItem(
                                                        id = matched.id, code = matched.code, name = matched.name,
                                                        sellingPrice = matched.sellingPrice, invoiceId = matched.invoiceId,
                                                        supplierId = matched.supplierId, totalQty = matched.totalQty,
                                                        discount = dv, discountValue = dv, discountPercentage = dp
                                                    )
                                                }
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

                    // Cart Items
                    items(cartItems) { item ->
                        CartItemCard(
                            item = item,
                            onEdit = {
                                editingItemId = item.id
                                editingQuantity = item.quantity.toString()
                            },
                            onDelete = {
                                cartItems = cartItems.filter { it.id != item.id }
                            }
                        )
                    }

                }

                // Bottom Section - Sales Rep, Discount, Totals, Cash, Print (mirrors CreditBillScreen)
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
                        // Sales Rep dropdown — same style as CreditBillScreen
                        if (!hideSalesPersonField) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = selectedSalesRep,
                                    onValueChange = { query ->
                                        selectedSalesRep = query
                                        filteredSalesReps = if (query.isEmpty()) salesReps
                                            else salesReps.filter { it.contains(query, ignoreCase = true) }
                                        expandedSalesRep = filteredSalesReps.isNotEmpty()
                                    },
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
                                            items(filteredSalesReps) { rep ->
                                                Text(
                                                    text = rep,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            selectedSalesRep = rep
                                                            expandedSalesRep = false
                                                        }
                                                        .padding(12.dp),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black
                                                )
                                                Divider()
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Row: Discount %, Discount Value, Invoice Date
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

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Dis. Val",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    OutlinedTextField(
                                        value = String.format("%.2f", discount),
                                        onValueChange = { newValue -> discountValue = newValue },
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

                        // Row: Gross, Net Total
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

                        // Cash Paid
                        TextField(
                            value = cashValue,
                            onValueChange = { cashValue = it },
                            label = { Text("Cash Paid") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Change Display — red background/text when negative
                        if (change != 0.0 && change.isFinite()) {
                            val isNegativeChange = change < 0
                            val cardBg = if (isNegativeChange) Color(0xFFFFF1F0) else Color(0xFFF0FFF4)
                            val textColor = if (isNegativeChange) Color(0xFFFF3B30) else Color(0xFF22C55E)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(cardBg),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg)
                            ) {
                                Text(
                                    "Change: ${String.format("%.2f", change)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Print Button
                        Button(
                            onClick = {
                                if (cartItems.isNotEmpty() && !isSaving) {
                                    isSaving = true
                                    scope.launch {
                                        // Save bill to backend
                                        android.util.Log.d("CashBillScreen", "User clicked 'Print & Save Bill' button")
                                        android.util.Log.d("CashBillScreen", "Cart has ${cartItems.size} items")
                                        val result = withContext(Dispatchers.Default) {
                                            saveBill(
                                                context,
                                                token!!,
                                                cartItems,
                                                selectedCustomer,
                                                selectedSalesRep,
                                                discount,
                                                (cashValue.toDoubleOrNull() ?: 0.0),
                                                "CASH"
                                            )
                                        }

                                        // Check if result is an error (starts with "Failed" or "Exception" or contains "Error:")
                                        if (result != null && (result.startsWith("Failed") || result.startsWith("Exception") || result.contains("Error:"))) {
                                            errorMessage = extractErrorMessage(result)
                                            showErrorDialog = true
                                        } else {
                                            // result contains the bill number
                                            val savedBillNo = result ?: ""
                                            // Last-chance template load: it normally preloads in
                                            // the background, but if the user saved before that
                                            // finished (or it failed), load it now so print /
                                            // preview / share are never silently skipped.
                                            if (billTemplate == null) {
                                                val use58mm = config?.use58mmBillTemplateForAndroidApp == "Y"
                                                val billType = if (use58mm) "android58" else "android"
                                                billTemplate = loadCashBillTemplate(context, token!!, billType)
                                            }
                                            // Print receipt using PDF - use singleton PrinterService
                                            try {
                                                val printerService = PrinterService.getInstance()

                                                // Check if already connected
                                                val isAlreadyConnected = printerService.isConnected()
                                                Log.d("CashBillScreen", "Printer already connected: $isAlreadyConnected")

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
                                                                    Log.d("CashBillScreen", "Connecting to printer: ${printerDevice.name}")
                                                                    printerService.connectDevice(printerDevice)
                                                                }
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.w("CashBillScreen", "Printer auto-connect skipped (preview/share still proceeds): ${e.message}")
                                                    }
                                                }

                                                if (printerService.isConnected()) {
                                                    Log.d("CashBillScreen", "Printer connected, preparing to print")
                                                    // Build receipt data from cached template
                                                    if (billTemplate != null) {
                                                        val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                                        val receiptItems = cartItems.map { ReceiptItem(it.name, it.quantity, it.sellingPrice, it.amount, it.discountValue) }
                                                        val showLineDiscount = config?.showLineDiscountInSmallBills == "Y"
                                                        val totalItemDiscount = cartItems.sumOf { it.quantity * it.discountValue }
                                                        val receiptData = buildReceiptFromTemplate(
                                                            billTemplate!!,
                                                            receiptItems,
                                                            selectedCustomer.ifEmpty { "Walk-in" },
                                                            selectedSalesRep.ifEmpty { "N/A" },
                                                            cartItems.sumOf { it.amount },
                                                            discount,
                                                            netTotal,
                                                            (cashValue.toDoubleOrNull() ?: 0.0),
                                                            savedBillNo
                                                        ).copy(
                                                            showLineDiscount = showLineDiscount,
                                                            totalItemDiscount = totalItemDiscount
                                                        )

                                                        Log.d("CashBillScreen", "Printing receipt...")
                                                        val printedFile = printerService.printPdfReceiptAndReturnFile(pdfGenerator, receiptData)
                                                        Log.d("CashBillScreen", "Print completed - keeping connection alive")
                                                        // DO NOT disconnect - keep connection for next print
                                                        if (printedFile != null) {
                                                            lastReceiptPdf = printedFile
                                                            if (config?.showReceiptPrintPreviewAndroidApp == "Y") {
                                                                previewPdfFile = printedFile
                                                            }
                                                        }
                                                    } else {
                                                        Log.e("CashBillScreen", "Bill template not available, cannot print")
                                                    }
                                                } else {
                                                    Log.e("CashBillScreen", "Printer not connected. Please connect from Printer screen first.")
                                                    // Even without a printer, generate PDF for preview/share if enabled
                                                    val showPreview = config?.showReceiptPrintPreviewAndroidApp == "Y"
                                                    val enableShare = config?.enableShareAndroidApp == "Y"
                                                    if (billTemplate != null && (showPreview || enableShare)) {
                                                        val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
                                                        val receiptItems = cartItems.map { ReceiptItem(it.name, it.quantity, it.sellingPrice, it.amount, it.discountValue) }
                                                        val showLineDiscount = config?.showLineDiscountInSmallBills == "Y"
                                                        val totalItemDiscount = cartItems.sumOf { it.quantity * it.discountValue }
                                                        val receiptData = buildReceiptFromTemplate(
                                                            billTemplate!!,
                                                            receiptItems,
                                                            selectedCustomer.ifEmpty { "Walk-in" },
                                                            selectedSalesRep.ifEmpty { "N/A" },
                                                            cartItems.sumOf { it.amount },
                                                            discount,
                                                            netTotal,
                                                            (cashValue.toDoubleOrNull() ?: 0.0),
                                                            savedBillNo
                                                        ).copy(
                                                            showLineDiscount = showLineDiscount,
                                                            totalItemDiscount = totalItemDiscount
                                                        )
                                                        val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
                                                        lastReceiptPdf = pdfFile
                                                        if (showPreview) {
                                                            previewPdfFile = pdfFile
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("CashBillScreen", "Print error: ${e.message}", e)
                                                e.printStackTrace()
                                            }

                                            // Reset form
                                            cartItems = emptyList()
                                            selectedCustomer = ""
                                            selectedSalesRep = ""
                                            discountValue = "0"
                                            cashValue = ""
                                            successMessage = "Bill #$savedBillNo saved successfully!"
                                            showSuccessDialog = true
                                        }
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            enabled = cartItems.isNotEmpty() && netTotal > 0 && change >= 0 && !isSaving
                        ) {
                            Text(if (isSaving) "Saving..." else "Print & Save Bill")
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
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(filteredItems.take(20)) { item ->
                            ItemRow(
                                item = item,
                                onClick = {
                                    val existingItem = cartItems.find { it.id == item.id }
                                    cartItems = if (existingItem != null) {
                                        cartItems.map {
                                            if (it.id == item.id) it.copy(quantity = it.quantity + 1)
                                            else it
                                        }
                                    } else {
                                        val applyDefault = config?.setDefaultDiscountForPrice == "Y" &&
                                            item.defaultDiscountForPrice > 0 && item.sellingPrice > 0
                                        val dv = if (applyDefault) item.defaultDiscountForPrice else 0.0
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

    // Quantity Edit Dialog
    if (editingItemId != null) {
        AlertDialog(
            onDismissRequest = { editingItemId = null },
            title = { Text("Edit Quantity") },
            text = {
                TextField(
                    value = editingQuantity,
                    onValueChange = { editingQuantity = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = editingQuantity.toIntOrNull() ?: 1
                        if (qty > 0) {
                            cartItems = cartItems.map {
                                if (it.id == editingItemId) it.copy(quantity = qty)
                                else it
                            }
                        }
                        editingItemId = null
                        editingQuantity = ""
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(onClick = { editingItemId = null }) {
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
            title = { Text("Error Saving Bill", color = Color.Red) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun CartItemCard(
    item: BillItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showFreeIssue: Boolean = false,
    itemNumber: Int = 0
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (itemNumber > 0) "$itemNumber. ${item.name}" else item.name,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black
                )
                if (item.isPromoFreeItem) {
                    Text("FREE - Promotion", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Text("${item.code} | Qty: ${item.quantity} x ${String.format("%.2f", item.sellingPrice)}", fontSize = 12.sp, color = Color.Gray)
                // Show discount if present
                if (item.discountValue > 0 || item.discountPercentage > 0) {
                    val discountText = if (item.discountPercentage > 0) {
                        "Discount: ${String.format("%.2f", item.discountPercentage)}% (${String.format("%.2f", item.discountValue)})"
                    } else {
                        "Discount: ${String.format("%.2f", item.discountValue)}"
                    }
                    Text(discountText, fontSize = 11.sp, color = Color(0xFFFF9800))
                }
                // Show free issue if enabled and present
                if (showFreeIssue && item.freeIssueQty > 0) {
                    Text(
                        "Free Qty: ${if (item.freeIssueQty % 1 == 0.0) item.freeIssueQty.toInt().toString() else String.format("%.2f", item.freeIssueQty)}",
                        fontSize = 11.sp,
                        color = Color(0xFF9C27B0)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Rs. ${String.format("%.2f", item.amount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
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

@Composable
fun ItemRow(
    item: ItemOption,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${item.code} - ${String.format("%.2f", item.sellingPrice)}", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
fun TotalRow(
    label: String,
    amount: String,
    isBold: Boolean = false,
    onEdit: ((String) -> Unit)? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    textColor: Color = Color.Black
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit?.invoke(amount) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = fontSize,
            color = textColor
        )
        Text(
            amount,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = fontSize,
            color = textColor
        )
    }
}

private suspend fun fetchAllItems(context: Context, token: String): List<ItemOption> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val rows = com.pos.cosmos.local.CosmosDatabase.get(context).itemDao().all()
            rows.map {
                ItemOption(
                    id = it.id.toInt(),
                    code = it.itemCode.orEmpty(),
                    name = it.name.orEmpty(),
                    sellingPrice = it.sellingPrice,
                    invoiceId = 0,
                    supplierId = 0,
                    totalQty = it.stockQty.toInt()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("CashBillScreen", "local fetchAllItems failed", e)
            emptyList()
        }
    }
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllBillItems?billType=CASH")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("CashBillScreen", "getAllBillItems response: $content")

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
                    val itemBarcode = itemMap["barcode"] as? String ?: ""
                    val sellingPrice = (itemMap["sellingPrice"] as? Number)?.toDouble() ?: 0.0

                    // Extract first invoice with qty > 0 and its supplier from billSupplierList
                    var invoiceId = 0
                    var supplierId = 0
                    var totalQty = 0
                    var defaultDiscount = 0.0

                    @Suppress("UNCHECKED_CAST")
                    val billSupplierList = itemMap["billSupplierList"] as? List<Map<String, Any>>
                    if (!billSupplierList.isNullOrEmpty()) {
                        // Find first supplier that has an invoice with qty > 0
                        for (supplierMap in billSupplierList) {
                            @Suppress("UNCHECKED_CAST")
                            val billInvoiceList = supplierMap["billInvoiceList"] as? List<Map<String, Any>>
                            if (!billInvoiceList.isNullOrEmpty()) {
                                // Find first invoice with qty > 0
                                for (invoiceMap in billInvoiceList) {
                                    val invoiceQty = (invoiceMap["qty"] as? Number)?.toInt() ?: 0
                                    if (invoiceQty > 0) {
                                        invoiceId = (invoiceMap["invoiceId"] as? Number)?.toInt() ?: 0
                                        totalQty = invoiceQty
                                        supplierId = (supplierMap["supplierId"] as? Number)?.toInt() ?: 0
                                        defaultDiscount = (invoiceMap["defaultDiscountForInvoiceSellingPrice"] as? Number)?.toDouble() ?: 0.0
                                        break
                                    }
                                }
                                if (invoiceId > 0) break  // Exit outer loop if we found a valid invoice
                            }
                        }
                    }

                    android.util.Log.d("CashBillScreen", "Parsed item: $itemCode ($itemId) - invoiceId=$invoiceId, supplierId=$supplierId, totalQty=$totalQty (qty>0), defaultDiscount=$defaultDiscount")

                    ItemOption(
                        id = itemId,
                        code = itemCode,
                        name = itemName,
                        sellingPrice = sellingPrice,
                        invoiceId = invoiceId,
                        supplierId = supplierId,
                        totalQty = totalQty,
                        barcode = itemBarcode,
                        defaultDiscountForPrice = defaultDiscount
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CashBillScreen", "Error parsing item: ${e.message}")
                    null
                }
            }
        } else {
            android.util.Log.e("CashBillScreen", "getAllBillItems request failed with response code: ${connection.responseCode}")
            emptyList()
        }
    } catch (e: Exception) {
        android.util.Log.e("CashBillScreen", "Exception in fetchAllItems: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun fetchCustomers(context: Context, token: String): List<String> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.CosmosDatabase.get(context).customerDao().all().mapNotNull { it.name }
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
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>
            list?.mapNotNull { (it["name"] as? String) } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun fetchSalesReps(context: Context, token: String): List<String> {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            com.pos.cosmos.local.CosmosDatabase.get(context).salesRefDao().all().mapNotNull { it.name }
        } catch (e: Exception) { emptyList() }
    }
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
            list?.mapNotNull { (it["name"] as? String) } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
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
        } catch (e: Exception) {
            android.util.Log.e("CashBillScreen", "local fetchConfig failed", e)
            null
        }
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

private suspend fun saveBill(
    context: Context,
    token: String,
    items: List<BillItem>,
    customer: String,
    salesRep: String,
    discount: Double,
    cashPaid: Double,
    billType: String
): String? {
    val grossTotal = items.sumOf { it.amount }
    val netTotal = grossTotal - discount
    val billNo = System.currentTimeMillis() / 1000

    // ── Local-first path (mirrors desktop CashBillForm.SaveBillLocal) ──
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val localBillNo = com.pos.cosmos.local.LocalBillSaver.saveBill(
                context = context,
                billNo = 0,
                billType = "CASH",
                customerId = 0,
                cashCustomerName = customer,
                salesRefId = 0,
                total = netTotal,
                discount = 0.0,
                discountValue = discount,
                cash = cashPaid,
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
            android.util.Log.d("CashBillScreen", "Bill $localBillNo queued locally (offline).")
            localBillNo.toString()
        } catch (e: Exception) {
            val errorMsg = "Failed to save bill locally: ${e.message}"
            android.util.Log.e("CashBillScreen", errorMsg, e)
            errorMsg
        }
    }

    return try {
        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        val billData = jsonObjectOf(
            "billItems" to items.map { item ->
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
                    "isBox" to false
                )
            },
            "billNo" to billNo,
            "cash" to cashPaid,
            "billType" to billType,
            "salesRefId" to 0,
            "discount" to discount,
            "discountValue" to discount,
            "discountPercentage" to 0.0,
            "customerId" to 0,
            "paidAmount" to cashPaid,
            "total" to netTotal,
            "billingDate" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()),
            "id" to 0,
            "customerName" to customer,
            "salesRefName" to salesRep,
            "userEvent" to jsonObjectOf(
                "event" to "Save small Cash Bill $billNo",
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "userName" to "",
                "eventTime" to "",
                "mapLocations" to null
            ),
            "paymentMethod" to "CASH",
            "paymentDetails" to ""
        )

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBill")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = billData.toString()
        android.util.Log.d("CashBillScreen", "=== BILL SAVE DETAILS ===")
        android.util.Log.d("CashBillScreen", "Bill Number: $billNo")
        android.util.Log.d("CashBillScreen", "Items Count: ${items.size}")
        android.util.Log.d("CashBillScreen", "Gross Total: $grossTotal")
        android.util.Log.d("CashBillScreen", "Discount: $discount")
        android.util.Log.d("CashBillScreen", "Net Total: $netTotal")
        android.util.Log.d("CashBillScreen", "Cash Paid: $cashPaid")
        android.util.Log.d("CashBillScreen", "=== FULL PAYLOAD ===")
        android.util.Log.d("CashBillScreen", jsonString)
        android.util.Log.d("CashBillScreen", "=== SENDING TO: ${ApiConfig.BASE_URL_8080}/bill/saveBill ===")
        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("CashBillScreen", "Bill saved successfully. Response: $responseBody")
            billNo.toString()  // Return bill number on success
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            val errorMsg = "Failed to save bill. Response code: ${connection.responseCode}\nError: $errorResponse"
            android.util.Log.e("CashBillScreen", errorMsg)
            errorMsg
        }
    } catch (e: Exception) {
        val errorMsg = "Exception while saving bill: ${e.message}\n${e.stackTraceToString()}"
        android.util.Log.e("CashBillScreen", errorMsg)
        errorMsg
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
    val change = cashPaid - netTotal

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
 * Loads the cash-bill receipt template, cache-first with a network fallback. Used both by
 * the background preload effect and as a last-chance load in the save flow so that the
 * print-preview / share features never silently break due to a missing template.
 */
private suspend fun loadCashBillTemplate(
    context: android.content.Context,
    token: String,
    billType: String
): JsonObject? {
    var template = BillTemplateCache.getTemplate(context, billType)
    if (template == null) {
        template = fetchSmallBillTemplateCash(token, billType)
        if (template != null) {
            BillTemplateCache.saveTemplate(context, template, billType)
        }
    }
    return template
}

private suspend fun fetchSmallBillTemplateCash(
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
                        Log.d("CashBillScreen", "Fetched '$targetBillType' bill template successfully")
                        smallTemplate = template
                        break
                    }
                }
                if (smallTemplate == null && targetBillType != "android") {
                    for (i in 0 until allTemplates.size()) {
                        val template = allTemplates.get(i).asJsonObject
                        val billType = template.get("billType")?.asString
                        if (billType == "android") {
                            Log.d("CashBillScreen", "Falling back to 'android' template")
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
                            Log.d("CashBillScreen", "Falling back to 'small' template")
                            smallTemplate = template
                            break
                        }
                    }
                }
            }
            smallTemplate
        } else {
            Log.e("CashBillScreen", "Failed to fetch bill templates: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("CashBillScreen", "Error fetching bill template: ${e.message}")
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
                val jsonObject = com.google.gson.JsonParser.parseString(errorPart).asJsonObject
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
