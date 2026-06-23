package com.pos.cosmos.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.pos.cosmos.utils.LocalResponsiveDimensions
import com.pos.cosmos.utils.LocationHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig

// Data classes for All Orders
data class Order(
    val id: Long,
    val orderNo: Int,
    val orderDate: String,
    val customerId: Long,
    val customerName: String,
    val salesRefId: Long,
    val salesRefName: String,
    val total: Double,
    val discountValue: Double,
    val discountPercentage: Double,
    val status: String, // 'P' = Processed, 'A' = Accepted, 'N' = Not Accepted
    val billNo: Int? = null,
    val orderItems: List<OrderItemDetail> = emptyList(),
    val creditCustomerList: List<CreditCustomer> = emptyList(),
    val paymentOption: String = ""
) {
    val netTotal: Double
        get() = total - discountValue

    val statusText: String
        get() = when (status) {
            "P" -> "Processed"
            "A" -> "Accepted"
            else -> "Not Accepted"
        }

    val statusColor: Color
        get() = when (status) {
            "P" -> Color(0xFF4CAF50)
            "A" -> Color(0xFF2196F3)
            else -> Color(0xFFFF9800)
        }
}

data class OrderItemDetail(
    val itemId: Int,
    val itemCode: String,
    val itemName: String,
    val qty: Double,
    val unitPrice: Double,
    val discount: Double,
    val discountValue: Double,
    val discountPercentage: Double,
    val totalPrice: Double,
    val supplierId: Int = 0,
    val invoiceId: Int = 0,
    val freeIssueQty: Double = 0.0
)

data class CreditCustomer(
    val customerId: Long,
    val customerName: String,
    val customerAddress: String = "",
    val amount: Double
)

data class ItemInfo(
    val id: Int,
    val code: String,
    val name: String
)

