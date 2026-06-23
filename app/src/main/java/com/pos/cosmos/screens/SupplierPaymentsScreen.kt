package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.models.SupplierInvoice
import com.pos.cosmos.models.SupplierInvoiceFull
import com.pos.cosmos.models.SupplierPayment
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.utils.PaymentReceiptLine
import com.pos.cosmos.utils.ReceiptPreviewDialog
import com.pos.cosmos.utils.ReceiptShareUtils
import com.pos.cosmos.utils.producePaymentReceipt
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierPaymentsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    // Supplier selection
    var selectedSupplier by remember { mutableStateOf("") }
    var selectedSupplierId by remember { mutableStateOf(0) }
    var allSuppliers by remember { mutableStateOf<List<SupplierData>>(emptyList()) }
    var filteredSuppliers by remember { mutableStateOf<List<SupplierData>>(emptyList()) }
    var expandedSuppliers by remember { mutableStateOf(false) }

    // Supplier-level remaining total = total invoiced - sum of all payments
    var remainingTotal by remember { mutableStateOf(0.0) }

    // Invoice selection
    var invoices by remember { mutableStateOf<List<SupplierInvoice>>(emptyList()) }
    var filteredInvoices by remember { mutableStateOf<List<SupplierInvoice>>(emptyList()) }
    var expandedInvoices by remember { mutableStateOf(false) }
    var selectedInvoice by remember { mutableStateOf("") }
    var selectedInvoiceId by remember { mutableStateOf(0) }

    // Invoice amounts
    var billAmount by remember { mutableStateOf(0.0) }
    var returnAmount by remember { mutableStateOf(0.0) }
    var netAmount by remember { mutableStateOf(0.0) }
    var paidAmount by remember { mutableStateOf(0.0) }
    var balanceAmount by remember { mutableStateOf(0.0) }

    // Payment history (all supplier payments)
    var paymentHistory by remember { mutableStateOf<List<SupplierPayment>>(emptyList()) }

    var loading by remember { mutableStateOf(false) }
    var canDelete by remember { mutableStateOf(false) }

    // History search (date range + explicit search, like Customer Payments)
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var historySearched by remember { mutableStateOf(false) }

    // Dialog states
    var displayCashPayment by remember { mutableStateOf(false) }
    var displayChequePayment by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<SupplierPayment?>(null) }
    var selectedHistoryPayment by remember { mutableStateOf<SupplierPayment?>(null) }

    // Cash inputs
    var cashAmount by remember { mutableStateOf("") }
    var cashTRN by remember { mutableStateOf("") }

    // Cheque inputs
    var chequeAmount by remember { mutableStateOf("") }
    var chequeNumber by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bankAndBranch by remember { mutableStateOf("") }
    var chequeTRN by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf<Long?>(null) }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Receipt preview / share state (gated by showReceiptPrintPreviewAndroidApp / enableShareAndroidApp)
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }

    // Generate a payment receipt and route it to print + preview + share per config.
    fun emitPaymentReceipt(title: String, lines: List<PaymentReceiptLine>) {
        val tk = token ?: return
        val showPreview = config?.showReceiptPrintPreviewAndroidApp == "Y"
        val enableShare = config?.enableShareAndroidApp == "Y"
        scope.launch {
            val (pdf, tmpl) = withContext(Dispatchers.IO) {
                producePaymentReceipt(
                    context, tk, billTemplate,
                    config?.use58mmBillTemplateForAndroidApp == "Y",
                    showPreview, enableShare, title, lines
                )
            }
            if (tmpl != null) billTemplate = tmpl
            if (pdf != null) {
                if (showPreview) previewPdfFile = pdf
                else if (enableShare) ReceiptShareUtils.sharePdf(context, pdf)
            }
        }
    }

    fun refreshSupplierRemaining() {
        if (selectedSupplierId <= 0) return
        scope.launch {
            withContext(Dispatchers.IO) {
                val invoicedTotal = fetchSupplierInvoicedTotal(token!!, selectedSupplierId)
                val payments = fetchSupplierPayments(token!!, selectedSupplierId)
                // Remaining total needs the full payment sum, but the displayed history
                // list is only populated via the Search button below.
                remainingTotal = invoicedTotal - payments.sumOf { it.paidAmount }
            }
        }
    }

    // Load payment history filtered by the chosen date range. With a supplier selected it
    // shows that supplier's payments; with none selected it shows all suppliers' payments
    // (like the Customer Payments screen). Loads only when the user taps Search.
    fun runHistorySearch() {
        val tk = token ?: return
        val supId = selectedSupplierId
        scope.launch {
            val payments = withContext(Dispatchers.IO) {
                if (supId > 0) fetchSupplierPayments(tk, supId) else fetchAllSupplierPayments(tk)
            }
            paymentHistory = payments.filter { withinDateRange(it.paymentDate, fromDate, toDate) }
            historySearched = true
        }
    }

    fun refreshInvoicePaid() {
        if (selectedInvoiceId <= 0) return
        scope.launch {
            withContext(Dispatchers.IO) {
                val paid = fetchInvoicePaidTotal(token!!, selectedInvoiceId)
                paidAmount = paid
                balanceAmount = netAmount - paidAmount
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            withContext(Dispatchers.IO) {
                val suppliers = fetchAllSuppliersForPayments(token!!)
                allSuppliers = suppliers
                filteredSuppliers = suppliers
                canDelete = fetchDeletePermission(token!!)
                // Config may be null if the user came straight to this screen — fetch it so the
                // receipt preview / share flags are honoured.
                val appConfig = fetchSupplierAppConfig(token!!)
                if (appConfig != null) {
                    appViewModel.setConfig(appConfig)
                }
                // Preload the bill template so the company header / logo are ready for receipts.
                if (appConfig?.showReceiptPrintPreviewAndroidApp == "Y" || appConfig?.enableShareAndroidApp == "Y") {
                    billTemplate = com.pos.cosmos.utils.loadBillTemplateForPaymentReceipt(
                        context, token!!, appConfig?.use58mmBillTemplateForAndroidApp == "Y"
                    )
                }
            }
            loading = false
        }
    }

    fun filterSuppliers(query: String) {
        selectedSupplier = query
        if (query.isEmpty()) {
            filteredSuppliers = allSuppliers
            expandedSuppliers = false
            // Clearing the supplier name clears everything derived from the selection.
            selectedSupplierId = 0
            selectedInvoice = ""
            selectedInvoiceId = 0
            invoices = emptyList()
            filteredInvoices = emptyList()
            expandedInvoices = false
            remainingTotal = 0.0
            billAmount = 0.0
            returnAmount = 0.0
            netAmount = 0.0
            paidAmount = 0.0
            balanceAmount = 0.0
            paymentHistory = emptyList()
            historySearched = false
            fromDate = null
            toDate = null
            successMessage = ""
            errorMessage = ""
        } else {
            filteredSuppliers = allSuppliers.filter {
                it.name?.contains(query, ignoreCase = true) ?: false
            }
            expandedSuppliers = filteredSuppliers.isNotEmpty()
        }
    }

    fun onSelectSupplier(supplier: SupplierData) {
        selectedSupplier = supplier.name ?: ""
        selectedSupplierId = supplier.id
        expandedSuppliers = false

        // Reset invoice + amounts
        selectedInvoice = ""
        selectedInvoiceId = 0
        invoices = emptyList()
        filteredInvoices = emptyList()
        billAmount = 0.0
        returnAmount = 0.0
        netAmount = 0.0
        paidAmount = 0.0
        balanceAmount = 0.0

        // Reset history — it loads only when the user taps Search.
        paymentHistory = emptyList()
        historySearched = false
        fromDate = null
        toDate = null

        scope.launch {
            withContext(Dispatchers.IO) {
                val invoicedTotal = fetchSupplierInvoicedTotal(token!!, supplier.id)
                val payments = fetchSupplierPayments(token!!, supplier.id)
                remainingTotal = invoicedTotal - payments.sumOf { it.paidAmount }
                val invoiceList = fetchInvoicesBySupplier(token!!, supplier.id)
                invoices = invoiceList
                filteredInvoices = invoiceList
            }
        }
    }

    fun filterInvoices(query: String) {
        selectedInvoice = query
        if (query.isEmpty()) {
            filteredInvoices = invoices
            expandedInvoices = false
        } else {
            filteredInvoices = invoices.filter {
                it.invoiceName.contains(query, ignoreCase = true)
            }
            expandedInvoices = filteredInvoices.isNotEmpty()
        }
    }

    fun onSelectInvoice(invoice: SupplierInvoice) {
        selectedInvoice = invoice.invoiceName
        selectedInvoiceId = invoice.id
        expandedInvoices = false
        billAmount = 0.0
        paidAmount = 0.0

        scope.launch {
            withContext(Dispatchers.IO) {
                val full = fetchSelectedInvoice(token!!, invoice.id)
                val bill = full?.invoiceDetails?.sumOf { it.qty * it.unitCost } ?: 0.0
                billAmount = bill
                netAmount = bill - returnAmount
                balanceAmount = netAmount
                val paid = fetchInvoicePaidTotal(token!!, invoice.id)
                paidAmount = paid
                balanceAmount = netAmount - paid
            }
        }
    }

    fun clearPaymentFields() {
        cashAmount = ""
        cashTRN = ""
        chequeAmount = ""
        chequeNumber = ""
        accountNumber = ""
        bankAndBranch = ""
        chequeTRN = ""
        chequeDate = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supplier Payments", color = Color.White) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF0F3460))
                    .padding(16.dp)
            ) {
                // Supplier selection
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Supplier", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = selectedSupplier,
                                    onValueChange = { filterSuppliers(it) },
                                    label = { Text("Select Supplier") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSuppliers = true },
                                    singleLine = true
                                )
                                if (expandedSuppliers && filteredSuppliers.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                            items(filteredSuppliers) { supplier ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onSelectSupplier(supplier) }
                                                        .padding(12.dp)
                                                ) {
                                                    Text(
                                                        text = supplier.name ?: "",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (!supplier.address.isNullOrEmpty()) {
                                                        Text(
                                                            text = supplier.address,
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
                        }
                    }
                }

                // Success / Error messages
                if (successMessage.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF34C759).copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = successMessage,
                                color = Color(0xFF34C759),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                if (errorMessage.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE94560).copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = errorMessage,
                                color = Color(0xFFE94560),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Remaining total + invoice selector
                if (selectedSupplierId > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Remaining Total Payments", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                                Text(
                                    String.format(Locale.US, "%.2f", remainingTotal),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingTotal > 0) Color(0xFFE94560) else Color(0xFF34C759)
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Invoice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    TextField(
                                        value = selectedInvoice,
                                        onValueChange = { filterInvoices(it) },
                                        label = { Text("Select Invoice") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedInvoices = invoices.isNotEmpty() },
                                        singleLine = true
                                    )
                                    if (expandedInvoices && filteredInvoices.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 48.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White)
                                        ) {
                                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                                items(filteredInvoices) { invoice ->
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { onSelectInvoice(invoice) }
                                                            .padding(12.dp)
                                                    ) {
                                                        Text(
                                                            text = invoice.invoiceName,
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
                        }
                    }
                }

                // Invoice amounts + payment buttons
                if (selectedInvoiceId > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                BillDetailsRow("Bill Amount", String.format(Locale.US, "%.2f", billAmount), Color(0xFF333333))
                                BillDetailsRow("Return", String.format(Locale.US, "%.2f", returnAmount), Color(0xFF333333))
                                BillDetailsRow("Net", String.format(Locale.US, "%.2f", netAmount), Color(0xFF333333))
                                BillDetailsRow("Paid", String.format(Locale.US, "%.2f", paidAmount), Color(0xFF34C759))
                                BillDetailsRow(
                                    "Balance",
                                    String.format(Locale.US, "%.2f", balanceAmount),
                                    if (balanceAmount <= 0) Color(0xFF34C759) else Color(0xFFE94560)
                                )
                            }
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Button(
                                    onClick = { displayCashPayment = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                ) { Text("Cash Payment") }
                                Button(
                                    onClick = { displayChequePayment = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                                ) { Text("Cheque Payment") }
                            }
                        }
                    }
                }

                // Payment history — always shown; searches all suppliers when none is selected.
                item {
                    Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Payment History", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = fromDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: "",
                                        onValueChange = {},
                                        label = { Text("From") },
                                        readOnly = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A2E), fontWeight = FontWeight.SemiBold),
                                        colors = supplierDarkFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 4.dp)
                                            .clickable { showFromPicker = true },
                                        trailingIcon = {
                                            IconButton(onClick = { showFromPicker = true }) {
                                                Icon(Icons.Default.DateRange, contentDescription = "From date", tint = Color(0xFF1A1A2E))
                                            }
                                        }
                                    )
                                    OutlinedTextField(
                                        value = toDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: "",
                                        onValueChange = {},
                                        label = { Text("To") },
                                        readOnly = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A2E), fontWeight = FontWeight.SemiBold),
                                        colors = supplierDarkFieldColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 4.dp)
                                            .clickable { showToPicker = true },
                                        trailingIcon = {
                                            IconButton(onClick = { showToPicker = true }) {
                                                Icon(Icons.Default.DateRange, contentDescription = "To date", tint = Color(0xFF1A1A2E))
                                            }
                                        }
                                    )
                                    Button(
                                        onClick = { runHistorySearch() },
                                        modifier = Modifier
                                            .weight(0.5f)
                                            .align(Alignment.CenterVertically)
                                    ) { Text("Search") }
                                }

                                if (historySearched && paymentHistory.isEmpty()) {
                                    Text(
                                        "No payments found",
                                        fontSize = 12.sp,
                                        color = Color(0xFF333333),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(paymentHistory) { payment ->
                        val invoiceName = invoices.firstOrNull { it.id == payment.invoiceId }?.invoiceName
                            ?: "#${payment.invoiceId}"
                        val supplierName = allSuppliers.firstOrNull { it.id == payment.supplierId }?.name ?: ""
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { selectedHistoryPayment = payment },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        if (supplierName.isNotBlank()) {
                                            Text(supplierName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A1A2E))
                                        }
                                        Text("Invoice: $invoiceName", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1A1A2E))
                                        Text(payment.paymentDate.orEmpty().replace('T', ' ').take(16), fontSize = 10.sp, color = Color(0xFF555555))
                                    }
                                    Text(String.format(Locale.US, "%.2f", payment.paidAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A2E))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val typeText = if (!payment.chequeNumber.isNullOrBlank()) "Cheque" else "Cash"
                                val detail = if (!payment.chequeNumber.isNullOrBlank()) payment.chequeNumber!! else payment.trn.orEmpty().ifBlank { "Cash" }
                                Text("$typeText - $detail", fontSize = 10.sp, color = Color(0xFF555555))
                            }
                        }
                    }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    // Cash dialog
    if (displayCashPayment) {
        val entered = cashAmount.toDoubleOrNull() ?: 0.0
        val overpay = entered > balanceAmount + 0.005
        AlertDialog(
            onDismissRequest = { displayCashPayment = false },
            title = { Text("Cash Payment") },
            text = {
                Column {
                    TextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it },
                        label = { Text("Amount") },
                        isError = overpay,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Balance: ${String.format(Locale.US, "%.2f", balanceAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (overpay) {
                        Text(
                            "Amount cannot exceed the invoice balance.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = cashTRN,
                        onValueChange = { cashTRN = it },
                        label = { Text("TRN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = entered > 0.0 && !overpay,
                    onClick = {
                        val amount = cashAmount.toDoubleOrNull() ?: 0.0
                        val trnVal = cashTRN
                        val supId = selectedSupplierId
                        val invId = selectedInvoiceId
                        val supName = selectedSupplier
                        val invName = selectedInvoice
                        val preBalance = balanceAmount
                        displayCashPayment = false
                        clearPaymentFields()
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val payment = SupplierPayment(
                                    id = 0,
                                    supplierId = supId,
                                    invoiceId = invId,
                                    paidAmount = amount,
                                    type = "cash",
                                    chequeNumber = "",
                                    chequeDate = null,
                                    bank = "",
                                    trn = trnVal,
                                    paymentDate = nowIso(),
                                    active = "Y"
                                )
                                addSupplierPayment(token!!, payment)
                            }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refreshSupplierRemaining()
                                refreshInvoicePaid()
                                if (historySearched) runHistorySearch()
                                val lines = buildList {
                                    if (supName.isNotBlank()) add(PaymentReceiptLine("Supplier:", supName))
                                    if (invName.isNotBlank()) add(PaymentReceiptLine("Invoice:", invName))
                                    add(PaymentReceiptLine("Payment Type:", "Cash"))
                                    if (trnVal.isNotBlank()) add(PaymentReceiptLine("TRN:", trnVal))
                                    add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", amount), emphasize = true))
                                    add(PaymentReceiptLine("Balance:", String.format(Locale.US, "%.2f", (preBalance - amount).coerceAtLeast(0.0)), bold = true))
                                }
                                emitPaymentReceipt("SUPPLIER PAYMENT RECEIPT", lines)
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { displayCashPayment = false; clearPaymentFields() }) { Text("Cancel") }
            }
        )
    }

    // Cheque dialog
    if (displayChequePayment) {
        val entered = chequeAmount.toDoubleOrNull() ?: 0.0
        val overpay = entered > balanceAmount + 0.005
        var showDatePicker by remember { mutableStateOf(false) }
        val chequeDateText = chequeDate?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
        } ?: ""
        val focusManager = LocalFocusManager.current
        val moveNext = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

        AlertDialog(
            onDismissRequest = { displayChequePayment = false },
            title = { Text("Cheque Payment") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    TextField(
                        value = chequeAmount,
                        onValueChange = { chequeAmount = it },
                        label = { Text("Amount") },
                        isError = overpay,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Balance: ${String.format(Locale.US, "%.2f", balanceAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (overpay) {
                        Text(
                            "Amount cannot exceed the invoice balance.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = chequeNumber,
                        onValueChange = { chequeNumber = it },
                        label = { Text("Cheque Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = bankAndBranch,
                        onValueChange = { bankAndBranch = it },
                        label = { Text("Bank and Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = chequeTRN,
                        onValueChange = { chequeTRN = it },
                        label = { Text("TRN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chequeDateText,
                        onValueChange = { },
                        label = { Text("Cheque Date") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Select date")
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = entered > 0.0 && !overpay,
                    onClick = {
                        val amount = chequeAmount.toDoubleOrNull() ?: 0.0
                        val cheqNum = chequeNumber
                        val bank = bankAndBranch
                        val trnVal = chequeTRN
                        val cheqDateMillis = chequeDate
                        val supId = selectedSupplierId
                        val invId = selectedInvoiceId
                        val supName = selectedSupplier
                        val invName = selectedInvoice
                        val preBalance = balanceAmount
                        displayChequePayment = false
                        clearPaymentFields()
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                val cheqDateIso = cheqDateMillis?.let {
                                    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                                    fmt.timeZone = TimeZone.getTimeZone("UTC")
                                    fmt.format(Date(it))
                                }
                                val payment = SupplierPayment(
                                    id = 0,
                                    supplierId = supId,
                                    invoiceId = invId,
                                    paidAmount = amount,
                                    type = "cheque",
                                    chequeNumber = cheqNum,
                                    chequeDate = cheqDateIso,
                                    bank = bank,
                                    trn = trnVal,
                                    paymentDate = nowIso(),
                                    active = "Y"
                                )
                                addSupplierPayment(token!!, payment)
                            }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refreshSupplierRemaining()
                                refreshInvoicePaid()
                                if (historySearched) runHistorySearch()
                                val lines = buildList {
                                    if (supName.isNotBlank()) add(PaymentReceiptLine("Supplier:", supName))
                                    if (invName.isNotBlank()) add(PaymentReceiptLine("Invoice:", invName))
                                    add(PaymentReceiptLine("Payment Type:", "Cheque"))
                                    if (cheqNum.isNotBlank()) add(PaymentReceiptLine("Cheque No:", cheqNum))
                                    cheqDateMillis?.let {
                                        add(PaymentReceiptLine("Cheque Date:", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))))
                                    }
                                    if (bank.isNotBlank()) add(PaymentReceiptLine("Bank:", bank))
                                    if (trnVal.isNotBlank()) add(PaymentReceiptLine("TRN:", trnVal))
                                    add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", amount), emphasize = true))
                                    add(PaymentReceiptLine("Balance:", String.format(Locale.US, "%.2f", (preBalance - amount).coerceAtLeast(0.0)), bold = true))
                                }
                                emitPaymentReceipt("SUPPLIER PAYMENT RECEIPT", lines)
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { displayChequePayment = false; clearPaymentFields() }) { Text("Cancel") }
            }
        )

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = chequeDate ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        chequeDate = datePickerState.selectedDateMillis
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }

    // Delete confirmation
    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            containerColor = Color.White,
            titleContentColor = Color(0xFF1A1A2E),
            textContentColor = Color(0xFF1A1A2E),
            title = { Text("Delete Payment", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E)) },
            text = { Text("Are you sure to delete this payment of ${String.format(Locale.US, "%.2f", payment.paidAmount)}?", color = Color(0xFF1A1A2E)) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                    onClick = {
                        val id = payment.id
                        paymentToDelete = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { deleteSupplierPayment(token!!, id) }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refreshSupplierRemaining()
                                refreshInvoicePaid()
                                if (historySearched) runHistorySearch()
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { paymentToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Payment-history detail popup. Tapping a history row opens this; it offers Print
    // (receipt preview / share per config) and, with permission, Delete (which asks for
    // confirmation via the dialog above).
    selectedHistoryPayment?.let { p ->
        val supplierName = allSuppliers.firstOrNull { it.id == p.supplierId }?.name ?: ""
        val invoiceName = invoices.firstOrNull { it.id == p.invoiceId }?.invoiceName ?: "#${p.invoiceId}"
        val typeDisplay = if (!p.chequeNumber.isNullOrBlank()) "Cheque" else "Cash"
        AlertDialog(
            onDismissRequest = { selectedHistoryPayment = null },
            containerColor = Color.White,
            titleContentColor = Color(0xFF1A1A2E),
            textContentColor = Color(0xFF1A1A2E),
            title = { Text("Payment Details", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (supplierName.isNotBlank()) SupplierDetailRow("Supplier", supplierName)
                    SupplierDetailRow("Invoice", invoiceName)
                    SupplierDetailRow("Payment Date", p.paymentDate.orEmpty().replace('T', ' ').take(16))
                    SupplierDetailRow("Amount", String.format(Locale.US, "%.2f", p.paidAmount))
                    SupplierDetailRow("Type", typeDisplay)
                    if (!p.chequeNumber.isNullOrBlank()) SupplierDetailRow("Cheque Number", p.chequeNumber!!)
                    if (!p.chequeDate.isNullOrBlank()) SupplierDetailRow("Cheque Date", p.chequeDate!!.replace('T', ' ').take(16))
                    if (p.bank.isNotBlank()) SupplierDetailRow("Bank", p.bank)
                    if (!p.trn.isNullOrBlank()) SupplierDetailRow("TRN", p.trn)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        onClick = {
                            val lines = buildList {
                                if (supplierName.isNotBlank()) add(PaymentReceiptLine("Supplier:", supplierName))
                                add(PaymentReceiptLine("Invoice:", invoiceName))
                                add(PaymentReceiptLine("Payment Type:", typeDisplay))
                                if (!p.chequeNumber.isNullOrBlank()) add(PaymentReceiptLine("Cheque No:", p.chequeNumber!!))
                                if (!p.chequeDate.isNullOrBlank()) add(PaymentReceiptLine("Cheque Date:", p.chequeDate!!.replace('T', ' ').take(16)))
                                if (p.bank.isNotBlank()) add(PaymentReceiptLine("Bank:", p.bank))
                                if (!p.trn.isNullOrBlank()) add(PaymentReceiptLine("TRN:", p.trn))
                                add(PaymentReceiptLine("Payment Date:", p.paymentDate.orEmpty().replace('T', ' ').take(16)))
                                add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", p.paidAmount), emphasize = true))
                            }
                            selectedHistoryPayment = null
                            emitPaymentReceipt("SUPPLIER PAYMENT RECEIPT", lines)
                        }
                    ) { Text("Print") }

                    if (canDelete) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                            onClick = {
                                // Route to the confirmation dialog above.
                                paymentToDelete = p
                                selectedHistoryPayment = null
                            }
                        ) { Text("Delete") }
                    }
                }
            },
            dismissButton = {
                Button(onClick = { selectedHistoryPayment = null }) { Text("Close") }
            }
        )
    }

    // History "From" date picker
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { fromDate = state.selectedDateMillis; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { fromDate = null; showFromPicker = false }) { Text("Clear") } }
        ) { DatePicker(state = state) }
    }

    // History "To" date picker
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { toDate = state.selectedDateMillis; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { toDate = null; showToPicker = false }) { Text("Clear") } }
        ) { DatePicker(state = state) }
    }

    // Receipt preview dialog (shown when showReceiptPrintPreviewAndroidApp == "Y").
    previewPdfFile?.let { pdf ->
        ReceiptPreviewDialog(
            pdfFile = pdf,
            canShare = config?.enableShareAndroidApp == "Y",
            onShare = { ReceiptShareUtils.sharePdf(context, pdf) },
            onDismiss = { previewPdfFile = null }
        )
    }
}

@Composable
private fun SupplierDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color(0xFF555555))
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = Color(0xFF1A1A2E),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun supplierDarkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF1A1A2E),
    unfocusedTextColor = Color(0xFF1A1A2E),
    disabledTextColor = Color(0xFF1A1A2E),
    focusedLabelColor = Color(0xFF1A1A2E),
    unfocusedLabelColor = Color(0xFF333333),
    disabledLabelColor = Color(0xFF333333),
    focusedBorderColor = Color(0xFF1A1A2E),
    unfocusedBorderColor = Color(0xFF666666),
    disabledBorderColor = Color(0xFF666666),
    cursorColor = Color(0xFF1A1A2E)
)

/**
 * True if the ISO payment date (e.g. "2026-06-10T18:30:00.000+00:00") falls within the
 * optional [fromMs]/[toMs] range. Compares the yyyy-MM-dd date part lexicographically
 * (ISO dates sort chronologically). A null bound means that side is open.
 */
private fun withinDateRange(iso: String, fromMs: Long?, toMs: Long?): Boolean {
    if (fromMs == null && toMs == null) return true
    val datePart = iso.take(10)
    if (datePart.length < 10) return true
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    if (fromMs != null && datePart < fmt.format(Date(fromMs))) return false
    if (toMs != null && datePart > fmt.format(Date(toMs))) return false
    return true
}

private fun nowIso(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date())
}

// ---------------- API helpers (mirror CustomerPaymentsScreen HttpURLConnection style) ----------------

// Suppliers — dealer-service (8090)
private suspend fun fetchAllSuppliersForPayments(token: String): List<SupplierData> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/supplier/getAllSuppliers?companyCode=0")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<SupplierData>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// Total invoiced for a supplier — bill-service (8080) /invoice/getSupplierPayments
private suspend fun fetchSupplierInvoicedTotal(token: String, supplierId: Int): Double {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getSupplierPayments?supplierId=$supplierId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }.toDoubleOrNull() ?: 0.0
        } else 0.0
    } catch (e: Exception) {
        e.printStackTrace(); 0.0
    }
}

