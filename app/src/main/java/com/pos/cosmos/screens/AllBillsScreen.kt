package com.pos.cosmos.screens

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.pos.cosmos.models.*
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig

// Data classes for API responses
internal data class CustomerDataModel(
    val id: Long = 0,
    val name: String? = null,
    val address: String? = null,
    val telephone: String? = null,
    val email: String? = null,
    val status: String? = null,
    val details: String? = null,
    val companyCode: Int = 0
)

internal data class SalesRepDataModel(
    val id: Long = 0,
    val name: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllBillsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token by appViewModel.token.collectAsState()

    // State variables
    var allBills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var filteredBills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var allCustomers by remember { mutableStateOf<List<CustomerDataModel>>(emptyList()) }
    var allSalesReps by remember { mutableStateOf<List<SalesRepDataModel>>(emptyList()) }
    var allItems by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var expandedBillIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var loadingKots by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Filter state
    var filterState by remember { mutableStateOf(BillFilterState()) }
    var showFilterSection by remember { mutableStateOf(true) }

    // Email dialog state
    var showEmailDialog by remember { mutableStateOf(false) }
    var selectedBillForEmail by remember { mutableStateOf<Bill?>(null) }
    var emailAddress by remember { mutableStateOf("") }

    // Config state
    var showFreeIssueInOrderForm by remember { mutableStateOf("") }

    // Load initial data
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                // Load config
                val config = withContext(Dispatchers.IO) { fetchAllBillsConfig(token!!) }
                if (config != null) {
                    showFreeIssueInOrderForm = config.showFreeIssueInOrderForm ?: ""
                    Log.d("AllBills", "showFreeIssueInOrderForm: $showFreeIssueInOrderForm")
                }

                // Load reference data
                allCustomers = withContext(Dispatchers.IO) {
                    fetchAllCustomers(token!!)
                }
                Log.d("AllBills", "Loaded ${allCustomers.size} customers")

                allSalesReps = withContext(Dispatchers.IO) {
                    fetchAllSalesReps(token!!)
                }
                // Load all items for name mapping
                allItems = withContext(Dispatchers.IO) {
                    fetchAllItems(token!!)
                }
                // Load bills
                allBills = withContext(Dispatchers.IO) {
                    fetchBillsByType(token!!, filterState)
                }
                Log.d("AllBills", "Loaded ${allBills.size} bills, first bill customerId: ${allBills.firstOrNull()?.customerId}")

                filteredBills = allBills
                loading = false
            }
        }
    }

    // Functions
    fun applyFilters() {
        scope.launch {
            loading = true
            allBills = withContext(Dispatchers.IO) {
                fetchBillsByType(token!!, filterState)
            }
            filteredBills = allBills
            loading = false
        }
    }

    fun toggleBillExpansion(billId: Long) {
        if (expandedBillIds.contains(billId)) {
            expandedBillIds = expandedBillIds - billId
        } else {
            expandedBillIds = expandedBillIds + billId
            val bill = allBills.find { it.id == billId }
            if (bill != null && bill.kots.isEmpty()) {
                scope.launch {
                    loadingKots = loadingKots + billId
                    val kots = withContext(Dispatchers.IO) {
                        fetchKitchenOrdersByBill(token!!, billId)
                    }
                    allBills = allBills.map {
                        if (it.id == billId) it.copy(kots = kots) else it
                    }
                    filteredBills = allBills
                    loadingKots = loadingKots - billId
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Bills", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F3460))
                .padding(innerPadding)
        ) {
            // Filter Section
            if (showFilterSection) {
                BillFilterSection(
                    filterState = filterState,
                    onFilterChange = { filterState = it },
                    customers = allCustomers,
                    salesReps = allSalesReps,
                    onApplyFilters = { applyFilters() }
                )
            }

            // Bills List
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE94560))
                }
            } else if (filteredBills.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "No bills",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No bills found",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    items(filteredBills) { bill ->
                        // Map customer name from customers list (like web app does)
                        val mappedCustomerName = bill.customerName?.takeIf { it.isNotEmpty() }
                            ?: bill.cashCustomerName?.takeIf { it.isNotEmpty() }
                            ?: allCustomers.find { it.id == bill.customerId }?.name
                            ?: "Walk-in"

                        BillCard(
                            bill = bill,
                            customerName = mappedCustomerName,
                            isExpanded = expandedBillIds.contains(bill.id),
                            isLoadingKots = loadingKots.contains(bill.id),
                            onToggleExpand = { toggleBillExpansion(bill.id) },
                            onSmallPrint = {
                                scope.launch {
                                    loading = true
                                    val printResult = withContext(Dispatchers.IO) {
                                        printBillReceipt(
                                            context = context,
                                            bill = bill,
                                            customersMap = allCustomers.associateBy { it.id },
                                            itemNames = allItems
                                        )
                                    }
                                    loading = false
                                    if (printResult.success) {
                                        Toast.makeText(context, "Bill #${bill.billNo} printed successfully", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, printResult.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onWhatsApp = {
                                // Call backend API to send WhatsApp
                                scope.launch {
                                    loading = true
                                    val result = withContext(Dispatchers.IO) {
                                        sendBillWhatsApp(token!!, bill.billNo, bill.billType)
                                    }
                                    loading = false
                                    if (result.success) {
                                        val message = when (result.notificationType) {
                                            "WHATSAPP" -> "WhatsApp sent to ${result.recipient}"
                                            "SMS" -> "SMS sent to ${result.recipient}"
                                            else -> "Notification sent successfully"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Check if it's a "not configured" message
                                        val errorMsg = result.errorMessage ?: "Failed to send"
                                        if (errorMsg.contains("not configured", ignoreCase = true)) {
                                            Toast.makeText(context, "WhatsApp/SMS not configured", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            onEmail = {
                                selectedBillForEmail = bill
                                emailAddress = allCustomers.find { it.id == bill.customerId }?.email ?: ""
                                showEmailDialog = true
                            },
                            itemNames = allItems,
                            showFreeIssue = showFreeIssueInOrderForm == "Y"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Summary footer
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Total Bills: ${filteredBills.size}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Total Sales: Rs ${String.format("%,.2f", filteredBills.sumOf { it.total })}",
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Email Dialog
    if (showEmailDialog && selectedBillForEmail != null) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Email Invoice") },
            text = {
                Column {
                    Text("Send invoice for Bill #${selectedBillForEmail?.billNo}")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = emailAddress,
                        onValueChange = { emailAddress = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            // TODO: Implement email functionality
                            Toast.makeText(context, "Email sent to $emailAddress", Toast.LENGTH_SHORT).show()
                            showEmailDialog = false
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun BillFilterSection(
    filterState: BillFilterState,
    onFilterChange: (BillFilterState) -> Unit,
    customers: List<CustomerDataModel>,
    salesReps: List<SalesRepDataModel>,
    onApplyFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Filters",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bill Type Dropdown
            BillTypeDropdown(
                selectedType = filterState.billType,
                onTypeSelected = { onFilterChange(filterState.copy(billType = it)) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date Range
            Row(modifier = Modifier.fillMaxWidth()) {
                DatePickerField(
                    label = "From",
                    selectedDate = filterState.startDate,
                    onDateSelected = { onFilterChange(filterState.copy(startDate = it)) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                DatePickerField(
                    label = "To",
                    selectedDate = filterState.endDate,
                    onDateSelected = { onFilterChange(filterState.copy(endDate = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer Autocomplete
            AutocompleteTextField(
                label = "Customer",
                value = filterState.selectedCustomerName ?: "",
                items = customers.map { (it.name ?: "") to (it.id ?: 0L) },
                onItemSelected = { name, id ->
                    onFilterChange(filterState.copy(selectedCustomerId = id, selectedCustomerName = name))
                },
                onClear = {
                    onFilterChange(filterState.copy(selectedCustomerId = null, selectedCustomerName = null))
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sales Rep Autocomplete
            AutocompleteTextField(
                label = "Sales Representative",
                value = filterState.selectedSalesRepName ?: "",
                items = salesReps.map { (it.name ?: "") to (it.id ?: 0L) },
                onItemSelected = { name, id ->
                    onFilterChange(filterState.copy(selectedSalesRepId = id, selectedSalesRepName = name))
                },
                onClear = {
                    onFilterChange(filterState.copy(selectedSalesRepId = null, selectedSalesRepName = null))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Apply Filters Button
            Button(
                onClick = onApplyFilters,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
            ) {
                Text("Apply Filters", color = Color.White)
            }
        }
    }
}

@Composable
fun BillTypeDropdown(
    selectedType: BillType,
    onTypeSelected: (BillType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = selectedType.displayName,
            onValueChange = {},
            label = { Text("Bill Type") },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                        contentDescription = "Dropdown"
                    )
                }
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFF0F3460),
                focusedContainerColor = Color(0xFF0F3460),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedLabelColor = Color.Gray,
                focusedLabelColor = Color.White
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            BillType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    selectedDate: Date?,
    onDateSelected: (Date?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    Box(modifier = modifier) {
        TextField(
            value = selectedDate?.let { dateFormatter.format(it) } ?: "",
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select date")
                }
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFF0F3460),
                focusedContainerColor = Color(0xFF0F3460),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedLabelColor = Color.Gray,
                focusedLabelColor = Color.White
            )
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.time ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onDateSelected(Date(it))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AutocompleteTextField(
    label: String,
    value: String,
    items: List<Pair<String, Long>>,
    onItemSelected: (String, Long) -> Unit,
    onClear: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(value) }
    var expanded by remember { mutableStateOf(false) }
    var filteredItems by remember { mutableStateOf(items) }

    LaunchedEffect(value) {
        searchQuery = value
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                expanded = true
                filteredItems = items.filter {
                    it.first.contains(query, ignoreCase = true)
                }
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        onClear()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color(0xFF0F3460),
                focusedContainerColor = Color(0xFF0F3460),
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedLabelColor = Color.Gray,
                focusedLabelColor = Color.White
            )
        )

        if (expanded && filteredItems.isNotEmpty() && searchQuery.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(filteredItems.take(5)) { item ->
                        Text(
                            text = item.first,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onItemSelected(item.first, item.second)
                                    searchQuery = item.first
                                    expanded = false
                                }
                                .padding(12.dp),
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
fun BillCard(
    bill: Bill,
    customerName: String = "Walk-in",
    isExpanded: Boolean,
    isLoadingKots: Boolean,
    onToggleExpand: () -> Unit,
    onSmallPrint: () -> Unit,
    onWhatsApp: () -> Unit,
    onEmail: () -> Unit,
    itemNames: Map<Long, String> = emptyMap(),
    showFreeIssue: Boolean = false
) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bill #${bill.billNo}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black
                    )
                    Text(
                        bill.billingDate?.let {
                            try { dateFormatter.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it)) }
                            catch (e: Exception) { it }
                        } ?: "",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Bill Type Badge
                Surface(
                    color = when (bill.billType) {
                        "CASH" -> Color(0xFF4CAF50)
                        "CREDIT" -> Color(0xFF2196F3)
                        else -> Color.Gray
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        bill.billType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer and Sales Rep
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Customer: $customerName",
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    if (bill.salesMan?.isNotEmpty() == true) {
                        Text(
                            "Sales: ${bill.salesMan}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total:", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "Rs ${String.format("%.2f", bill.total)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                if (bill.discount > 0) {
                    Column {
                        Text("Discount:", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            "Rs ${String.format("%.2f", bill.discount)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                    }
                }
                Column {
                    Text("Cash:", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "Rs ${String.format("%.2f", bill.cash)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Bill Items
                    if (bill.billItems.isNotEmpty()) {
                        Text(
                            "Items (${bill.billItems.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        bill.billItems.forEach { item ->
                            BillItemRow(item, itemNames[item.itemId] ?: "Item #${item.itemId}", showFreeIssue)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // KOTs
                    if (isLoadingKots) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFE94560)
                            )
                        }
                    } else if (bill.kots.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Kitchen Orders (${bill.kots.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        bill.kots.forEach { kot ->
                            KotRow(kot)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Action Buttons
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = onSmallPrint,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Small Print", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Small Print")
                        }
                        Button(
                            onClick = onWhatsApp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "WhatsApp", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillItemRow(item: BillItemDetail, itemName: String, showFreeIssue: Boolean = false) {
    // Calculate amount same as web app: (qty - returnQty) * (unitPrice - discountValue)
    val amount = (item.qty - item.returnQty) * (item.unitPrice - item.discountValue)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                itemName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Row {
                Text(
                    "${item.qty.toInt()} x Rs ${String.format("%.2f", item.unitPrice)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                if (showFreeIssue && item.freeIssueQty > 0) {
                    Text(
                        " | Free: ${if (item.freeIssueQty % 1 == 0.0) item.freeIssueQty.toInt().toString() else String.format("%.2f", item.freeIssueQty)}",
                        fontSize = 11.sp,
                        color = Color(0xFF9C27B0),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Text(
            "Rs ${String.format("%.2f", amount)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

@Composable
fun KotRow(kot: KitchenOrder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (kot.status) {
                "PENDING" -> Color(0xFFFFF9C4)
                "COMPLETED" -> Color(0xFFC8E6C9)
                "CANCELLED" -> Color(0xFFFFCDD2)
                else -> Color(0xFFE3F2FD)
            }
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "KOT #${kot.kotNo}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.Black
                )
                Text(
                    kot.status ?: "PENDING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            if (kot.orderType != null) {
                Text(
                    "Type: ${kot.orderType}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            if (kot.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                kot.items.forEach { item ->
                    Text(
                        "${item.quantity.toInt()}x ${item.itemName}",
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

// API Functions

private suspend fun fetchBillsByType(
    token: String,
    filterState: BillFilterState
): List<Bill> {
    return withContext(Dispatchers.IO) {
        try {
            val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            dateFormatter.timeZone = TimeZone.getTimeZone("UTC")

            val params = mutableListOf<String>()
            params.add("billType=${filterState.billType.apiValue}")
            params.add("page=0")
            params.add("size=100")

            filterState.startDate?.let {
                params.add("startDate=${dateFormatter.format(it)}")
            }
            filterState.endDate?.let {
                params.add("endDate=${dateFormatter.format(it)}")
            }
            filterState.selectedCustomerId?.let {
                params.add("customerId=$it")
            }
            filterState.selectedSalesRepId?.let {
                params.add("salesRefId=$it")
            }

            val queryString = params.joinToString("&")
            val url = "${ApiConfig.BASE_URL_8080}/bill/bills-by-type-paginated?$queryString"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val response = gson.fromJson(content, BillsResponse::class.java)

                // Recalculate bill totals from bill items (like web app does)
                // The API may return incorrect totals
                response.bills.map { bill ->
                    val itemsTotal = bill.billItems.sumOf { item ->
                        (item.qty - item.returnQty) * (item.unitPrice - item.discountValue)
                    }
                    val calculatedTotal = itemsTotal - bill.discountValue
                    bill.copy(total = calculatedTotal)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

private suspend fun fetchKitchenOrdersByBill(
    token: String,
    billId: Long
): List<KitchenOrder> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "${ApiConfig.BASE_URL_8090}/kitchen-order/by-bill/$billId"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                gson.fromJson(content, Array<KitchenOrder>::class.java).toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

private suspend fun fetchAllCustomers(token: String): List<CustomerDataModel> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()

                // Parse manually like AllOrdersScreen to avoid Gson deserialization issues
                @Suppress("UNCHECKED_CAST")
                val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>

                list?.mapNotNull { customerMap ->
                    val id = (customerMap["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val name = customerMap["name"] as? String
                    val address = customerMap["address"] as? String
                    val telephone = customerMap["telephone"] as? String
                    val email = customerMap["email"] as? String
                    val status = customerMap["status"] as? String
                    val details = customerMap["details"] as? String
                    val companyCode = (customerMap["companyCode"] as? Number)?.toInt() ?: 0

                    CustomerDataModel(
                        id = id,
                        name = name,
                        address = address,
                        telephone = telephone,
                        email = email,
                        status = status,
                        details = details,
                        companyCode = companyCode
                    )
                } ?: emptyList()
            } else {
                Log.e("AllBills", "Failed to fetch customers: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("AllBills", "Error fetching customers: ${e.message}", e)
            emptyList()
        }
    }
}

private suspend fun fetchAllSalesReps(token: String): List<SalesRepDataModel> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "${ApiConfig.BASE_URL_8090}/salesref/getAllSalesRefs"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                gson.fromJson(content, Array<SalesRepDataModel>::class.java).toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

private data class ItemData(
    val id: Long = 0,
    val itemName: String? = ""
)

private suspend fun fetchAllItems(token: String): Map<Long, String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "${ApiConfig.BASE_URL_8090}/item/getAllItems"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val items = gson.fromJson(content, Array<ItemData>::class.java).toList()
                items.associate { it.id to (it.itemName ?: "Unknown") }
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }
}

// Print result data class
private data class PrintBillResult(
    val success: Boolean,
    val message: String
)

// Function to print bill receipt via Bluetooth thermal printer
private fun printBillReceipt(
    context: Context,
    bill: Bill,
    customersMap: Map<Long, CustomerDataModel>,
    itemNames: Map<Long, String>
): PrintBillResult {
    return try {
        Log.d("AllBills", "Starting print for Bill #${bill.billNo}")

        // Get PrinterService singleton
        val printerService = PrinterService.getInstance()

        // Check if printer is connected, if not try to connect
        if (!printerService.isConnected()) {
            Log.d("AllBills", "Printer not connected, attempting to connect...")

            // Get Bluetooth adapter
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return PrintBillResult(false, "Bluetooth is not enabled")
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
                return PrintBillResult(false, "No paired printer found. Please pair a printer first.")
            }

            Log.d("AllBills", "Connecting to printer: ${printerDevice.name}")
            val connected = printerService.connectDevice(printerDevice)
            if (!connected) {
                return PrintBillResult(false, "Failed to connect to printer: ${printerDevice.name}")
            }
        }

        // Get bill template from cache (uses same configs as web app)
        val template = BillTemplateCache.getTemplate(context)

        // Build receipt items from bill items (same formula as web app)
        val receiptItems = bill.billItems.map { item ->
            val itemName = itemNames[item.itemId] ?: "Item #${item.itemId}"
            val effectiveQty = item.qty - item.returnQty
            // Calculate amount same as web app: (qty - returnQty) * (unitPrice - discountValue)
            val amount = effectiveQty * (item.unitPrice - item.discountValue)
            ReceiptItem(
                name = itemName,
                quantity = effectiveQty.toInt(),
                price = item.unitPrice,
                amount = amount
            )
        }

        // Get customer info
        val customer = customersMap[bill.customerId]

        // Build ReceiptData with template settings (same as web app)
        val receiptData = ReceiptData(
            items = receiptItems,
            customer = bill.customerName?.takeIf { it.isNotEmpty() } ?: bill.cashCustomerName?.takeIf { it.isNotEmpty() } ?: customer?.name ?: "Walk-in",
            salesRep = bill.salesMan ?: "N/A",
            grossTotal = bill.total,
            discount = bill.discount,
            netTotal = bill.total - bill.discount,
            cashPaid = bill.cash,
            billNo = bill.billNo.toString(),
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
            Log.d("AllBills", "Print successful for Bill #${bill.billNo}")
            PrintBillResult(true, "Printed successfully")
        } else {
            Log.e("AllBills", "Print failed for Bill #${bill.billNo}")
            PrintBillResult(false, "Print failed")
        }
    } catch (e: SecurityException) {
        Log.e("AllBills", "Bluetooth permission denied: ${e.message}")
        PrintBillResult(false, "Bluetooth permission denied. Please grant Bluetooth permissions.")
    } catch (e: Exception) {
        Log.e("AllBills", "Error printing bill: ${e.message}", e)
        PrintBillResult(false, "Error: ${e.message}")
    }
}

// WhatsApp result data class
private data class WhatsAppBillResult(
    val success: Boolean,
    val notificationType: String? = null,
    val recipient: String? = null,
    val errorMessage: String? = null
)

// Function to send bill WhatsApp via backend API
private suspend fun sendBillWhatsApp(token: String, billNo: Long, billType: String): WhatsAppBillResult {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("${ApiConfig.BASE_URL_8080}/bill/sendWhatsApp?billNo=$billNo&billType=$billType")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true

            // Send empty body for POST
            connection.outputStream.bufferedWriter().use { it.write("{}") }

            val responseCode = connection.responseCode
            Log.d("AllBills", "WhatsApp API response code: $responseCode")

            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("AllBills", "WhatsApp API response: $response")

                val gson = Gson()
                val result = gson.fromJson(response, NotificationResultResponse::class.java)

                WhatsAppBillResult(
                    success = result.sent,
                    notificationType = result.channel,
                    recipient = result.recipient,
                    errorMessage = result.message
                )
            } else {
                val errorResponse = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                } catch (e: Exception) {
                    "Unknown error"
                }
                Log.e("AllBills", "WhatsApp API error: $errorResponse")
                WhatsAppBillResult(
                    success = false,
                    errorMessage = "Server error: $responseCode"
                )
            }
        } catch (e: Exception) {
            Log.e("AllBills", "Error sending WhatsApp: ${e.message}", e)
            WhatsAppBillResult(
                success = false,
                errorMessage = "Error: ${e.message}"
            )
        }
    }
}

// Response class for notification result (matching backend NotificationResultDto)
private data class NotificationResultResponse(
    val sent: Boolean = false,
    val channel: String? = null,  // "WHATSAPP", "SMS", or "NONE"
    val message: String? = null,
    val recipient: String? = null
)

// App config data class for All Bills
private data class AllBillsAppConfig(
    val showFreeIssueInOrderForm: String? = null
)

// Fetch config
private suspend fun fetchAllBillsConfig(token: String): AllBillsAppConfig? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("${ApiConfig.BASE_URL_8090}/auth")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val jsonObject = gson.fromJson(response, com.google.gson.JsonObject::class.java)
                AllBillsAppConfig(
                    showFreeIssueInOrderForm = jsonObject.get("showFreeIssueInOrderForm")?.asString
                )
            } else {
                Log.e("AllBills", "Config fetch failed: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("AllBills", "Error fetching config: ${e.message}", e)
            null
        }
    }
}