data class StatusOption(
    val value: String,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllOrdersScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var filteredOrders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var expandedOrderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    // Filters and lookup maps
    var customers by remember { mutableStateOf<List<AllOrdersCustomerOption>>(emptyList()) }
    var salesReps by remember { mutableStateOf<List<AllOrdersSalesRefOption>>(emptyList()) }
    var itemsMap by remember { mutableStateOf<Map<Int, ItemInfo>>(emptyMap()) }
    var customersMap by remember { mutableStateOf<Map<Long, AllOrdersCustomerOption>>(emptyMap()) }
    var salesRepsMap by remember { mutableStateOf<Map<Long, AllOrdersSalesRefOption>>(emptyMap()) }
    var selectedCustomer by remember { mutableStateOf<AllOrdersCustomerOption?>(null) }
    var selectedSalesRep by remember { mutableStateOf<AllOrdersSalesRefOption?>(null) }
    var selectedStatus by remember { mutableStateOf<StatusOption?>(null) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var salesRepSearchQuery by remember { mutableStateOf("") }
    val statusOptions = remember { listOf(
        StatusOption("P", "Processed"),
        StatusOption("A", "Accepted"),
        StatusOption("N", "Not Accepted")
    ) }

    // Dialog states
    var showAcceptDialog by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var acceptingOrder by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Pagination
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val pageSize = remember(screenHeightDp) {
        val itemsPerScreen = (screenHeightDp - 200) / 60
        (itemsPerScreen * 2).coerceIn(20, 100)
    }

    var currentPage by remember { mutableStateOf(0) }
    var isLastPage by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var totalRecords by remember { mutableStateOf(0) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Printing related state
    var isPrinting by remember { mutableStateOf(false) }
    var printStatusMessage by remember { mutableStateOf("") }

    // Config state
    var showFreeIssueInOrderForm by remember { mutableStateOf("") }
    var showPaymentOptionsInOrderForm by remember { mutableStateOf("") }

    // Load orders function (no pagination - loads all orders)
    suspend fun loadOrders() {
        if (token == null) return

        val result = withContext(Dispatchers.IO) {
            fetchAllOrders(token!!, itemsMap, customersMap, salesRepsMap)
        }

        orders = result.orders
        totalRecords = result.totalElements
        isLastPage = true

        // Apply filters
        filteredOrders = orders.filter { order ->
            val matchesCustomer = selectedCustomer == null || order.customerId == selectedCustomer!!.id
            val matchesSalesRep = selectedSalesRep == null || order.salesRefId == selectedSalesRep!!.id
            val matchesStatus = selectedStatus == null || order.status == selectedStatus!!.value
            matchesCustomer && matchesSalesRep && matchesStatus
        }
    }

    // Initial load
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                try {
                    // Fetch config
                    val config = withContext(Dispatchers.IO) { fetchAllOrdersConfig(token!!) }
                    if (config != null) {
                        showFreeIssueInOrderForm = config.showFreeIssueInOrderForm ?: ""
                        showPaymentOptionsInOrderForm = config.showPaymentOptionsInOrderForm ?: ""
                        Log.d("AllOrders", "showFreeIssueInOrderForm: $showFreeIssueInOrderForm")
                        Log.d("AllOrders", "showPaymentOptionsInOrderForm: $showPaymentOptionsInOrderForm")
                    }

                    // Fetch items first (for mapping itemId to code/name)
                    val itemsList = withContext(Dispatchers.IO) { fetchAllItems(token!!) }
                    itemsMap = itemsList.associateBy { it.id }

                    // Fetch customers
                    val customerList = withContext(Dispatchers.IO) { fetchAllOrdersCustomersList(token!!) }
                    customers = customerList
                    customersMap = customerList.associateBy { it.id }

                    // Fetch sales reps
                    val salesRepList = withContext(Dispatchers.IO) { fetchAllOrdersSalesRepsList(token!!) }
                    salesReps = salesRepList
                    salesRepsMap = salesRepList.associateBy { it.id }

                    // Fetch orders (with mapping)
                    loadOrders()
                } catch (e: Exception) {
                    Log.e("AllOrders", "Error loading data: ${e.message}")
                }
                loading = false
            }
        }
    }

    // No infinite scroll needed - all orders loaded at once

    // Apply filters when selection changes
    LaunchedEffect(selectedCustomer, selectedSalesRep, selectedStatus) {
        filteredOrders = orders.filter { order ->
            val matchesCustomer = selectedCustomer == null || order.customerId == selectedCustomer!!.id
            val matchesSalesRep = selectedSalesRep == null || order.salesRefId == selectedSalesRep!!.id
            val matchesStatus = selectedStatus == null || order.status == selectedStatus!!.value
            matchesCustomer && matchesSalesRep && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Orders", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refreshing = true
                        scope.launch {
                            currentPage = 0
                            loadOrders()
                            refreshing = false
                        }
                    }) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
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
                // Filters Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Customer Filter
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Customer", color = Color.White, fontSize = 12.sp)
                            FilterDropdown(
                                value = customerSearchQuery,
                                onValueChange = { customerSearchQuery = it },
                                placeholder = "All Customers",
                                suggestions = customers.filter {
                                    customerSearchQuery.isEmpty() ||
                                    it.name.contains(customerSearchQuery, ignoreCase = true)
                                },
                                onSuggestionSelected = {
                                    selectedCustomer = it
                                    customerSearchQuery = it?.name ?: ""
                                },
                                onClear = {
                                    selectedCustomer = null
                                    customerSearchQuery = ""
                                },
                                displayText = { it?.name ?: "" }
                            )
                        }

                        // Sales Rep Filter
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sales Rep", color = Color.White, fontSize = 12.sp)
                            FilterDropdown(
                                value = salesRepSearchQuery,
                                onValueChange = { salesRepSearchQuery = it },
                                placeholder = "All Sales Reps",
                                suggestions = salesReps.filter {
                                    salesRepSearchQuery.isEmpty() ||
                                    it.name.contains(salesRepSearchQuery, ignoreCase = true)
                                },
                                onSuggestionSelected = {
                                    selectedSalesRep = it
                                    salesRepSearchQuery = it?.name ?: ""
                                },
                                onClear = {
                                    selectedSalesRep = null
                                    salesRepSearchQuery = ""
                                },
                                displayText = { it?.name ?: "" }
                            )
                        }
                    }

                    // Status Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status Filter
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Status", color = Color.White, fontSize = 12.sp)
                            StatusFilterDropdown(
                                selectedStatus = selectedStatus,
                                statusOptions = statusOptions,
                                onStatusSelected = { selectedStatus = it },
                                onClear = { selectedStatus = null }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Total records count
                Text(
                    "Showing ${filteredOrders.size} of $totalRecords orders",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )

                // Orders List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    // Table Header
                    item {
                        OrderTableHeader()
                    }

                    // Order rows
                    items(filteredOrders) { order ->
                        val isExpanded = expandedOrderIds.contains(order.id)

                        OrderRow(
                            order = order,
                            isExpanded = isExpanded,
                            onExpandClick = {
                                expandedOrderIds = if (isExpanded) {
                                    expandedOrderIds - order.id
                                } else {
                                    expandedOrderIds + order.id
                                }
                            }
                        )

                        if (isExpanded) {
                            OrderDetailsCard(
                                order = order,
                                showFreeIssue = showFreeIssueInOrderForm == "Y",
                                showPaymentOption = showPaymentOptionsInOrderForm == "Y",
                                onCreateInvoicePayment = {
                                    // Navigate to CreditBill with order data (like web app)
                                    navController.navigate("credit_bill?orderNo=${order.orderNo}&customerId=${order.customerId}&salesRefId=${order.salesRefId}")
                                },
                                onCreateInvoicePrint = {
                                    selectedOrder = order
                                    showAcceptDialog = true
                                },
                                onAcceptWithoutInvoice = {
                                    selectedOrder = order
                                    // Accept without creating invoice
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            acceptOrder(context, token!!, order, createBill = false)
                                        }
                                        if (result.success) {
                                            successMessage = result.message
                                            showSuccessDialog = true
                                            loadOrders()
                                        } else {
                                            errorMessage = result.message
                                            showErrorDialog = true
                                        }
                                    }
                                },
                                onSmallPrintOrder = {
                                    // Print order without creating invoice
                                    scope.launch {
                                        isPrinting = true
                                        printStatusMessage = "Printing order..."
                                        val printResult = withContext(Dispatchers.IO) {
                                            printOrderReceipt(
                                                context = context,
                                                order = order,
                                                customersMap = customersMap
                                            )
                                        }
                                        isPrinting = false
                                        if (printResult.success) {
                                            successMessage = "Order #${order.orderNo} printed successfully"
                                            showSuccessDialog = true
                                        } else {
                                            errorMessage = printResult.message
                                            showErrorDialog = true
                                        }
                                        printStatusMessage = ""
                                    }
                                },
                                onWhatsApp = {
                                    // Send WhatsApp message via backend API (true copy)
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                sendOrderWhatsApp(token!!, order.orderNo)
                                            }
                                            if (result.success) {
                                                successMessage = result.message
                                                showSuccessDialog = true
                                            } else {
                                                errorMessage = result.message
                                                showErrorDialog = true
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to send WhatsApp: ${e.message}"
                                            showErrorDialog = true
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Loading more indicator
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE94560),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Invoice & Print Dialog
    if (showAcceptDialog && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = {
                if (!acceptingOrder && !isPrinting) {
                    showAcceptDialog = false
                    selectedOrder = null
                    printStatusMessage = ""
                }
            },
            title = { Text("Create Invoice & Print") },
            text = {
                Column {
                    Text("Order #${selectedOrder!!.orderNo}", fontWeight = FontWeight.Bold)
                    Text("Customer: ${selectedOrder!!.customerName}")
                    Text("Net Total: Rs. ${String.format("%.2f", selectedOrder!!.netTotal)}")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (printStatusMessage.isNotEmpty()) {
                        Text(printStatusMessage, color = if (printStatusMessage.contains("Error") || printStatusMessage.contains("Failed")) Color.Red else Color(0xFF4CAF50))
                    } else {
                        Text("Create invoice and print for this order?")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        acceptingOrder = true
                        printStatusMessage = "Creating invoice..."
                        scope.launch {
                            val orderToProcess = selectedOrder!!
                            val result = withContext(Dispatchers.IO) {
                                acceptOrder(context, token!!, orderToProcess, createBill = true)
                            }

                            if (result.success) {
                                printStatusMessage = "Invoice created. Preparing to print..."
                                isPrinting = true

                                // Now print the invoice
                                val printResult = withContext(Dispatchers.IO) {
                                    printInvoice(
                                        context = context,
                                        order = orderToProcess,
                                        billNo = result.billNo ?: 0,
                                        customersMap = customersMap
                                    )
                                }

                                isPrinting = false
                                acceptingOrder = false

                                if (printResult.success) {
                                    showAcceptDialog = false
                                    successMessage = "Invoice #${result.billNo} created and printed successfully"
                                    showSuccessDialog = true
                                    loadOrders()
                                    selectedOrder = null
                                    printStatusMessage = ""
                                } else {
                                    // Invoice created but print failed
                                    printStatusMessage = "Invoice created but print failed: ${printResult.message}"
                                    // Still refresh orders since invoice was created
                                    loadOrders()
                                }
                            } else {
                                acceptingOrder = false
                                showAcceptDialog = false
                                errorMessage = result.message
                                showErrorDialog = true
                                selectedOrder = null
                                printStatusMessage = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = !acceptingOrder && !isPrinting
                ) {
                    if (acceptingOrder || isPrinting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPrinting) "Printing..." else "Creating...")
                        }
                    } else {
                        Text("Create Invoice & Print")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAcceptDialog = false
                        selectedOrder = null
                        printStatusMessage = ""
                    },
                    enabled = !acceptingOrder && !isPrinting
                ) {
                    Text("Cancel")
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
}

@Composable
fun OrderTableHeader() {
    val dimens = LocalResponsiveDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF007BFF))
            .padding(dimens.spacingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Order No", modifier = Modifier.weight(0.8f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = dimens.fontBody2)
        Text("Date", modifier = Modifier.weight(1.2f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = dimens.fontBody2)
        Text("Net Total", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = dimens.fontBody2, textAlign = TextAlign.End)
        Text("Status", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = dimens.fontBody2, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(dimens.iconXLarge))
    }
}

@Composable
fun OrderRow(
    order: Order,
    isExpanded: Boolean,
    onExpandClick: () -> Unit
) {
    val dimens = LocalResponsiveDimensions.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onExpandClick() },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                order.orderNo.toString(),
                modifier = Modifier.weight(0.8f),
                color = Color.White,
                fontSize = dimens.fontBody,
                fontWeight = FontWeight.Bold
            )
            Text(
                formatDate(order.orderDate),
                modifier = Modifier.weight(1.2f),
                color = Color.White,
                fontSize = dimens.fontBody2
            )
            Text(
                String.format("%.2f", order.netTotal),
                modifier = Modifier.weight(1f),
                color = Color(0xFF4CAF50),
                fontSize = dimens.fontBody,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
            Text(
                order.statusText,
                modifier = Modifier
                    .weight(1f)
                    .background(order.statusColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = dimens.spacingSmall, vertical = dimens.spacingTiny),
                color = order.statusColor,
                fontSize = dimens.fontCaption,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand",
                tint = Color(0xFFE94560),
                modifier = Modifier.size(dimens.iconNormal)
            )
        }
    }
}

