package com.pos.cosmos.screens

import android.annotation.SuppressLint
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.models.CustomerPayment
import com.pos.cosmos.models.BillWithDetails
import com.pos.cosmos.models.CustomerCreditSummary
import com.pos.cosmos.screens.CustomerData
import com.pos.cosmos.utils.PaymentReceiptLine
import com.pos.cosmos.utils.PaymentBillBlock
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.utils.ReceiptPreviewDialog
import com.pos.cosmos.utils.ReceiptShareUtils
import com.pos.cosmos.utils.producePaymentReceipt
import com.pos.cosmos.utils.producePaymentsByBillReceipt
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone
import com.pos.cosmos.api.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun CustomerPaymentsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var selectedCustomer by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf(0L) }
    var selectedBillNo by remember { mutableStateOf<Long?>(null) }
    var billSearchQuery by remember { mutableStateOf("") }
    var filteredCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var allCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var expandedCustomers by remember { mutableStateOf(false) }

    var bills by remember { mutableStateOf<List<BillWithDetails>>(emptyList()) }
    var filteredBills by remember { mutableStateOf<List<BillWithDetails>>(emptyList()) }
    var expandedBills by remember { mutableStateOf(false) }
    var billsLoaded by remember { mutableStateOf(false) }

    var totalOutstanding by remember { mutableStateOf(0.0) }
    var customerCredit by remember { mutableStateOf(0.0) }
    var showCustomerCredit by remember { mutableStateOf(false) }

    var billAmount by remember { mutableStateOf(0.0) }
    var returnAmount by remember { mutableStateOf(0.0) }
    var netAmount by remember { mutableStateOf(0.0) }
    var paidAmount by remember { mutableStateOf(0.0) }
    var creditNote by remember { mutableStateOf(0.0) }
    var balanceAmount by remember { mutableStateOf(0.0) }

    var paymentHistory by remember { mutableStateOf<List<CustomerPayment>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var canDelete by remember { mutableStateOf(false) }
    var selectedHistoryPayment by remember { mutableStateOf<CustomerPayment?>(null) }
    var confirmDeletePayment by remember { mutableStateOf<CustomerPayment?>(null) }

    // Dialog states
    var displayChequePayment by remember { mutableStateOf(false) }
    var displayCashPayment by remember { mutableStateOf(false) }
    var displayUseCustomerCredit by remember { mutableStateOf(false) }

    // Payment input fields
    var chequeAmount by remember { mutableStateOf("") }
    var chequeNumber by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var bankAndBranch by remember { mutableStateOf("") }
    var trn by remember { mutableStateOf("") }
    var chequeDate by remember { mutableStateOf<Long?>(null) }

    var cashAmount by remember { mutableStateOf("") }
    var cashTRN by remember { mutableStateOf("") }

    var creditAmountToBeUsed by remember { mutableStateOf("") }

    // Search history fields
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var historySearched by remember { mutableStateOf(false) }
    var showOnlyUnpaid by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Receipt preview / share state (gated by showReceiptPrintPreviewAndroidApp / enableShareAndroidApp)
    var billTemplate by remember { mutableStateOf<JsonObject?>(null) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                withContext(Dispatchers.IO) {
                    val customers = fetchAllCustomers(token!!)
                    allCustomers = customers
                    filteredCustomers = customers
                    canDelete = fetchCustomerDeletePermission(token!!)
                    // The payment screens are reachable without visiting a bill screen, so the
                    // shared config may be null here. Fetch it so the receipt preview / share
                    // flags (showReceiptPrintPreviewAndroidApp / enableShareAndroidApp) are honoured.
                    val appConfig = fetchAppConfig(token!!)
                    if (appConfig != null) {
                        appViewModel.setConfig(appConfig)
                    }
                    // Preload the bill template so the company header / logo are ready when
                    // a receipt is generated. Best-effort — generation falls back if null.
                    if (appConfig?.showReceiptPrintPreviewAndroidApp == "Y" || appConfig?.enableShareAndroidApp == "Y") {
                        billTemplate = com.pos.cosmos.utils.loadBillTemplateForPaymentReceipt(
                            context, token!!, appConfig?.use58mmBillTemplateForAndroidApp == "Y"
                        )
                    }
                }
                loading = false
            }
        }
    }

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

    // Generate the grouped "payments by bill" receipt and route it to print + preview + share,
    // mirroring emitPaymentReceipt's config gating.
    fun emitPaymentsByBillReceipt(
        title: String,
        summaryLines: List<PaymentReceiptLine>,
        sections: List<PaymentBillBlock>,
        grandTotalLines: List<PaymentReceiptLine>
    ) {
        val showPreview = config?.showReceiptPrintPreviewAndroidApp == "Y"
        val enableShare = config?.enableShareAndroidApp == "Y"
        scope.launch {
            val pdf = withContext(Dispatchers.IO) {
                producePaymentsByBillReceipt(
                    context,
                    config?.use58mmBillTemplateForAndroidApp == "Y",
                    showPreview, enableShare, title, summaryLines, sections, grandTotalLines
                )
            }
            if (pdf != null) {
                if (showPreview) previewPdfFile = pdf
                else if (enableShare) ReceiptShareUtils.sharePdf(context, pdf)
            }
        }
    }

    // Build one receipt covering every payment in the current history search, grouped by bill.
    // Bills are ordered by bill no; for each bill the bill's items are rendered exactly like the
    // small bill template (item grid + Gross / Discount / Net total), then the payments made
    // against that bill, with a blank line break between bills. Bills and item names are fetched
    // on demand (the history search itself returns only payments). Routed to print + preview +
    // share (same small bill template / config gating as a single payment receipt).
    fun printAllPayments() {
        if (paymentHistory.isEmpty()) return
        val tk = token ?: return
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val singleCustomer = paymentHistory.map { it.customerId }.distinct().size == 1
        val headerName = paymentHistory.firstOrNull()?.customerName ?: ""
        // Use the full paper width (80mm, or 58mm when that template is enabled).
        val paperWidthMm = if (config?.use58mmBillTemplateForAndroidApp == "Y") 58f else 80f

        // Payments grouped by bill, bills ordered by bill no ascending.
        val billGroups = paymentHistory.groupBy { it.billId }.toSortedMap()

        scope.launch {
            // Fetch each customer's bills once (the search can span multiple customers) plus the
            // item-name lookup, so the item grid can be rendered. All best-effort — when a bill or
            // its items are unavailable the item grid for that bill is simply omitted.
            val (billMap, itemNames) = withContext(Dispatchers.IO) {
                val bills = HashMap<Long, BillWithDetails>()
                paymentHistory.map { it.customerId }.distinct().forEach { cid ->
                    fetchBillsByCustomer(tk, cid).forEach { b -> bills[b.billNo] = b }
                }
                Pair(bills, fetchItemNames(tk))
            }

            val sections = billGroups.map { (billId, payments) ->
                val bill = billMap[billId]
                // Build the small-bill item grid for this bill (mirrors the credit/cash bill math:
                // line amount = qty * (unitPrice - per-line discount); gross is the sum of those).
                val receiptItems = bill?.billItems.orEmpty().map { item ->
                    val name = itemNames[item.itemId.toLong()] ?: "Item #${item.itemId}"
                    ReceiptItem(
                        name = name,
                        quantity = item.qty.toInt(),
                        price = item.unitPrice,
                        amount = item.qty * (item.unitPrice - item.discountValue),
                        discountValue = item.discountValue
                    )
                }
                val returnItems = bill?.billItems.orEmpty().filter { it.returnQty > 0.0 }.map { item ->
                    val name = itemNames[item.itemId.toLong()] ?: "Item #${item.itemId}"
                    ReceiptItem(
                        name = name,
                        quantity = item.returnQty.toInt(),
                        price = item.unitPrice,
                        amount = item.returnQty * (item.unitPrice - item.discountValue),
                        discountValue = item.discountValue
                    )
                }
                val grossTotal = receiptItems.sumOf { it.amount }
                val totalReturn = returnItems.sumOf { it.amount }
                val billDiscount = bill?.discountValue ?: 0.0
                val netTotal = grossTotal - totalReturn - billDiscount
                val totalItemDiscount = bill?.billItems.orEmpty().sumOf { it.qty * it.discountValue }
                // Only show the per-item "Dis." column when this bill actually has item-level discounts.
                val showLineDiscount = bill?.billItems.orEmpty().any { it.discountValue > 0.0 }

                val data = buildBillReceiptData(
                    template = billTemplate,
                    items = receiptItems,
                    grossTotal = grossTotal,
                    discount = billDiscount,
                    netTotal = netTotal,
                    billNo = billId.toString(),
                    showLineDiscount = showLineDiscount,
                    totalItemDiscount = totalItemDiscount,
                    returnItems = returnItems,
                    totalReturn = totalReturn,
                    paperWidthMm = paperWidthMm
                )

                // Payment rows for this bill, followed by the bill's total paid.
                val paymentLines = buildList {
                    payments.forEach { p ->
                        add(PaymentReceiptLine("", "", separator = true))
                        val typeDisplay = when (p.type.uppercase(Locale.US)) {
                            "CHEQUE" -> "Cheque"
                            "CREDIT" -> "Customer Credit"
                            else -> "Cash"
                        }
                        val isCheque = p.type.equals("CHEQUE", ignoreCase = true)
                        add(PaymentReceiptLine("Payment Date:", p.paymentDateStr.ifBlank { formatPaymentDate(p.paymentDate) }.take(10)))
                        add(PaymentReceiptLine("Type:", typeDisplay))
                        // Cheque-only fields — never shown for cash / customer-credit payments
                        // (the backend can carry a stale cheque date on non-cheque rows).
                        if (isCheque && p.chequeNumber.isNotBlank()) add(PaymentReceiptLine("Cheque No:", p.chequeNumber))
                        if (isCheque && !p.chequeDate.isNullOrBlank()) add(PaymentReceiptLine("Cheque Date:", formatDateOnly(p.chequeDate!!)))
                        if (isCheque && p.bankAndBranch.isNotBlank()) add(PaymentReceiptLine("Bank:", p.bankAndBranch))
                        if (isCheque && p.accountNumber.isNotBlank()) add(PaymentReceiptLine("Account No:", p.accountNumber))
                        val trnShown = if (p.trn.isNotBlank()) p.trn else p.cashTRN
                        if (trnShown.isNotBlank()) add(PaymentReceiptLine("TRN:", trnShown))
                        add(PaymentReceiptLine("Amount:", String.format(Locale.US, "%.2f", p.paidAmount), emphasize = true))
                    }
                    add(PaymentReceiptLine("", "", separator = true))
                    add(PaymentReceiptLine("Bill Total Paid:", String.format(Locale.US, "%.2f", payments.sumOf { it.paidAmount }), bold = true))
                }

                val custName = if (!singleCustomer) {
                    payments.firstOrNull { it.customerName.isNotBlank() }?.customerName ?: ""
                } else ""

                PaymentBillBlock(
                    data = data,
                    customerName = custName,
                    billDate = bill?.billingDate?.take(10) ?: "",
                    paymentLines = paymentLines
                )
            }

            val summaryLines = buildList {
                if (singleCustomer && headerName.isNotBlank()) {
                    add(PaymentReceiptLine("Customer:", headerName, bold = true, inline = true))
                }
                if (fromDate != null || toDate != null) {
                    val f = fromDate?.let { dayFmt.format(Date(it)) } ?: "—"
                    val t = toDate?.let { dayFmt.format(Date(it)) } ?: "—"
                    add(PaymentReceiptLine("Period:", "$f to $t", inline = true))
                }
                add(PaymentReceiptLine("Bills:", billGroups.size.toString(), inline = true))
                add(PaymentReceiptLine("Payments:", paymentHistory.size.toString(), inline = true))
            }
            val grandTotalLines = listOf(
                PaymentReceiptLine("Total Paid:", String.format(Locale.US, "%.2f", paymentHistory.sumOf { it.paidAmount }), bold = true, emphasize = true)
            )
            val title = if (singleCustomer && headerName.isNotBlank()) "CUSTOMER PAYMENTS" else "PAYMENT HISTORY"
            emitPaymentsByBillReceipt(title, summaryLines, sections, grandTotalLines)
        }
    }

    // Run the payment-history search with the current filters. With no customer selected
    // it returns all customers' payments (mirrors the web app); otherwise it filters to the
    // selected customer (and bill, if one is selected).
    fun runHistorySearch() {
        val tk = token ?: return
        val custId = selectedCustomerId
        val billNo = selectedBillNo
        scope.launch {
            val history = withContext(Dispatchers.IO) {
                fetchPaymentHistory(tk, custId, billNo, fromDate, toDate)
            }
            val nameById = allCustomers.associate { it.id to (it.name ?: "") }
            paymentHistory = history
                .filter { p ->
                    (custId <= 0 || p.customerId == custId) &&
                        (billNo == null || billNo <= 0 || p.billId == billNo) &&
                        p.paidAmount != 0.0
                }
                .map { p -> sanitizePayment(p, nameById[p.customerId] ?: "") }
            historySearched = true
        }
    }

    fun filterCustomers(query: String) {
        selectedCustomer = query
        if (query.isEmpty()) {
            filteredCustomers = allCustomers
            expandedCustomers = false
            // Clearing the customer name must also clear everything derived from the
            // selected customer (bill, outstanding, credit, amounts).
            selectedCustomerId = 0L
            selectedBillNo = null
            billSearchQuery = ""
            bills = emptyList()
            filteredBills = emptyList()
            billsLoaded = false
            expandedBills = false
            totalOutstanding = 0.0
            customerCredit = 0.0
            showCustomerCredit = false
            billAmount = 0.0
            returnAmount = 0.0
            netAmount = 0.0
            paidAmount = 0.0
            creditNote = 0.0
            balanceAmount = 0.0
            successMessage = ""
            errorMessage = ""
        } else {
            filteredCustomers = allCustomers.filter { customer ->
                (customer.name?.contains(query, ignoreCase = true) ?: false)
            }
            expandedCustomers = filteredCustomers.isNotEmpty()
        }
    }

    fun onSelectCustomer(customer: CustomerData) {
        selectedCustomer = customer.name ?: ""
        selectedCustomerId = customer.id
        expandedCustomers = false

        // Clear previous bill data
        selectedBillNo = null
        billSearchQuery = ""
        bills = emptyList()
        filteredBills = emptyList()
        billsLoaded = false

        // Reset bill details
        billAmount = 0.0
        returnAmount = 0.0
        netAmount = 0.0
        paidAmount = 0.0
        creditNote = 0.0
        balanceAmount = 0.0

        // Fetch outstanding and bills
        scope.launch {
            withContext(Dispatchers.IO) {
                val summary = fetchCustomerCreditSummary(token!!, selectedCustomerId)
                totalOutstanding = summary.totalOutstanding
                customerCredit = summary.customerCredit
                showCustomerCredit = customerCredit > 0.0

                val billsData = if (showOnlyUnpaid) {
                    fetchUnpaidBills(token!!, selectedCustomerId)
                } else {
                    fetchBillsByCustomer(token!!, selectedCustomerId)
                }
                bills = billsData
                filteredBills = billsData
                billsLoaded = true
                // Auto-expand if bills are available
                if (billsData.isNotEmpty()) {
                    expandedBills = true
                }
            }
        }
    }

    fun filterBills(query: String) {
        billSearchQuery = query
        if (query.isEmpty()) {
            filteredBills = bills
            // Keep showing bills if they exist
            expandedBills = bills.isNotEmpty()
        } else {
            filteredBills = bills.filter { bill ->
                bill.billNo.toString().contains(query, ignoreCase = true)
            }
            expandedBills = filteredBills.isNotEmpty()
        }
    }

    fun onSelectBill(bill: BillWithDetails) {
        selectedBillNo = bill.billNo
        billSearchQuery = bill.billNo.toString()
        expandedBills = false

        // Calculate bill amounts
        var total = 0.0
        var returnVal = 0.0

        for (item in bill.billItems) {
            total += item.qty * (item.unitPrice - item.discountValue)
            returnVal += item.returnQty * (item.unitPrice - item.discountValue)
        }
        total -= bill.discountValue

        billAmount = total
        netAmount = total
        returnAmount = returnVal

        // Fetch paid amount and credit note
        scope.launch {
            withContext(Dispatchers.IO) {
                val paid = fetchBillPayment(token!!, bill.billNo, selectedCustomerId)
                paidAmount = paid

                val credit = fetchCreditNote(token!!, bill.billNo)
                creditNote = credit

                // Calculate balance after fetching data
                balanceAmount = netAmount - paidAmount - creditNote - returnAmount
            }
        }
    }

    fun clearPaymentFields() {
        chequeAmount = ""
        chequeNumber = ""
        accountNumber = ""
        bankAndBranch = ""
        trn = ""
        chequeDate = null
        cashAmount = ""
        cashTRN = ""
        creditAmountToBeUsed = ""
    }

    // UI Layout
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Payments", color = Color.White) },
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
                // Customer Selection
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Customer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                TextField(
                                    value = selectedCustomer,
                                    onValueChange = { filterCustomers(it) },
                                    label = { Text("Select Customer") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedCustomers = true },
                                    singleLine = true,
                                    readOnly = false
                                )

                                if (expandedCustomers && filteredCustomers.isNotEmpty()) {
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
                                                        .clickable { onSelectCustomer(customer) }
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
                        }
                    }
                }

                // Success/Error Messages
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

                // Outstanding and Credit Info
                if (selectedCustomerId > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Total Outstanding", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                                        Text(String.format("%.2f", totalOutstanding), fontSize = 16.sp, color = Color(0xFFE94560))
                                    }
                                    if (showCustomerCredit) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Customer Credit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                                            Text(String.format("%.2f", customerCredit), fontSize = 16.sp, color = Color(0xFF34C759))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bill Selection
                if (selectedCustomerId > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Bill Number", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (billsLoaded) {
                                        Text(
                                            "${bills.size} bills found",
                                            fontSize = 12.sp,
                                            color = Color(0xFF34C759)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    TextField(
                                        value = billSearchQuery,
                                        onValueChange = { filterBills(it) },
                                        label = { Text("Type or select bill number") },
                                        placeholder = { Text("Search bill...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Always show bills dropdown when bills are loaded and expanded
                                    if (expandedBills && filteredBills.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp),
                                            color = Color(0xFFF5F5F5),
                                            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                        ) {
                                            LazyColumn {
                                                items(filteredBills) { bill ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { onSelectBill(bill) }
                                                            .padding(12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Bill #${bill.billNo}",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1A1A2E)
                                                        )
                                                        Text(
                                                            text = bill.billingDate?.take(10) ?: "",
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF666666)
                                                        )
                                                    }
                                                    Divider(color = Color.LightGray)
                                                }
                                            }
                                        }
                                    } else if (billsLoaded && bills.isEmpty()) {
                                        Text(
                                            "No bills found for this customer",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = showOnlyUnpaid,
                                        onCheckedChange = {
                                            showOnlyUnpaid = it
                                            selectedBillNo = null
                                            billSearchQuery = ""
                                            if (selectedCustomerId > 0) {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val billsData = if (showOnlyUnpaid) {
                                                            fetchUnpaidBills(token!!, selectedCustomerId)
                                                        } else {
                                                            fetchBillsByCustomer(token!!, selectedCustomerId)
                                                        }
                                                        bills = billsData
                                                        filteredBills = billsData
                                                        if (billsData.isNotEmpty()) {
                                                            expandedBills = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    Text("Show only unpaid bills", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Bill Details
                if (selectedBillNo != null && selectedBillNo!! > 0) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                BillDetailsRow("Bill Amount", String.format("%.2f", billAmount), Color(0xFF333333))
                                BillDetailsRow("Return Amount", String.format("%.2f", returnAmount), Color(0xFF333333))
                                BillDetailsRow("Net Amount", String.format("%.2f", netAmount), Color(0xFF333333))
                                BillDetailsRow("Paid Amount", String.format("%.2f", paidAmount), Color(0xFF34C759))
                                BillDetailsRow("Credit Note", String.format("%.2f", creditNote), Color(0xFF333333))
                                BillDetailsRow(
                                    "Balance",
                                    String.format("%.2f", balanceAmount),
                                    if (balanceAmount <= 0) Color(0xFF34C759) else Color(0xFFE94560)
                                )

                                // Show fully paid message
                                if (balanceAmount <= 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF34C759).copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            text = "This bill is fully paid",
                                            color = Color(0xFF34C759),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Payment Buttons - Only show if balance > 0
                    if (balanceAmount > 0) {
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
                                        onClick = { displayChequePayment = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                                    ) {
                                        Text("Cheque Payment")
                                    }

                                    Button(
                                        onClick = { displayCashPayment = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                    ) {
                                        Text("Cash Payment")
                                    }

                                    if (showCustomerCredit && customerCredit > 0) {
                                        Button(
                                            onClick = { displayUseCustomerCredit = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                                        ) {
                                            Text("Use Customer Credit")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Payment History Section
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
                            Text(
                                if (selectedCustomerId > 0) "Showing payments for ${selectedCustomer}"
                                else "No customer selected — searches all customers",
                                fontSize = 11.sp,
                                color = Color(0xFF333333)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = fromDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "",
                                    onValueChange = {},
                                    label = { Text("From") },
                                    readOnly = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A2E), fontWeight = FontWeight.SemiBold),
                                    colors = darkFieldColors(),
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
                                    value = toDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "",
                                    onValueChange = {},
                                    label = { Text("To") },
                                    readOnly = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A2E), fontWeight = FontWeight.SemiBold),
                                    colors = darkFieldColors(),
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
                                ) {
                                    Text("Search")
                                }
                            }

                            if (historySearched && paymentHistory.isEmpty()) {
                                Text(
                                    "No payments found",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }

                            // Print every payment in the current search as one receipt.
                            if (paymentHistory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { printAllPayments() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                                ) {
                                    Text("Print All Payments (${paymentHistory.size})")
                                }
                            }
                        }
                    }
                }

                // Payment History Table
                items(paymentHistory) { payment ->
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
                                    if (payment.customerName.isNotBlank()) {
                                        Text(payment.customerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A1A2E))
                                    }
                                    Text("Bill #${payment.billId}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1A1A2E))
                                    Text(payment.paymentDateStr, fontSize = 10.sp, color = Color(0xFF555555))
                                }
                                Text(String.format("%.2f", payment.paidAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A2E))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val typeDisplay = when (payment.type.uppercase(Locale.US)) {
                                "CHEQUE" -> "Cheque"
                                "CREDIT" -> "Customer Credit"
                                else -> "Cash"
                            }
                            // Show every relevant field for the payment. Cheque-specific fields are
                            // shown only for cheque payments (cash/credit can carry stale values).
                            val isCheque = payment.type.equals("CHEQUE", ignoreCase = true)
                            Text("Type: $typeDisplay", fontSize = 10.sp, color = Color(0xFF555555))
                            if (isCheque && payment.chequeNumber.isNotBlank()) {
                                Text("Cheque No: ${payment.chequeNumber}", fontSize = 10.sp, color = Color(0xFF555555))
                            }
                            if (isCheque && !payment.chequeDate.isNullOrBlank()) {
                                Text("Cheque Date: ${formatDateOnly(payment.chequeDate!!)}", fontSize = 10.sp, color = Color(0xFF555555))
                            }
                            if (isCheque && payment.bankAndBranch.isNotBlank()) {
                                Text("Bank & Branch: ${payment.bankAndBranch}", fontSize = 10.sp, color = Color(0xFF555555))
                            }
                            if (isCheque && payment.accountNumber.isNotBlank()) {
                                Text("Account No: ${payment.accountNumber}", fontSize = 10.sp, color = Color(0xFF555555))
                            }
                            run {
                                val trnShown = if (payment.trn.isNotBlank()) payment.trn else payment.cashTRN
                                if (trnShown.isNotBlank()) {
                                    Text("TRN: $trnShown", fontSize = 10.sp, color = Color(0xFF555555))
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    // Dialogs for payments will be added in separate composables
    ChequePaymentDialog(
        visible = displayChequePayment,
        balanceAmount = balanceAmount,
        amount = chequeAmount,
        onAmountChange = { chequeAmount = it },
        chequeNumber = chequeNumber,
        onChequeNumberChange = { chequeNumber = it },
        chequeDateMillis = chequeDate,
        onChequeDateChange = { chequeDate = it },
        accountNumber = accountNumber,
        onAccountNumberChange = { accountNumber = it },
        bankAndBranch = bankAndBranch,
        onBankAndBranchChange = { bankAndBranch = it },
        trn = trn,
        onTrnChange = { trn = it },
        onPay = {
            if (selectedBillNo != null && selectedBillNo!! > 0) {
                // Capture values BEFORE clearing
                val paymentAmount = chequeAmount.toDoubleOrNull() ?: 0.0
                val paymentChequeNumber = chequeNumber
                val paymentBankAndBranch = bankAndBranch
                val paymentAccountNumber = accountNumber
                val paymentTrn = trn
                val paymentChequeDateMillis = chequeDate
                val billId = selectedBillNo!!
                val custId = selectedCustomerId
                val custName = selectedCustomer
                val preBalance = balanceAmount

                displayChequePayment = false
                clearPaymentFields()

                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        isoDateFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val chequeDateIso = paymentChequeDateMillis?.let { isoDateFormat.format(Date(it)) }
                        val payment = CustomerPayment(
                            id = 0,
                            customerId = custId,
                            billId = billId,
                            paidAmount = paymentAmount,
                            type = "cheque",
                            chequeNumber = paymentChequeNumber,
                            chequeDate = chequeDateIso,
                            bankAndBranch = paymentBankAndBranch,
                            accountNumber = paymentAccountNumber,
                            paymentDate = isoDateFormat.format(Date()),
                            active = "Y",
                            salesRepId = 0,
                            trn = paymentTrn,
                            cashTRN = "",
                            chequeStatus = "pending",
                            customerName = "",
                            paymentDateStr = "",
                            chequeDateStr = ""
                        )
                        addPayment(token!!, payment)
                    }
                    if (result.first) {
                        successMessage = result.second
                        errorMessage = ""
                        // Refresh outstanding and paid amount
                        withContext(Dispatchers.IO) {
                            val summary = fetchCustomerCreditSummary(token!!, custId)
                            totalOutstanding = summary.totalOutstanding
                            val paid = fetchBillPayment(token!!, billId, custId)
                            paidAmount = paid
                            balanceAmount = netAmount - paidAmount - creditNote - returnAmount
                        }
                        val lines = buildList {
                            if (custName.isNotBlank()) add(PaymentReceiptLine("Customer:", custName))
                            add(PaymentReceiptLine("Bill No:", billId.toString()))
                            add(PaymentReceiptLine("Payment Type:", "Cheque"))
                            if (paymentChequeNumber.isNotBlank()) add(PaymentReceiptLine("Cheque No:", paymentChequeNumber))
                            paymentChequeDateMillis?.let {
                                add(PaymentReceiptLine("Cheque Date:", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))))
                            }
                            if (paymentBankAndBranch.isNotBlank()) add(PaymentReceiptLine("Bank:", paymentBankAndBranch))
                            if (paymentTrn.isNotBlank()) add(PaymentReceiptLine("TRN:", paymentTrn))
                            add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", paymentAmount), emphasize = true))
                            add(PaymentReceiptLine("Balance:", String.format(Locale.US, "%.2f", (preBalance - paymentAmount).coerceAtLeast(0.0)), bold = true))
                        }
                        emitPaymentReceipt("CUSTOMER PAYMENT RECEIPT", lines)
                    } else {
                        errorMessage = result.second
                        successMessage = ""
                    }
                }
            } else {
                displayChequePayment = false
                clearPaymentFields()
            }
        },
        onCancel = {
            displayChequePayment = false
            clearPaymentFields()
        }
    )

    CashPaymentDialog(
        visible = displayCashPayment,
        balanceAmount = balanceAmount,
        amount = cashAmount,
        onAmountChange = { cashAmount = it },
        trn = cashTRN,
        onTrnChange = { cashTRN = it },
        onPay = {
            if (selectedBillNo != null && selectedBillNo!! > 0) {
                // Capture values BEFORE clearing
                val paymentAmount = cashAmount.toDoubleOrNull() ?: 0.0
                val paymentTRN = cashTRN
                val billId = selectedBillNo!!
                val custId = selectedCustomerId
                val custName = selectedCustomer
                val preBalance = balanceAmount

                displayCashPayment = false
                clearPaymentFields()

                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        isoDateFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val payment = CustomerPayment(
                            id = 0,
                            customerId = custId,
                            billId = billId,
                            paidAmount = paymentAmount,
                            type = "cash",
                            chequeNumber = "",
                            chequeDate = null,
                            bankAndBranch = "",
                            accountNumber = "",
                            paymentDate = isoDateFormat.format(Date()),
                            active = "Y",
                            salesRepId = 0,
                            trn = "",
                            cashTRN = paymentTRN,
                            chequeStatus = "pending",
                            customerName = "",
                            paymentDateStr = "",
                            chequeDateStr = ""
                        )
                        addPayment(token!!, payment)
                    }
                    if (result.first) {
                        successMessage = result.second
                        errorMessage = ""
                        // Refresh outstanding and paid amount for current bill
                        withContext(Dispatchers.IO) {
                            val summary = fetchCustomerCreditSummary(token!!, custId)
                            totalOutstanding = summary.totalOutstanding
                            // Refresh paid amount for the bill
                            val paid = fetchBillPayment(token!!, billId, custId)
                            paidAmount = paid
                            balanceAmount = netAmount - paidAmount - creditNote - returnAmount
                        }
                        val lines = buildList {
                            if (custName.isNotBlank()) add(PaymentReceiptLine("Customer:", custName))
                            add(PaymentReceiptLine("Bill No:", billId.toString()))
                            add(PaymentReceiptLine("Payment Type:", "Cash"))
                            if (paymentTRN.isNotBlank()) add(PaymentReceiptLine("TRN:", paymentTRN))
                            add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", paymentAmount), emphasize = true))
                            add(PaymentReceiptLine("Balance:", String.format(Locale.US, "%.2f", (preBalance - paymentAmount).coerceAtLeast(0.0)), bold = true))
                        }
                        emitPaymentReceipt("CUSTOMER PAYMENT RECEIPT", lines)
                    } else {
                        errorMessage = result.second
                        successMessage = ""
                    }
                }
            } else {
                displayCashPayment = false
                clearPaymentFields()
            }
        },
        onCancel = {
            displayCashPayment = false
            clearPaymentFields()
        }
    )

    if (showCustomerCredit && displayUseCustomerCredit) {
        UseCustomerCreditDialog(
            visible = displayUseCustomerCredit,
            availableCredit = customerCredit,
            balanceAmount = balanceAmount,
            amount = creditAmountToBeUsed,
            onAmountChange = { creditAmountToBeUsed = it },
            onPay = {
                val creditAmt = creditAmountToBeUsed.toDoubleOrNull() ?: 0.0
                // Guard: never apply more than the available credit (or the bill balance).
                if (creditAmt <= 0.0 || creditAmt > customerCredit + 0.005 || creditAmt > balanceAmount + 0.005) {
                    errorMessage = "Amount cannot exceed the available credit or balance."
                    successMessage = ""
                } else if (selectedBillNo != null && selectedBillNo!! > 0) {
                    // Capture values before fields are cleared below.
                    val custId = selectedCustomerId
                    val billId = selectedBillNo!!
                    val custName = selectedCustomer
                    val preBalance = balanceAmount
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val creditUsage = mapOf(
                                "customerId" to custId,
                                "billId" to billId,
                                "amount" to creditAmt
                            )
                            useCustomerCredit(token!!, creditUsage)
                        }
                        if (result.first) {
                            successMessage = result.second
                            errorMessage = ""
                            paidAmount += creditAmt
                            balanceAmount = netAmount - paidAmount - creditNote - returnAmount
                            // Refresh outstanding and customer credit
                            withContext(Dispatchers.IO) {
                                val summary = fetchCustomerCreditSummary(token!!, custId)
                                totalOutstanding = summary.totalOutstanding
                                customerCredit = summary.customerCredit
                            }
                            val lines = buildList {
                                if (custName.isNotBlank()) add(PaymentReceiptLine("Customer:", custName))
                                add(PaymentReceiptLine("Bill No:", billId.toString()))
                                add(PaymentReceiptLine("Payment Type:", "Customer Credit"))
                                add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", creditAmt), emphasize = true))
                                add(PaymentReceiptLine("Balance:", String.format(Locale.US, "%.2f", (preBalance - creditAmt).coerceAtLeast(0.0)), bold = true))
                            }
                            emitPaymentReceipt("CUSTOMER PAYMENT RECEIPT", lines)
                        } else {
                            errorMessage = result.second
                            successMessage = ""
                        }
                    }
                }
                displayUseCustomerCredit = false
                clearPaymentFields()
            },
            onCancel = {
                displayUseCustomerCredit = false
                clearPaymentFields()
            }
        )
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

    // History "From" date picker
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = { fromDate = state.selectedDateMillis; showFromPicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { fromDate = null; showFromPicker = false }) { Text("Clear") }
            }
        ) { DatePicker(state = state) }
    }

    // History "To" date picker
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = { toDate = state.selectedDateMillis; showToPicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { toDate = null; showToPicker = false }) { Text("Clear") }
            }
        ) { DatePicker(state = state) }
    }

    // Payment-history detail popup. Tapping a history row opens this; deletion is offered
    // only when the user has item-delete permission.
    selectedHistoryPayment?.let { p ->
        val typeDisplay = when (p.type.uppercase(Locale.US)) {
            "CHEQUE" -> "Cheque"
            "CREDIT" -> "Customer Credit"
            else -> "Cash"
        }
        AlertDialog(
            onDismissRequest = { selectedHistoryPayment = null },
            containerColor = Color.White,
            titleContentColor = Color(0xFF1A1A2E),
            textContentColor = Color(0xFF1A1A2E),
            title = { Text("Payment Details", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (p.customerName.isNotBlank()) PaymentDetailRow("Customer", p.customerName)
                    PaymentDetailRow("Bill No", p.billId.toString())
                    PaymentDetailRow("Payment Date", p.paymentDateStr.ifBlank { formatPaymentDate(p.paymentDate) })
                    PaymentDetailRow("Amount", String.format(Locale.US, "%.2f", p.paidAmount))
                    PaymentDetailRow("Type", typeDisplay)
                    val isCheque = p.type.equals("CHEQUE", ignoreCase = true)
                    if (isCheque && p.chequeNumber.isNotBlank()) PaymentDetailRow("Cheque Number", p.chequeNumber)
                    if (isCheque && !p.chequeDate.isNullOrBlank()) PaymentDetailRow("Cheque Date", formatDateOnly(p.chequeDate!!))
                    if (isCheque && p.bankAndBranch.isNotBlank()) PaymentDetailRow("Bank & Branch", p.bankAndBranch)
                    if (isCheque && p.accountNumber.isNotBlank()) PaymentDetailRow("Account No", p.accountNumber)
                    val trnShown = if (p.trn.isNotBlank()) p.trn else p.cashTRN
                    if (trnShown.isNotBlank()) PaymentDetailRow("TRN", trnShown)
                    if (p.type.equals("CHEQUE", ignoreCase = true) && p.chequeStatus.isNotBlank()) {
                        PaymentDetailRow("Cheque Status", p.chequeStatus)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Print / preview / share the receipt for this historical payment.
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        onClick = {
                            val lines = buildList {
                                if (p.customerName.isNotBlank()) add(PaymentReceiptLine("Customer:", p.customerName))
                                add(PaymentReceiptLine("Bill No:", p.billId.toString()))
                                add(PaymentReceiptLine("Payment Type:", typeDisplay))
                                val isCheque = p.type.equals("CHEQUE", ignoreCase = true)
                                if (isCheque && p.chequeNumber.isNotBlank()) add(PaymentReceiptLine("Cheque No:", p.chequeNumber))
                                if (isCheque && !p.chequeDate.isNullOrBlank()) add(PaymentReceiptLine("Cheque Date:", formatDateOnly(p.chequeDate!!)))
                                if (isCheque && p.bankAndBranch.isNotBlank()) add(PaymentReceiptLine("Bank:", p.bankAndBranch))
                                val trnShown = if (p.trn.isNotBlank()) p.trn else p.cashTRN
                                if (trnShown.isNotBlank()) add(PaymentReceiptLine("TRN:", trnShown))
                                add(PaymentReceiptLine("Payment Date:", p.paymentDateStr.ifBlank { formatPaymentDate(p.paymentDate) }.take(10)))
                                add(PaymentReceiptLine("Amount Paid:", String.format(Locale.US, "%.2f", p.paidAmount), emphasize = true))
                            }
                            selectedHistoryPayment = null
                            emitPaymentReceipt("CUSTOMER PAYMENT RECEIPT", lines)
                        }
                    ) { Text("Print") }

                    if (canDelete) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                            onClick = {
                                // Ask for confirmation first; the actual delete happens in the
                                // confirmation dialog below.
                                confirmDeletePayment = p
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

    // Delete confirmation — the details dialog's Delete only opens this; deletion happens here.
    confirmDeletePayment?.let { pay ->
        AlertDialog(
            onDismissRequest = { confirmDeletePayment = null },
            containerColor = Color.White,
            titleContentColor = Color(0xFF1A1A2E),
            textContentColor = Color(0xFF1A1A2E),
            title = { Text("Delete Payment", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E)) },
            text = {
                Text(
                    "Are you sure you want to delete this payment of ${String.format(Locale.US, "%.2f", pay.paidAmount)}" +
                        (if (pay.customerName.isNotBlank()) " for ${pay.customerName}" else "") + "?",
                    color = Color(0xFF1A1A2E)
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                    onClick = {
                        confirmDeletePayment = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                deleteCustomerPayment(token!!, pay.id, pay.type)
                            }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                runHistorySearch()
                                if (selectedCustomerId > 0) {
                                    withContext(Dispatchers.IO) {
                                        val summary = fetchCustomerCreditSummary(token!!, selectedCustomerId)
                                        totalOutstanding = summary.totalOutstanding
                                        if (selectedBillNo != null && selectedBillNo!! > 0) {
                                            paidAmount = fetchBillPayment(token!!, selectedBillNo!!, selectedCustomerId)
                                            balanceAmount = netAmount - paidAmount - creditNote - returnAmount
                                        }
                                    }
                                }
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { confirmDeletePayment = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PaymentDetailRow(label: String, value: String) {
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
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
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

@Composable
fun BillDetailsRow(label: String, value: String, valueColor: Color = Color.Black) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = valueColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChequePaymentDialog(
    visible: Boolean,
    balanceAmount: Double,
    amount: String,
    onAmountChange: (String) -> Unit,
    chequeNumber: String,
    onChequeNumberChange: (String) -> Unit,
    chequeDateMillis: Long?,
    onChequeDateChange: (Long?) -> Unit,
    accountNumber: String,
    onAccountNumberChange: (String) -> Unit,
    bankAndBranch: String,
    onBankAndBranchChange: (String) -> Unit,
    trn: String,
    onTrnChange: (String) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit
) {
    if (visible) {
        val entered = amount.toDoubleOrNull() ?: 0.0
        val overpay = entered > balanceAmount + 0.005
        val payDisabled = entered <= 0.0 || overpay || chequeNumber.isBlank()

        var showDatePicker by remember { mutableStateOf(false) }
        val chequeDateText = chequeDateMillis?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it))
        } ?: ""
        val focusManager = LocalFocusManager.current
        val moveNext = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        val finishActions = KeyboardActions(onDone = { focusManager.clearFocus() })

        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Cheque Payment") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    TextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text("Amount") },
                        isError = overpay,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Balance: ${String.format("%.2f", balanceAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (overpay) {
                        Text(
                            "Amount cannot exceed the balance.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = chequeNumber,
                        onValueChange = onChequeNumberChange,
                        label = { Text("Cheque Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
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
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = accountNumber,
                        onValueChange = onAccountNumberChange,
                        label = { Text("Account Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = bankAndBranch,
                        onValueChange = onBankAndBranchChange,
                        label = { Text("Bank & Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = moveNext
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = trn,
                        onValueChange = onTrnChange,
                        label = { Text("TRN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = finishActions
                    )
                }
            },
            confirmButton = {
                Button(onClick = onPay, enabled = !payDisabled) {
                    Text("Pay")
                }
            },
            dismissButton = {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        )

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = chequeDateMillis ?: System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        onChequeDateChange(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun CashPaymentDialog(
    visible: Boolean,
    balanceAmount: Double,
    amount: String,
    onAmountChange: (String) -> Unit,
    trn: String,
    onTrnChange: (String) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit
) {
    if (visible) {
        val entered = amount.toDoubleOrNull() ?: 0.0
        val overpay = entered > balanceAmount + 0.005
        val payDisabled = entered <= 0.0 || overpay

        val focusManager = LocalFocusManager.current

        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Cash Payment") },
            text = {
                Column {
                    TextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text("Amount") },
                        isError = overpay,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                        })
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Balance: ${String.format("%.2f", balanceAmount)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    if (overpay) {
                        Text(
                            "Amount cannot exceed the balance.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = trn,
                        onValueChange = onTrnChange,
                        label = { Text("TRN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        })
                    )
                }
            },
            confirmButton = {
                Button(onClick = onPay, enabled = !payDisabled) {
                    Text("Pay")
                }
            },
            dismissButton = {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UseCustomerCreditDialog(
    visible: Boolean,
    availableCredit: Double,
    balanceAmount: Double,
    amount: String,
    onAmountChange: (String) -> Unit,
    onPay: () -> Unit,
    onCancel: () -> Unit
) {
    if (visible) {
        val entered = amount.toDoubleOrNull() ?: 0.0
        val exceedsCredit = entered > availableCredit + 0.005
        val exceedsBalance = entered > balanceAmount + 0.005
        val payDisabled = entered <= 0.0 || exceedsCredit || exceedsBalance

        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Use Customer Credit") },
            text = {
                Column {
                    Text("Available Credit: ${String.format("%.2f", availableCredit)}", fontWeight = FontWeight.Bold)
                    Text("Balance: ${String.format("%.2f", balanceAmount)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        label = { Text("Amount to be Used") },
                        isError = exceedsCredit || exceedsBalance,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    if (exceedsCredit) {
                        Text(
                            "Amount cannot exceed the available credit.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else if (exceedsBalance) {
                        Text(
                            "Amount cannot exceed the balance.",
                            color = Color(0xFFE94560),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onPay, enabled = !payDisabled) {
                    Text("Pay")
                }
            },
            dismissButton = {
                Button(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        )
    }
}

// API Functions
private suspend fun fetchAllCustomers(token: String): List<CustomerData> {
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
            gson.fromJson(content, Array<CustomerData>::class.java).toList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun fetchCustomerCreditSummary(token: String, customerId: Long): com.pos.cosmos.models.CustomerCreditSummary {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/customerCreditSummary?customerId=$customerId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            gson.fromJson(content, com.pos.cosmos.models.CustomerCreditSummary::class.java)
        } else {
            com.pos.cosmos.models.CustomerCreditSummary()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        com.pos.cosmos.models.CustomerCreditSummary()
    }
}

private suspend fun fetchBillsByCustomer(token: String, customerId: Long): List<BillWithDetails> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getBillsByCustomer?customerId=$customerId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            gson.fromJson(content, Array<BillWithDetails>::class.java).toList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

// Item id -> exact item name lookup (bill-service 8080 /item/getAllItems, returns ItemDto whose
// name field is `name`). Used to label the bill item rows on the grouped "Print All Payments"
// receipt with the same names the bill / returns screens show. Reads the name from whichever key
// is present so it is robust across item DTO variants; items with no resolvable name are left out
// so the caller falls back to "Item #<id>". Best-effort — returns empty on failure.
private suspend fun fetchItemNames(token: String): Map<Long, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = Gson().fromJson(content, com.google.gson.JsonArray::class.java)
            val map = HashMap<Long, String>()
            if (arr != null) {
                fun str(o: JsonObject, key: String): String? =
                    try { o.get(key)?.takeIf { !it.isJsonNull }?.asString } catch (e: Exception) { null }
                for (i in 0 until arr.size()) {
                    val o = arr.get(i).asJsonObject
                    val id = try { o.get("id")?.asLong } catch (e: Exception) { null } ?: continue
                    val name = (str(o, "name") ?: str(o, "itemName") ?: str(o, "description"))?.takeIf { it.isNotBlank() }
                    if (name != null) map[id] = name
                }
            }
            map
        } else emptyMap()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }
}

// Map the small bill template (android / android58) onto a ReceiptData for one bill so its items
// render exactly like a credit/cash small bill (item grid + Gross / Discount / Net). Mirrors the
// field mapping used by the bill screens' buildReceiptFromTemplate; the company header/labels are
// only needed once but are carried on every section's data for simplicity. Falls back to defaults
// when the template is null.
private fun buildBillReceiptData(
    template: JsonObject?,
    items: List<ReceiptItem>,
    grossTotal: Double,
    discount: Double,
    netTotal: Double,
    billNo: String,
    showLineDiscount: Boolean,
    totalItemDiscount: Double,
    returnItems: List<ReceiptItem>,
    totalReturn: Double,
    paperWidthMm: Float
): ReceiptData {
    // Full-width column right-edges (mm). Ignore the template's narrower defaults so the grid
    // spans the whole paper (80mm, or 58mm). ~2mm right pad keeps the Amount column off the edge;
    // Price / Qty / Amount are evenly spaced across the printable width.
    val colAmountMmVal = paperWidthMm - 2f
    val colQtyMmVal = colAmountMmVal - paperWidthMm * 0.22f
    val colPriceMmVal = colQtyMmVal - paperWidthMm * 0.22f
    if (template == null) {
        return ReceiptData(
            items = items,
            grossTotal = grossTotal,
            discount = discount,
            netTotal = netTotal,
            billNo = billNo,
            showLineDiscount = showLineDiscount,
            totalItemDiscount = totalItemDiscount,
            returnItems = returnItems,
            totalReturn = totalReturn,
            colPriceMm = colPriceMmVal,
            colQtyMm = colQtyMmVal,
            colAmountMm = colAmountMmVal,
            colHeaderPriceMm = colPriceMmVal,
            colHeaderQtyMm = colQtyMmVal,
            colHeaderAmountMm = colAmountMmVal
        )
    }
    fun str(key: String, default: String = "") = try { template.get(key)?.asString ?: default } catch (e: Exception) { default }
    fun int(key: String, default: Int) = try { template.get(key)?.asInt ?: default } catch (e: Exception) { default }
    fun flt(key: String, default: Float) = try { template.get(key)?.asFloat ?: default } catch (e: Exception) { default }
    return ReceiptData(
        items = items,
        returnItems = returnItems,
        totalReturn = totalReturn,
        grossTotal = grossTotal,
        discount = discount,
        netTotal = netTotal,
        billNo = billNo,
        showLineDiscount = showLineDiscount,
        totalItemDiscount = totalItemDiscount,
        companyName = str("companyName", "COSMOS POS"),
        address = str("address"),
        contact = str("contact"),
        email = str("email"),
        companyNameFont = int("companyFont", 14),
        addressFont = int("addressFont", 12),
        contactFont = int("contactFont", 12),
        emailFont = int("emailFont", 12),
        lastLineFont = int("lastLineFont", 10),
        companyGap = int("companyGap", 0),
        addressGap = int("addressGap", 0),
        contactGap = int("contactGap", 0),
        emailGap = int("emailGap", 0),
        lastLineGap = int("lastLineGap", 0),
        upperGap = int("upperGap", 0),
        leftGap = int("leftGap", 0),
        logoDataUrl = str("logoDataURL"),
        logoLeft = int("logoLeft", 0),
        logoUpper = int("logoUpper", 0),
        logoWidth = int("logoWidth", 0),
        logoHeight = int("logoHeight", 0),
        lastLine = str("lastLine"),
        billNoLabel = str("billNoLabel", "Bill No :"),
        dateLabel = str("dateLabel", "Date:"),
        itemHeader = str("itemHeader", "Item"),
        priceHeader = str("priceHeader", "Price"),
        qtyHeader = str("qtyHeader", "Qty"),
        amountHeader = str("totalHeader", "Amount"),
        grossTotalLabel = str("grossTotalLabel", "Gross Total:"),
        discountLabel = str("billDiscountLabel", "Discount:"),
        netTotalLabel = str("netTotalLabel", "Net Total:"),
        billNoFontSize = flt("billNoFontSize", 11f),
        dateFontSize = flt("dateFontSize", 11f),
        headerFontSize = flt("itemHeaderFontSize", 11f),
        itemFontSize = flt("itemFontSize", 10f),
        grossTotalFontSize = flt("grossTotalFontSize", 11f),
        discountFontSize = flt("billDiscountFontSize", 11f),
        netTotalFontSize = flt("netTotalFontSize", 13f),
        billNoGap = flt("billNoGap2", 5f),
        dateGap = flt("dateGap2", 5f),
        separatorGap = flt("separatorGap", 5f),
        headerGap = flt("headerGap2", 5f),
        itemNameGap = flt("itemNameGap", 5f),
        itemGap = flt("itemGap", 5f),
        grossTotalGap = flt("grossTotalGap", 5f),
        discountGap = flt("discountGap", 6f),
        netTotalGap = flt("netTotalGap", 5f),
        colPriceMm = colPriceMmVal,
        colQtyMm = colQtyMmVal,
        colAmountMm = colAmountMmVal,
        colHeaderPriceMm = colPriceMmVal,
        colHeaderQtyMm = colQtyMmVal,
        colHeaderAmountMm = colAmountMmVal
    )
}

private suspend fun fetchUnpaidBills(token: String, customerId: Long): List<BillWithDetails> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/unPaidBillsByCustomer?customerId=$customerId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            gson.fromJson(content, Array<BillWithDetails>::class.java).toList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private suspend fun fetchBillPayment(token: String, billNo: Long, customerId: Long): Double {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/customer/payment/totalByCustomer?billId=$billNo&customerId=$customerId")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            content.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    } catch (e: Exception) {
        e.printStackTrace()
        0.0
    }
}

private suspend fun fetchCreditNote(token: String, billNo: Long): Double {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getCreditNote?billId=$billNo")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            content.toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
    } catch (e: Exception) {
        e.printStackTrace()
        0.0
    }
}

/**
 * Fetch customer payments for the date range via POST /customer/payment/details (bill-service).
 * The backend returns ALL company payments in the range (it does not filter by customer/bill),
 * mirroring the web app — the caller filters client-side by selected customer/bill. Passing no
 * dates returns all payments. customerId/billId are sent for parity but ignored server-side.
 */
private suspend fun fetchPaymentHistory(
    token: String,
    customerId: Long,
    billNo: Long?,
    fromDate: Long?,
    toDate: Long?
): List<CustomerPayment> {
    return try {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        isoFmt.timeZone = TimeZone.getTimeZone("UTC")
        val body = HashMap<String, Any?>()
        body["customerId"] = customerId
        body["billId"] = billNo ?: 0
        body["fromDate"] = fromDate?.let { isoFmt.format(Date(it)) }
        // Extend the "to" date to end-of-day so same-day payments are included.
        body["toDate"] = toDate?.let { isoFmt.format(Date(it + 86_399_999L)) }

        val connection = URL("${ApiConfig.BASE_URL_8080}/customer/payment/details")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(Gson().toJson(body)) }

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<CustomerPayment>::class.java).toList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * Rebuild a CustomerPayment with all String fields guaranteed non-null and a display
 * customer name / formatted date filled in. The backend sends null for fields like
 * chequeNumber on cash/credit rows; Gson stores those nulls in the non-null Kotlin
 * fields, so calling .copy() on them throws. We coalesce via a nullable parameter so
 * the null checks actually run (and downstream .isNotBlank()/.uppercase() reads are safe).
 */
private fun sanitizePayment(p: CustomerPayment, displayName: String): CustomerPayment {
    fun s(v: String?): String = v ?: ""
    return CustomerPayment(
        id = p.id,
        customerId = p.customerId,
        billId = p.billId,
        paidAmount = p.paidAmount,
        type = s(p.type),
        chequeNumber = s(p.chequeNumber),
        chequeDate = p.chequeDate,
        bankAndBranch = s(p.bankAndBranch),
        accountNumber = s(p.accountNumber),
        paymentDate = s(p.paymentDate),
        active = s(p.active).ifBlank { "Y" },
        salesRepId = p.salesRepId,
        trn = s(p.trn),
        cashTRN = s(p.cashTRN),
        chequeStatus = s(p.chequeStatus),
        customerName = displayName.ifBlank { s(p.customerName) },
        paymentDateStr = formatPaymentDate(s(p.paymentDate)),
        chequeDateStr = s(p.chequeDateStr)
    )
}

/** Format a backend ISO-8601 payment date (e.g. "2026-06-10T18:30:00.000+00:00") as "yyyy-MM-dd HH:mm". */
private fun formatPaymentDate(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        iso.replace('T', ' ').take(16)
    } catch (e: Exception) {
        iso
    }
}

/** Date only (no time) — used for cheque dates. */
private fun formatDateOnly(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        iso.take(10)
    } catch (e: Exception) {
        iso
    }
}

private suspend fun addPayment(token: String, payment: CustomerPayment): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/customer/payment/add")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true

        val gson = Gson()
        val jsonBody = gson.toJson(payment)

        // Log the request for debugging
        android.util.Log.d("CustomerPayment", "Sending payment: $jsonBody")

        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }

        val responseCode = connection.responseCode
        android.util.Log.d("CustomerPayment", "Response code: $responseCode")

        if (responseCode == 200) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            android.util.Log.d("CustomerPayment", "Response body: $responseBody")
            Pair(true, "Payment added successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            android.util.Log.e("CustomerPayment", "Error response: $errorMsg")
            Pair(false, "Payment failed (${responseCode}): $errorMsg")
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomerPayment", "Exception: ${e.message}", e)
        Pair(false, "Payment failed: ${e.message}")
    }
}

// Delete a customer payment — bill-service (8080) /bill/deleteSelectedPayment.
// `type` must be the uppercase type from /customer/payment/details ("CREDIT" routes to
// credit-usage deletion; anything else deactivates the payment row).
private suspend fun deleteCustomerPayment(token: String, paymentId: Long, type: String): Pair<Boolean, String> {
    return try {
        val typeParam = java.net.URLEncoder.encode(type, "UTF-8")
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/deleteSelectedPayment?paymentId=$paymentId&type=$typeParam")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            if (body == "true") Pair(true, "Payment deleted successfully!")
            else Pair(false, "Could not delete this payment")
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Delete failed: $err")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Delete failed: ${e.message}")
    }
}

// App config — dealer-service (8090) /admin/getConfigsByToken.
private suspend fun fetchAppConfig(token: String): com.pos.cosmos.models.Config? {
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
        e.printStackTrace()
        null
    }
}

// Item-delete permission — dealer-service (8090) /admin/getUserPermissionsByToken.
private suspend fun fetchCustomerDeletePermission(token: String): Boolean {
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
        e.printStackTrace()
        false
    }
}

// Apply customer credit — bill-service (8080) /customer/payment/credit.
// Body matches CustomerCreditUsageDto: { customerId, billId, amount }. (The old
// /bill/addCustomerCreditUsage path does not exist on the backend, which is why this
// previously failed with "Failed to apply credit".)
private suspend fun useCustomerCredit(token: String, creditUsage: Map<String, Any?>): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/customer/payment/credit")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true

        val gson = Gson()
        val jsonBody = gson.toJson(creditUsage)
        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }

        if (connection.responseCode == 200) {
            Pair(true, "Customer credit applied successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Failed to apply credit (${connection.responseCode}): $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, "Failed to apply credit: ${e.message}")
    }
}