// All payments for a supplier — bill-service (8080) /supplier/payment?supplierId=
private suspend fun fetchSupplierPayments(token: String, supplierId: Int): List<SupplierPayment> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/supplier/payment?supplierId=$supplierId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<SupplierPayment>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// All supplier payments for the company — bill-service (8080) /supplier/payment/all.
// Used by the history search when no supplier is selected.
private suspend fun fetchAllSupplierPayments(token: String): List<SupplierPayment> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/supplier/payment/all")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<SupplierPayment>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// Invoices for a supplier — bill-service (8080) /invoice/getAllInvoicesBySupplier
private suspend fun fetchInvoicesBySupplier(token: String, supplierId: Int): List<SupplierInvoice> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getAllInvoicesBySupplier?supplierId=$supplierId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<SupplierInvoice>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// Selected invoice with details — bill-service (8080) /invoice/getSelectedInvoice?id=
private suspend fun fetchSelectedInvoice(token: String, invoiceId: Int): SupplierInvoiceFull? {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getSelectedInvoice?id=$invoiceId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, SupplierInvoiceFull::class.java)
        } else null
    } catch (e: Exception) {
        e.printStackTrace(); null
    }
}

// Total paid for a single invoice — bill-service (8080) /supplier/payment/total?invoiceId=
private suspend fun fetchInvoicePaidTotal(token: String, invoiceId: Int): Double {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/supplier/payment/total?invoiceId=$invoiceId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }.toDoubleOrNull() ?: 0.0
        } else 0.0
    } catch (e: Exception) {
        e.printStackTrace(); 0.0
    }
}

// Add payment — bill-service (8080) /supplier/payment/add
private suspend fun addSupplierPayment(token: String, payment: SupplierPayment): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/supplier/payment/add")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        val jsonBody = Gson().toJson(payment)
        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }
        val responseCode = connection.responseCode
        if (responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
            Pair(true, "Payment added successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Payment failed ($responseCode): $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "Payment failed: ${e.message}")
    }
}

// Delete payment — bill-service (8080) /supplier/payment?id=
private suspend fun deleteSupplierPayment(token: String, id: Int): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/supplier/payment?id=$id")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            Pair(true, "Payment deleted successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Delete failed: $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "Delete failed: ${e.message}")
    }
}

// App config — dealer-service (8090) /admin/getConfigsByToken
private suspend fun fetchSupplierAppConfig(token: String): com.pos.cosmos.models.Config? {
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
        e.printStackTrace(); null
    }
}

// Delete permission — dealer-service (8090) /admin/getUserPermissionsByToken
private suspend fun fetchDeletePermission(token: String): Boolean {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/admin/getUserPermissionsByToken")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val perm = Gson().fromJson(content, com.pos.cosmos.models.UserPermission::class.java)
            perm?.deleteItems == "Y"
        } else false
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}