@Composable
fun OrderDetailsCard(
    order: Order,
    onCreateInvoicePayment: () -> Unit,
    onCreateInvoicePrint: () -> Unit,
    onAcceptWithoutInvoice: () -> Unit,
    onSmallPrintOrder: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    showFreeIssue: Boolean = false,
    showPaymentOption: Boolean = false
) {
    val dimens = LocalResponsiveDimensions.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimens.spacingSmall, end = dimens.spacingSmall, bottom = dimens.spacingSmall),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252547))
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMedium)) {
            // Order Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Order No: ${order.orderNo}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = dimens.fontSubtitle)
                    if (order.billNo != null && order.billNo > 0) {
                        Text("Bill No: ${order.billNo}", color = Color(0xFF4CAF50), fontSize = dimens.fontBody2)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Customer: ${order.customerName}", color = Color.White, fontSize = dimens.fontBody2)
                    Text("Sales Rep: ${order.salesRefName}", color = Color.Gray, fontSize = dimens.fontCaption)
                    if (showPaymentOption && order.paymentOption.isNotEmpty()) {
                        Text("Payment: ${order.paymentOption}", color = Color(0xFF2196F3), fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Credit Customers (if any)
            if (order.creditCustomerList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(dimens.spacingSmall))
                Text("Credit Customers:", color = Color(0xFF2196F3), fontSize = dimens.fontBody2, fontWeight = FontWeight.Bold)
                order.creditCustomerList.forEach { cc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${cc.customerName}", color = Color.White, fontSize = dimens.fontCaption)
                        Text("Rs. ${String.format("%.2f", cc.amount)}", color = Color(0xFF4CAF50), fontSize = dimens.fontCaption)
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingMedium))

            // Items Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF007BFF).copy(alpha = 0.5f))
                    .padding(dimens.spacingSmall)
            ) {
                Text("Code", modifier = Modifier.weight(0.8f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold)
                Text("Name", modifier = Modifier.weight(1.5f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold)
                Text("Qty", modifier = Modifier.weight(0.5f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                if (showFreeIssue) {
                    Text("Free", modifier = Modifier.weight(0.5f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
                Text("Price", modifier = Modifier.weight(0.7f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Total", modifier = Modifier.weight(0.8f), color = Color.White, fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }

            // Items
            order.orderItems.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimens.spacingTiny, horizontal = dimens.spacingSmall)
                ) {
                    Text(item.itemCode, modifier = Modifier.weight(0.8f), color = Color.White, fontSize = dimens.fontCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.itemName, modifier = Modifier.weight(1.5f), color = Color.White, fontSize = dimens.fontCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (item.qty % 1 == 0.0) item.qty.toInt().toString() else String.format("%.2f", item.qty),
                        modifier = Modifier.weight(0.5f),
                        color = Color.White,
                        fontSize = dimens.fontCaption,
                        textAlign = TextAlign.End
                    )
                    if (showFreeIssue) {
                        Text(
                            if (item.freeIssueQty % 1 == 0.0) item.freeIssueQty.toInt().toString() else String.format("%.2f", item.freeIssueQty),
                            modifier = Modifier.weight(0.5f),
                            color = Color(0xFF9C27B0),
                            fontSize = dimens.fontCaption,
                            textAlign = TextAlign.End
                        )
                    }
                    Text(String.format("%.2f", item.unitPrice), modifier = Modifier.weight(0.7f), color = Color.White, fontSize = dimens.fontCaption, textAlign = TextAlign.End)
                    Text(String.format("%.2f", item.totalPrice), modifier = Modifier.weight(0.8f), color = Color(0xFF4CAF50), fontSize = dimens.fontCaption, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                }
            }

            // Totals
            Divider(modifier = Modifier.padding(vertical = dimens.spacingSmall), color = Color.White.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Gross Total:", color = Color.White, fontSize = dimens.fontBody2)
                Text(String.format("Rs. %.2f", order.total), color = Color.White, fontSize = dimens.fontBody2)
            }
            if (order.discountValue > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Discount:", color = Color(0xFFFF9800), fontSize = dimens.fontBody2)
                    Text(String.format("Rs. %.2f", order.discountValue), color = Color(0xFFFF9800), fontSize = dimens.fontBody2)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Net Total:", color = Color(0xFF4CAF50), fontSize = dimens.fontSubtitle, fontWeight = FontWeight.Bold)
                Text(String.format("Rs. %.2f", order.netTotal), color = Color(0xFF4CAF50), fontSize = dimens.fontSubtitle, fontWeight = FontWeight.Bold)
            }

            // Action buttons (only for non-processed orders)
            if (order.status != "P") {
                Spacer(modifier = Modifier.height(dimens.spacingMedium))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
                ) {
                    // Create Invoice & Payment button
                    Button(
                        onClick = onCreateInvoicePayment,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                    ) {
                        Text("Create Invoice & Payment", fontSize = dimens.fontBody2)
                    }

                    // Create Invoice & Print button
                    Button(
                        onClick = onCreateInvoicePrint,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Create Invoice & Print", fontSize = dimens.fontBody2)
                    }

                    // Accept Order without create Invoice button
                    Button(
                        onClick = onAcceptWithoutInvoice,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Accept Order without create Invoice", fontSize = dimens.fontBody2)
                    }
                }
            }

            // Small Print and WhatsApp buttons (always visible)
            Spacer(modifier = Modifier.height(dimens.spacingSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSmall)
            ) {
                // Small Print Order button
                Button(
                    onClick = onSmallPrintOrder,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                ) {
                    Text("Small Print", fontSize = dimens.fontBody2)
                }

                // WhatsApp button
                Button(
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Text("WhatsApp", fontSize = dimens.fontBody2)
                }
            }
        }
    }
}

@Composable
fun <T> FilterDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<T>,
    onSuggestionSelected: (T?) -> Unit,
    onClear: () -> Unit,
    displayText: (T?) -> String
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.isNotEmpty()
            },
            placeholder = { Text(placeholder, fontSize = 12.sp, color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007BFF),
                unfocusedBorderColor = Color.Gray
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            singleLine = true,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                        Text("x", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
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
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusFilterDropdown(
    selectedStatus: StatusOption?,
    statusOptions: List<StatusOption>,
    onStatusSelected: (StatusOption?) -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = selectedStatus?.label ?: "",
            onValueChange = { },
            placeholder = { Text("All Status", fontSize = 12.sp, color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { expanded = !expanded },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF007BFF),
                unfocusedBorderColor = Color.Gray
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            singleLine = true,
            readOnly = true,
            trailingIcon = {
                Row {
                    if (selectedStatus != null) {
                        IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
                            Text("x", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        )

        if (expanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                LazyColumn {
                    items(statusOptions) { status ->
                        Text(
                            text = status.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusSelected(status)
                                    expanded = false
                                }
                                .padding(12.dp),
                            fontSize = 12.sp,
                            color = when (status.value) {
                                "P" -> Color(0xFF4CAF50)
                                "A" -> Color(0xFF2196F3)
                                else -> Color(0xFFFF9800)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format date
private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateString.take(19))
        outputFormat.format(date!!)
    } catch (e: Exception) {
        dateString.take(10)
    }
}

// API Functions
data class OrdersResult(
    val orders: List<Order>,
    val totalElements: Int,
    val isLastPage: Boolean
)

// Result class for acceptOrder that includes bill number
data class AcceptOrderResult(
    val success: Boolean,
    val message: String,
    val billNo: Int? = null
)

// Result class for print operation
data class PrintResult(
    val success: Boolean,
    val message: String
)

// Function to print order receipt via Bluetooth thermal printer (without creating invoice)
private fun printOrderReceipt(
    context: Context,
    order: Order,
    customersMap: Map<Long, AllOrdersCustomerOption>
): PrintResult {
    return try {
        Log.d("AllOrders", "Starting print for Order #${order.orderNo}")

        // Get PrinterService singleton
        val printerService = PrinterService.getInstance()

        // Check if printer is connected, if not try to connect
        if (!printerService.isConnected()) {
            Log.d("AllOrders", "Printer not connected, attempting to connect...")

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return PrintResult(false, "Bluetooth is not enabled")
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
                return PrintResult(false, "No paired printer found. Please pair a printer first.")
            }

            Log.d("AllOrders", "Connecting to printer: ${printerDevice.name}")
            val connected = printerService.connectDevice(printerDevice)
            if (!connected) {
                return PrintResult(false, "Failed to connect to printer: ${printerDevice.name}")
            }
        }

        // Get bill template from cache (uses same configs as web app)
        val template = BillTemplateCache.getTemplate(context)

        // Build receipt items from order items
        val receiptItems = order.orderItems.map { item ->
            ReceiptItem(
                name = item.itemName,
                quantity = item.qty.toInt(),
                price = item.unitPrice - item.discountValue,
                amount = item.totalPrice
            )
        }

        // Get customer info
        val customer = customersMap[order.customerId]

        // Build ReceiptData with template settings (same as web app)
        val receiptData = ReceiptData(
            items = receiptItems,
            customer = order.customerName.ifEmpty { customer?.name ?: "Walk-in" },
            salesRep = order.salesRefName,
            grossTotal = order.total,
            discount = order.discountValue,
            netTotal = order.netTotal,
            cashPaid = 0.0,
            billNo = order.orderNo.toString(),
            isOrder = true,
            companyName = template?.get("companyName")?.asString ?: "COSMOS POS",
            address = template?.get("address")?.asString ?: "",
            contact = template?.get("contact")?.asString ?: "",
            email = template?.get("emailAddress")?.asString ?: "",
            companyNameFont = template?.get("companyNameFont")?.asInt ?: 14,
            addressFont = template?.get("addressFont")?.asInt ?: 8,
            contactFont = template?.get("contactFont")?.asInt ?: 8,
            emailFont = template?.get("emailFont")?.asInt ?: 8,
            lastLineFont = template?.get("lastLineFont")?.asInt ?: 10,
            companyGap = template?.get("companyGap")?.asInt ?: 0,
            addressGap = template?.get("addressGap")?.asInt ?: 0,
            contactGap = template?.get("contactGap")?.asInt ?: 0,
            emailGap = template?.get("emailGap")?.asInt ?: 0,
            lastLineGap = template?.get("lastLineGap")?.asInt ?: 0,
            upperGap = template?.get("upperGap")?.asInt ?: 0,
            leftGap = template?.get("leftGap")?.asInt ?: 0,
            logoDataUrl = template?.get("imageDataUrl")?.asString ?: "",
            logoLeft = template?.get("logoLeft")?.asInt ?: 0,
            logoUpper = template?.get("logoUpper")?.asInt ?: 0,
            logoWidth = template?.get("logoWidth")?.asInt ?: 0,
            logoHeight = template?.get("logoHeight")?.asInt ?: 0,
            lastLine = template?.get("lastLine")?.asString ?: ""
        )

        // Generate PDF and print
        val pdfGenerator = PdfReceiptGenerator(context)
        val printSuccess = printerService.printPdfReceipt(pdfGenerator, receiptData)

        if (printSuccess) {
            Log.d("AllOrders", "Print successful for Order #${order.orderNo}")
            PrintResult(true, "Printed successfully")
        } else {
            Log.e("AllOrders", "Print failed for Order #${order.orderNo}")
            PrintResult(false, "Print failed")
        }
    } catch (e: SecurityException) {
        Log.e("AllOrders", "Bluetooth permission denied: ${e.message}")
        PrintResult(false, "Bluetooth permission denied. Please grant Bluetooth permissions.")
    } catch (e: Exception) {
        Log.e("AllOrders", "Error printing order: ${e.message}", e)
        PrintResult(false, "Error: ${e.message}")
    }
}

// Function to print invoice via Bluetooth thermal printer
private fun printInvoice(
    context: Context,
    order: Order,
    billNo: Int,
    customersMap: Map<Long, AllOrdersCustomerOption>
): PrintResult {
    return try {
        Log.d("AllOrders", "Starting print for Bill #$billNo")

        // Get PrinterService singleton
        val printerService = PrinterService.getInstance()

        // Check if printer is connected, if not try to connect
        if (!printerService.isConnected()) {
            Log.d("AllOrders", "Printer not connected, attempting to connect...")

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return PrintResult(false, "Bluetooth is not enabled")
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
                return PrintResult(false, "No paired printer found. Please pair a printer first.")
            }

            Log.d("AllOrders", "Connecting to printer: ${printerDevice.name}")
            val connected = printerService.connectDevice(printerDevice)
            if (!connected) {
                return PrintResult(false, "Failed to connect to printer: ${printerDevice.name}")
            }
        }

        // Get bill template from cache
        val template = BillTemplateCache.getTemplate(context)

        // Build receipt items from order items
        val receiptItems = order.orderItems.map { item ->
            ReceiptItem(
                name = item.itemName,
                quantity = item.qty.toInt(),
                price = item.unitPrice - item.discountValue,
                amount = item.totalPrice
            )
        }

        // Get customer info
        val customer = customersMap[order.customerId]

        // Build ReceiptData with template settings
        val receiptData = ReceiptData(
            items = receiptItems,
            customer = order.customerName.ifEmpty { customer?.name ?: "Walk-in" },
            salesRep = order.salesRefName,
            grossTotal = order.total,
            discount = order.discountValue,
            netTotal = order.netTotal,
            cashPaid = 0.0, // Credit bill, no cash paid
            billNo = billNo.toString(),
            companyName = template?.get("companyName")?.asString ?: "COSMOS POS",
            address = template?.get("address")?.asString ?: "",
            contact = template?.get("contact")?.asString ?: "",
            email = template?.get("emailAddress")?.asString ?: "",
            companyNameFont = template?.get("companyNameFont")?.asInt ?: 14,
            addressFont = template?.get("addressFont")?.asInt ?: 8,
            contactFont = template?.get("contactFont")?.asInt ?: 8,
            emailFont = template?.get("emailFont")?.asInt ?: 8,
            lastLineFont = template?.get("lastLineFont")?.asInt ?: 10,
            companyGap = template?.get("companyGap")?.asInt ?: 0,
            addressGap = template?.get("addressGap")?.asInt ?: 0,
            contactGap = template?.get("contactGap")?.asInt ?: 0,
            emailGap = template?.get("emailGap")?.asInt ?: 0,
            lastLineGap = template?.get("lastLineGap")?.asInt ?: 0,
            upperGap = template?.get("upperGap")?.asInt ?: 0,
            leftGap = template?.get("leftGap")?.asInt ?: 0,
            logoDataUrl = template?.get("imageDataUrl")?.asString ?: "",
            logoLeft = template?.get("logoLeft")?.asInt ?: 0,
            logoUpper = template?.get("logoUpper")?.asInt ?: 0,
            logoWidth = template?.get("logoWidth")?.asInt ?: 0,
            logoHeight = template?.get("logoHeight")?.asInt ?: 0,
            lastLine = template?.get("lastLine")?.asString ?: ""
        )

        // Generate PDF and print
        val pdfGenerator = PdfReceiptGenerator(context)
        val printSuccess = printerService.printPdfReceipt(pdfGenerator, receiptData)

        if (printSuccess) {
            Log.d("AllOrders", "Print successful for Bill #$billNo")
            PrintResult(true, "Printed successfully")
        } else {
            Log.e("AllOrders", "Print failed for Bill #$billNo")
            PrintResult(false, "Print failed")
        }
    } catch (e: SecurityException) {
        Log.e("AllOrders", "Bluetooth permission denied: ${e.message}")
        PrintResult(false, "Bluetooth permission denied. Please grant Bluetooth permissions.")
    } catch (e: Exception) {
        Log.e("AllOrders", "Error printing invoice: ${e.message}", e)
        PrintResult(false, "Error: ${e.message}")
    }
}

private suspend fun fetchAllOrders(
    token: String,
    itemsMap: Map<Int, ItemInfo>,
    customersMap: Map<Long, AllOrdersCustomerOption>,
    salesRepsMap: Map<Long, AllOrdersSalesRefOption>
): OrdersResult {
    return try {
        // API returns all orders without pagination
        val connection = URL("${ApiConfig.BASE_URL_8080}/order/getAllOrders")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("AllOrders", "Response: ${content.take(500)}")
            val gson = Gson()

            // Response is a simple array: [{order: {...}, creditCustomerList: [...]}, ...]
            @Suppress("UNCHECKED_CAST")
            val ordersList = gson.fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()

            val totalElements = ordersList.size
            val isLast = true // No pagination, all orders returned at once

            val orders = ordersList.mapNotNull { orderMap ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    val orderData = orderMap["order"] as? Map<String, Any> ?: orderMap

                    val id = (orderData["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val orderNo = (orderData["orderNo"] as? Number)?.toInt() ?: 0
                    val orderDate = orderData["orderDate"] as? String ?: ""
                    val customerId = (orderData["customerId"] as? Number)?.toLong() ?: 0
                    val salesRefId = (orderData["salesRefId"] as? Number)?.toLong() ?: 0
                    val status = orderData["status"] as? String ?: "N"
                    val billNo = (orderData["billNo"] as? Number)?.toInt()
                    val discountValue = (orderData["discountValue"] as? Number)?.toDouble() ?: 0.0
                    val discountPercentage = (orderData["discountPercentage"] as? Number)?.toDouble() ?: 0.0
                    val paymentOption = orderData["paymentOption"] as? String ?: ""

                    // Map customerId and salesRefId to names
                    val customerName = customersMap[customerId]?.name ?: ""
                    val salesRefName = salesRepsMap[salesRefId]?.name ?: ""

                    // Parse order items and map itemId to code/name
                    @Suppress("UNCHECKED_CAST")
                    val itemsList = orderData["orderItems"] as? List<Map<String, Any>> ?: emptyList()
                    var total = 0.0

                    val orderItems = itemsList.map { itemMap ->
                        val itemId = (itemMap["itemId"] as? Number)?.toInt() ?: 0
                        val itemInfo = itemsMap[itemId]
                        val qty = (itemMap["qty"] as? Number)?.toDouble() ?:
                                  (itemMap["qty"] as? String)?.toDoubleOrNull() ?: 0.0
                        val unitPrice = (itemMap["unitPrice"] as? Number)?.toDouble() ?: 0.0
                        val itemDiscountValue = (itemMap["discountValue"] as? Number)?.toDouble() ?: 0.0
                        val itemTotalPrice = qty * (unitPrice - itemDiscountValue)
                        total += itemTotalPrice

                        OrderItemDetail(
                            itemId = itemId,
                            itemCode = itemInfo?.code ?: "",
                            itemName = itemInfo?.name ?: "Unknown Item",
                            qty = qty,
                            unitPrice = unitPrice,
                            discount = (itemMap["discount"] as? Number)?.toDouble() ?: 0.0,
                            discountValue = itemDiscountValue,
                            discountPercentage = (itemMap["discountPercentage"] as? Number)?.toDouble() ?: 0.0,
                            totalPrice = itemTotalPrice,
                            supplierId = (itemMap["supplierId"] as? Number)?.toInt() ?: 0,
                            invoiceId = (itemMap["invoiceId"] as? Number)?.toInt() ?: 0,
                            freeIssueQty = (itemMap["freeIssueQty"] as? Number)?.toDouble() ?: 0.0
                        )
                    }

                    // Parse credit customer list and map names
                    @Suppress("UNCHECKED_CAST")
                    val creditList = (orderMap["creditCustomerList"] as? List<Map<String, Any>>)?.map { ccMap ->
                        val ccCustomerId = (ccMap["customerId"] as? Number)?.toLong() ?: 0
                        val ccCustomer = customersMap[ccCustomerId]
                        CreditCustomer(
                            customerId = ccCustomerId,
                            customerName = ccCustomer?.name ?: "",
                            customerAddress = ccCustomer?.address ?: "",
                            amount = (ccMap["amount"] as? Number)?.toDouble() ?: 0.0
                        )
                    } ?: emptyList()

                    Order(
                        id = id,
                        orderNo = orderNo,
                        orderDate = orderDate,
                        customerId = customerId,
                        customerName = customerName,
                        salesRefId = salesRefId,
                        salesRefName = salesRefName,
                        total = if (total > 0) total else (orderData["total"] as? Number)?.toDouble() ?: 0.0,
                        discountValue = discountValue,
                        discountPercentage = discountPercentage,
                        status = status,
                        billNo = billNo,
                        orderItems = orderItems,
                        creditCustomerList = creditList,
                        paymentOption = paymentOption
                    )
                } catch (e: Exception) {
                    Log.e("AllOrders", "Error parsing order: ${e.message}")
                    null
                }
            }

            OrdersResult(orders, totalElements, isLast)
        } else {
            Log.e("AllOrders", "Failed to fetch orders: ${connection.responseCode}")
            OrdersResult(emptyList(), 0, true)
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error fetching orders: ${e.message}")
        OrdersResult(emptyList(), 0, true)
    }
}

private suspend fun acceptOrder(context: Context, token: String, order: Order, createBill: Boolean): AcceptOrderResult {
    return try {
        val endpoint = if (createBill) {
            "${ApiConfig.BASE_URL_8080}/order/acceptOrder"
        } else {
            "${ApiConfig.BASE_URL_8080}/order/acceptOrderWithoutCreatingBill"
        }

        // Get current location for user event
        val location = LocationHelper.getCurrentLocation(context)

        // Build order object
        val orderObject = JsonObject().apply {
            addProperty("id", order.id)
            addProperty("orderNo", order.orderNo)
            addProperty("orderDate", order.orderDate)
            addProperty("customerId", order.customerId)
            addProperty("customerName", order.customerName)
            addProperty("salesRefId", order.salesRefId)
            addProperty("salesRefName", order.salesRefName)
            addProperty("total", order.total)
            addProperty("discountValue", order.discountValue)
            addProperty("discountPercentage", order.discountPercentage)
            addProperty("status", "A")

            val itemsArray = JsonArray()
            order.orderItems.forEach { item ->
                val itemObj = JsonObject().apply {
                    addProperty("itemId", item.itemId)
                    addProperty("itemCode", item.itemCode)
                    addProperty("itemName", item.itemName)
                    addProperty("qty", item.qty.toInt().toString())
                    addProperty("unitPrice", item.unitPrice)
                    addProperty("discount", item.discount)
                    addProperty("discountValue", item.discountValue)
                    addProperty("discountPercentage", item.discountPercentage)
                    addProperty("totalPrice", item.totalPrice)
                    addProperty("supplierId", item.supplierId)
                    addProperty("invoiceId", item.invoiceId)
                }
                itemsArray.add(itemObj)
            }
            add("orderItems", itemsArray)
        }

        // Build full payload
        val payload = JsonObject().apply {
            add("userEvent", JsonObject().apply {
                addProperty("event", "Accept Order ${order.orderNo}")
                addProperty("latitude", location.latitude.toString())
                addProperty("longitude", location.longitude.toString())
                addProperty("userName", "")
                addProperty("eventTime", "")
            })
            add("order", orderObject)
            add("creditCustomerList", JsonArray().apply {
                order.creditCustomerList.forEach { cc ->
                    add(JsonObject().apply {
                        addProperty("customerId", cc.customerId)
                        addProperty("customerName", cc.customerName)
                        addProperty("customerAddress", cc.customerAddress)
                        addProperty("amount", cc.amount)
                    })
                }
            })
        }

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = payload.toString()
        Log.d("AllOrders", "Accepting order: $jsonString")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("AllOrders", "Order accepted: $responseBody")

            // Parse response to get bill number
            var billNo: Int? = null
            try {
                val gson = Gson()
                val responseJson = gson.fromJson(responseBody, JsonObject::class.java)
                // The response contains the order object with billNo
                billNo = responseJson.get("billNo")?.asInt
                Log.d("AllOrders", "Bill number from response: $billNo")
            } catch (e: Exception) {
                Log.e("AllOrders", "Error parsing bill number: ${e.message}")
            }

            AcceptOrderResult(
                success = true,
                message = "Order #${order.orderNo} accepted successfully",
                billNo = billNo
            )
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("AllOrders", "Failed to accept order: ${connection.responseCode} - $errorResponse")
            AcceptOrderResult(
                success = false,
                message = "Failed to accept order: $errorResponse"
            )
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error accepting order: ${e.message}")
        AcceptOrderResult(
            success = false,
            message = "Error: ${e.message}"
        )
    }
}

// Customer data class for AllOrders
data class AllOrdersCustomerOption(
    val id: Long,
    val name: String,
    val address: String = "",
    val telephone: String = ""
)

// Sales Rep data class for AllOrders
data class AllOrdersSalesRefOption(
    val id: Long,
    val name: String
)

private suspend fun fetchAllOrdersCustomersList(token: String): List<AllOrdersCustomerOption> {
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

                AllOrdersCustomerOption(
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
        Log.e("AllOrders", "Error fetching customers: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchAllOrdersSalesRepsList(token: String): List<AllOrdersSalesRefOption> {
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

                AllOrdersSalesRefOption(id = id, name = name)
            } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error fetching sales reps: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchAllItems(token: String): List<ItemInfo> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("AllOrders", "Items response: ${content.take(200)}")
            val gson = Gson()

            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>

            list?.mapNotNull { itemMap ->
                val id = (itemMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                val code = itemMap["code"] as? String ?: ""
                val name = itemMap["name"] as? String ?: ""

                ItemInfo(id = id, code = code, name = name)
            } ?: emptyList()
        } else {
            Log.e("AllOrders", "Failed to fetch items: ${connection.responseCode}")
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error fetching items: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchAllOrdersConfig(token: String): com.pos.cosmos.models.Config? {
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
            Log.e("AllOrders", "Failed to fetch config: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error fetching config: ${e.message}")
        null
    }
}

data class WhatsAppResult(
    val success: Boolean,
    val message: String
)

private suspend fun sendOrderWhatsApp(token: String, orderNo: Int): WhatsAppResult {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/order/sendWhatsApp?orderNo=$orderNo")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true

        // Empty body for POST
        connection.outputStream.bufferedWriter().use { it.write("") }

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("AllOrders", "WhatsApp response: $content")

            val gson = Gson()
            val response = gson.fromJson(content, JsonObject::class.java)

            val sent = response.get("sent")?.asBoolean ?: false
            val channel = response.get("channel")?.asString ?: ""
            val recipient = response.get("recipient")?.asString ?: ""
            val errorMessage = response.get("message")?.asString ?: ""

            if (sent) {
                WhatsAppResult(true, "WhatsApp sent successfully to $recipient")
            } else {
                WhatsAppResult(false, errorMessage.ifEmpty { "Failed to send WhatsApp" })
            }
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Log.e("AllOrders", "WhatsApp failed: ${connection.responseCode} - $errorResponse")
            WhatsAppResult(false, "Failed to send WhatsApp: ${connection.responseCode}")
        }
    } catch (e: Exception) {
        Log.e("AllOrders", "Error sending WhatsApp: ${e.message}")
        WhatsAppResult(false, "Error: ${e.message}")
    }
}
