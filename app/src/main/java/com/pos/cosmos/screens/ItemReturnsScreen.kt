package com.pos.cosmos.screens

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig

// ==================== Data Classes ====================

private data class ReturnCustomer(val id: Int, val name: String)

private data class ReturnBillRef(val billNo: Int, val billType: String)

private data class ReturnBillItem(
    val itemId: Int,
    val itemCode: String,
    val itemName: String,
    val unitPrice: Double,
    val discount: Double,
    val qty: Double,
    val returnQty: Double, // previously returned
    var newReturnQty: Double = 0.0,
    val supplierId: Int = 0,
    val invoiceId: Int = 0
) {
    val total: Double get() = (unitPrice - discount) * qty
    val preReturnValue: Double get() = (unitPrice - discount) * returnQty
    val returnValue: Double get() = (unitPrice - discount) * newReturnQty
    val netValue: Double get() = (unitPrice - discount) * (qty - newReturnQty - returnQty)
    val availableToReturn: Double get() = qty - returnQty
}

private data class ReturnHistoryItem(
    val id: Int,
    val createdDate: String,
    val billNo: Int,
    val customerName: String,
    val itemCode: String,
    val itemName: String,
    val returnQty: Double,
    val unitPrice: Double,
    val returnValue: Double,
    val returnType: String
)

private data class ReturnHistorySummary(
    val returns: List<ReturnHistoryItem>,
    val totalDamageQty: Double = 0.0,
    val totalDamageValue: Double = 0.0,
    val totalStockReturnQty: Double = 0.0,
    val totalStockReturnValue: Double = 0.0,
    val totalReturnQty: Double = 0.0,
    val totalReturnValue: Double = 0.0
)

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemReturnsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    // Data
    var customers by remember { mutableStateOf<List<ReturnCustomer>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Return entry state
    var selectedCustomerId by remember { mutableStateOf(0) }
    var customerQuery by remember { mutableStateOf("") }
    var showCustomerDropdown by remember { mutableStateOf(false) }
    var selectedBillType by remember { mutableStateOf("CREDIT") }
    var billTypeExpanded by remember { mutableStateOf(false) }
    var customerBills by remember { mutableStateOf<List<ReturnBillRef>>(emptyList()) }
    var selectedBillNo by remember { mutableStateOf(0) }
    var invoiceQuery by remember { mutableStateOf("") }
    var showInvoiceDropdown by remember { mutableStateOf(false) }
    var selectedReturnType by remember { mutableStateOf("STOCK") }
    var returnTypeExpanded by remember { mutableStateOf(false) }
    var billItems by remember { mutableStateOf<List<ReturnBillItem>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }

    // Bill details
    var billCash by remember { mutableStateOf(0.0) }
    var billDiscount by remember { mutableStateOf(0.0) }
    var billDiscountValue by remember { mutableStateOf(0.0) }
    var billCustomerName by remember { mutableStateOf("") }
    var billSalesRef by remember { mutableStateOf("") }

    // History state
    var showHistory by remember { mutableStateOf(false) }
    var historyCustomerQuery by remember { mutableStateOf("") }
    var historyCustomerId by remember { mutableStateOf(0) }
    var showHistoryCustomerDropdown by remember { mutableStateOf(false) }
    var historyStartDate by remember { mutableStateOf("") }
    var historyEndDate by remember { mutableStateOf("") }
    var historyReturnType by remember { mutableStateOf("") }
    var historyReturnTypeExpanded by remember { mutableStateOf(false) }
    var historySummary by remember { mutableStateOf<ReturnHistorySummary?>(null) }
    var historyLoading by remember { mutableStateOf(false) }

    // Dialogs
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteItem by remember { mutableStateOf<ReturnHistoryItem?>(null) }

    // Computed
    val totalReturnValue = billItems.sumOf { it.returnValue }
    val grossTotal = billItems.sumOf { it.total }
    val netTotal = grossTotal - billDiscountValue
    val totalItems = billItems.size
    val totalPcs = billItems.sumOf { it.qty }.toInt()

    val filteredCustomers = remember(customerQuery, customers) {
        if (customerQuery.isBlank()) emptyList()
        else customers.filter { it.name.contains(customerQuery, ignoreCase = true) }.take(10)
    }
    val historyFilteredCustomers = remember(historyCustomerQuery, customers) {
        if (historyCustomerQuery.isBlank()) emptyList()
        else customers.filter { it.name.contains(historyCustomerQuery, ignoreCase = true) }.take(10)
    }
    val filteredBills = remember(invoiceQuery, customerBills, selectedBillType) {
        val typed = customerBills.filter { it.billType == selectedBillType }
        if (invoiceQuery.isBlank()) typed.take(20)
        else typed.filter { it.billNo.toString().contains(invoiceQuery) }.take(20)
    }

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE0E0E0),
        focusedBorderColor = Color(0xFF4FC3F7),
        unfocusedBorderColor = Color(0xFF90A4AE),
        focusedLabelColor = Color(0xFF4FC3F7),
        unfocusedLabelColor = Color(0xFF90A4AE),
        cursorColor = Color.White
    )

    // Load all bills by type
    fun loadBills() {
        if (token != null) {
            scope.launch {
                val bills = withContext(Dispatchers.IO) {
                    val byType = fetchBillsByType(token!!, selectedBillType)
                    if (selectedCustomerId > 0) {
                        val byCustomer = fetchBillsByCustomer(token!!, selectedCustomerId)
                        (byCustomer + byType).distinctBy { it.billNo }
                    } else byType
                }
                customerBills = bills
            }
        }
    }

    // Load data
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            withContext(Dispatchers.IO) {
                customers = fetchReturnCustomers(token!!)
            }
            loading = false
            loadBills()
        }
    }

    // Reload bills when bill type changes
    LaunchedEffect(selectedBillType) {
        if (token != null && !loading) {
            loadBills()
        }
    }

    // Load bill details when invoice selected
    fun loadBillDetails() {
        if (selectedBillNo > 0 && token != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    fetchBillDetails(token!!, selectedBillNo, selectedBillType)
                }
                if (result != null) {
                    billItems = result.first
                    val details = result.second
                    billCash = details["cash"] as? Double ?: 0.0
                    billDiscount = details["discount"] as? Double ?: 0.0
                    billDiscountValue = details["discountValue"] as? Double ?: 0.0
                    billCustomerName = details["customerName"] as? String ?: ""
                    billSalesRef = details["salesRef"] as? String ?: ""
                }
            }
        }
    }

    fun saveReturn() {
        val itemsToReturn = billItems.filter { it.newReturnQty > 0 }
        if (itemsToReturn.isEmpty()) {
            errorMessage = "Please enter return quantities"
            showErrorDialog = true
            return
        }

        isSaving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                submitReturn(token!!, selectedBillNo, selectedBillType, selectedReturnType, billItems)
            }
            isSaving = false
            if (result) {
                successMessage = "Return saved successfully!"
                showSuccessDialog = true
                billItems = emptyList()
                selectedBillNo = 0
                invoiceQuery = ""
            } else {
                errorMessage = "Failed to save return"
                showErrorDialog = true
            }
        }
    }

    fun loadHistory() {
        historyLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchReturnHistory(token!!, historyCustomerId, historyStartDate, historyEndDate, historyReturnType)
            }
            historySummary = result
            historyLoading = false
        }
    }

    fun deleteReturn(item: ReturnHistoryItem) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                deleteReturnRecord(token!!, item.id)
            }
            if (result) {
                successMessage = "Return deleted successfully!"
                showSuccessDialog = true
                loadHistory()
            } else {
                errorMessage = "Failed to delete return"
                showErrorDialog = true
            }
        }
    }

    // ==================== UI ====================

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Returns", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(
                            if (showHistory) "New Return" else "History",
                            color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A237E))
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF1A237E))
            }
        } else if (!showHistory) {
            // ==================== RETURN ENTRY ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF121212))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filters section
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Return Entry", fontWeight = FontWeight.Bold, color = Color(0xFF4FC3F7), fontSize = 16.sp)

                            // Customer
                            Box {
                                OutlinedTextField(
                                    value = customerQuery,
                                    onValueChange = {
                                        customerQuery = it
                                        showCustomerDropdown = it.isNotBlank()
                                        if (selectedCustomerId > 0) {
                                            selectedCustomerId = 0
                                            customerBills = emptyList()
                                            billItems = emptyList()
                                        }
                                    },
                                    label = { Text("Customer") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = inputColors
                                )
                                if (showCustomerDropdown && filteredCustomers.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                    ) {
                                        Column(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                                            filteredCustomers.forEach { cust ->
                                                Text(
                                                    cust.name, fontSize = 13.sp, color = Color(0xFF212121),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            customerQuery = cust.name
                                                            selectedCustomerId = cust.id
                                                            showCustomerDropdown = false
                                                            loadBills()
                                                        }
                                                        .padding(12.dp)
                                                )
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                }
                            }

                            // Bill Type + Return Type row
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Bill Type
                                ExposedDropdownMenuBox(
                                    expanded = billTypeExpanded,
                                    onExpandedChange = { billTypeExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = if (selectedBillType == "CREDIT") "Credit Bill" else "Cash Bill",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Bill Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = billTypeExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        colors = inputColors
                                    )
                                    ExposedDropdownMenu(expanded = billTypeExpanded, onDismissRequest = { billTypeExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Credit Bill") }, onClick = { selectedBillType = "CREDIT"; billTypeExpanded = false; invoiceQuery = ""; selectedBillNo = 0; billItems = emptyList() })
                                        DropdownMenuItem(text = { Text("Cash Bill") }, onClick = { selectedBillType = "CASH"; billTypeExpanded = false; invoiceQuery = ""; selectedBillNo = 0; billItems = emptyList() })
                                    }
                                }

                                // Return Type
                                ExposedDropdownMenuBox(
                                    expanded = returnTypeExpanded,
                                    onExpandedChange = { returnTypeExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = if (selectedReturnType == "STOCK") "Add to Stock" else "Damage",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Return Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = returnTypeExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        colors = inputColors
                                    )
                                    ExposedDropdownMenu(expanded = returnTypeExpanded, onDismissRequest = { returnTypeExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Add to Stock") }, onClick = { selectedReturnType = "STOCK"; returnTypeExpanded = false })
                                        DropdownMenuItem(text = { Text("Damage") }, onClick = { selectedReturnType = "DAMAGE"; returnTypeExpanded = false })
                                    }
                                }
                            }

                            // Invoice No
                            Box {
                                    OutlinedTextField(
                                        value = invoiceQuery,
                                        onValueChange = {
                                            invoiceQuery = it
                                            showInvoiceDropdown = true
                                        },
                                        label = { Text("Invoice No") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = inputColors,
                                        trailingIcon = {
                                            IconButton(onClick = { showInvoiceDropdown = !showInvoiceDropdown }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF90A4AE))
                                            }
                                        }
                                    )
                                    if (showInvoiceDropdown && filteredBills.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            elevation = CardDefaults.cardElevation(4.dp)
                                        ) {
                                            Column(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                                                filteredBills.forEach { bill ->
                                                    Text(
                                                        "${bill.billNo}", fontSize = 13.sp, color = Color(0xFF212121),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                invoiceQuery = bill.billNo.toString()
                                                                selectedBillNo = bill.billNo
                                                                showInvoiceDropdown = false
                                                                loadBillDetails()
                                                            }
                                                            .padding(12.dp)
                                                    )
                                                    HorizontalDivider()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                // Bill summary
                if (billItems.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bill #$selectedBillNo", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                                    Text("Return: ${String.format("%.2f", totalReturnValue)}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Net Total: ${String.format("%.2f", netTotal)}", color = Color(0xFFE0E0E0), fontSize = 12.sp)
                                    Text("Customer: $billCustomerName", color = Color(0xFFE0E0E0), fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Items table
                    items(billItems) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.itemName, color = Color(0xFF4FC3F7), fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${item.itemCode}", color = Color(0xFF90A4AE), fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Price: ${String.format("%.2f", item.unitPrice)}", color = Color(0xFFBDBDBD), fontSize = 11.sp)
                                    Text("Qty: ${item.qty.toInt()}", color = Color(0xFFBDBDBD), fontSize = 11.sp)
                                    Text("Pre-Return: ${item.returnQty.toInt()}", color = Color(0xFFFF8A65), fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Return Qty:", color = Color(0xFFE0E0E0), fontSize = 12.sp)
                                    IconButton(
                                        onClick = {
                                            val newQty = (item.newReturnQty - 1).coerceAtLeast(0.0)
                                            billItems = billItems.map { if (it.itemId == item.itemId) it.copy(newReturnQty = newQty) else it }
                                        },
                                        modifier = Modifier.size(32.dp).background(Color(0xFFE53935), RoundedCornerShape(6.dp))
                                    ) { Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }

                                    OutlinedTextField(
                                        value = if (item.newReturnQty > 0) item.newReturnQty.toInt().toString() else "",
                                        onValueChange = {
                                            val qty = (it.toDoubleOrNull() ?: 0.0).coerceIn(0.0, item.availableToReturn)
                                            billItems = billItems.map { bi -> if (bi.itemId == item.itemId) bi.copy(newReturnQty = qty) else bi }
                                        },
                                        modifier = Modifier.width(60.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, color = Color.White, fontSize = 14.sp),
                                        colors = inputColors
                                    )

                                    IconButton(
                                        onClick = {
                                            val newQty = (item.newReturnQty + 1).coerceAtMost(item.availableToReturn)
                                            billItems = billItems.map { if (it.itemId == item.itemId) it.copy(newReturnQty = newQty) else it }
                                        },
                                        modifier = Modifier.size(32.dp).background(Color(0xFF4CAF50), RoundedCornerShape(6.dp))
                                    ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }

                                    Spacer(modifier = Modifier.weight(1f))
                                    if (item.newReturnQty > 0) {
                                        Text("Val: ${String.format("%.2f", item.returnValue)}", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Save button
                    item {
                        Button(
                            onClick = { saveReturn() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !isSaving && billItems.any { it.newReturnQty > 0 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Return (${String.format("%.2f", totalReturnValue)})", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        } else {
            // ==================== RETURN HISTORY ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFF121212))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Filters
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Return History", fontWeight = FontWeight.Bold, color = Color(0xFF4FC3F7), fontSize = 16.sp)

                            // Customer filter
                            Box {
                                OutlinedTextField(
                                    value = historyCustomerQuery,
                                    onValueChange = {
                                        historyCustomerQuery = it
                                        showHistoryCustomerDropdown = it.isNotBlank()
                                        if (historyCustomerId > 0) historyCustomerId = 0
                                    },
                                    label = { Text("Customer (optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = inputColors
                                )
                                if (showHistoryCustomerDropdown && historyFilteredCustomers.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(4.dp)
                                    ) {
                                        Column(modifier = Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                                            historyFilteredCustomers.forEach { cust ->
                                                Text(
                                                    cust.name, fontSize = 13.sp, color = Color(0xFF212121),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            historyCustomerQuery = cust.name
                                                            historyCustomerId = cust.id
                                                            showHistoryCustomerDropdown = false
                                                        }
                                                        .padding(12.dp)
                                                )
                                                HorizontalDivider()
                                            }
                                        }
                                    }
                                }
                            }

                            // Date + Return Type
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = historyStartDate,
                                    onValueChange = { historyStartDate = it },
                                    label = { Text("Start Date") },
                                    placeholder = { Text("YYYY-MM-DD", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = inputColors
                                )
                                OutlinedTextField(
                                    value = historyEndDate,
                                    onValueChange = { historyEndDate = it },
                                    label = { Text("End Date") },
                                    placeholder = { Text("YYYY-MM-DD", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = inputColors
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = historyReturnTypeExpanded,
                                    onExpandedChange = { historyReturnTypeExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = when (historyReturnType) { "DAMAGE" -> "Damage"; "STOCK" -> "Added to Stock"; else -> "All Types" },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = historyReturnTypeExpanded) },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        colors = inputColors
                                    )
                                    ExposedDropdownMenu(expanded = historyReturnTypeExpanded, onDismissRequest = { historyReturnTypeExpanded = false }) {
                                        DropdownMenuItem(text = { Text("All Types") }, onClick = { historyReturnType = ""; historyReturnTypeExpanded = false })
                                        DropdownMenuItem(text = { Text("Damage") }, onClick = { historyReturnType = "DAMAGE"; historyReturnTypeExpanded = false })
                                        DropdownMenuItem(text = { Text("Added to Stock") }, onClick = { historyReturnType = "STOCK"; historyReturnTypeExpanded = false })
                                    }
                                }
                                Button(
                                    onClick = { loadHistory() },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Search", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }

                // Summary cards
                if (historySummary != null) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard("Damages", historySummary!!.totalDamageQty, historySummary!!.totalDamageValue, Color(0xFFEF5350), Modifier.weight(1f))
                            SummaryCard("To Stock", historySummary!!.totalStockReturnQty, historySummary!!.totalStockReturnValue, Color(0xFF4CAF50), Modifier.weight(1f))
                            SummaryCard("Total", historySummary!!.totalReturnQty, historySummary!!.totalReturnValue, Color(0xFF4FC3F7), Modifier.weight(1f))
                        }
                    }
                }

                // History items
                if (historyLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF4FC3F7))
                        }
                    }
                }

                if (historySummary != null) {
                    items(historySummary!!.returns) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.itemName, color = Color(0xFF4FC3F7), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("Bill #${item.billNo} | ${item.customerName}", color = Color(0xFF90A4AE), fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Qty: ${item.returnQty.toInt()}", color = Color(0xFFBDBDBD), fontSize = 11.sp)
                                        Text("Val: ${String.format("%.2f", item.returnValue)}", color = Color(0xFFEF5350), fontSize = 11.sp)
                                        Text(
                                            if (item.returnType == "DAMAGE") "Damage" else "To Stock",
                                            color = if (item.returnType == "DAMAGE") Color(0xFFEF5350) else Color(0xFF4CAF50),
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(item.createdDate, color = Color(0xFF757575), fontSize = 10.sp)
                                }
                                IconButton(onClick = { deleteItem = item; showDeleteConfirm = true }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success") },
            text = { Text(successMessage) },
            confirmButton = { TextButton(onClick = { showSuccessDialog = false }) { Text("OK") } }
        )
    }
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } }
        )
    }
    if (showDeleteConfirm && deleteItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Return") },
            text = { Text("Delete return of ${deleteItem!!.returnQty.toInt()} x ${deleteItem!!.itemName}?\n\nType: ${if (deleteItem!!.returnType == "DAMAGE") "Damage" else "Added to Stock"}\n\nIf added to stock, stock quantity will be reduced.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; deleteReturn(deleteItem!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SummaryCard(title: String, qty: Double, value: Double, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${qty.toInt()} items", color = Color(0xFFE0E0E0), fontSize = 11.sp)
            Text(String.format("%.2f", value), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ==================== API Functions ====================

private suspend fun fetchReturnCustomers(token: String): List<ReturnCustomer> {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000

        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            @Suppress("UNCHECKED_CAST")
            val list = Gson().fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()
            list.mapNotNull {
                val id = (it["id"] as? Number)?.toInt() ?: return@mapNotNull null
                val name = it["name"] as? String ?: return@mapNotNull null
                ReturnCustomer(id, name)
            }
        } else emptyList()
    } catch (e: Exception) { Log.e("CustomerReturns", "fetchCustomers: ${e.message}"); emptyList() }
}

private suspend fun fetchBillsByType(token: String, billType: String): List<ReturnBillRef> {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/getBillsByType?billType=$billType").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000

        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            // API may return list of numbers (bill nos) or list of bill objects
            try {
                @Suppress("UNCHECKED_CAST")
                val list = gson.fromJson(content, List::class.java) as? List<Any> ?: emptyList()
                list.mapNotNull { item ->
                    when (item) {
                        is Number -> ReturnBillRef(item.toInt(), billType)
                        is Map<*, *> -> {
                            val billNo = (item["billNo"] as? Number)?.toInt() ?: return@mapNotNull null
                            val type = item["billType"] as? String ?: billType
                            ReturnBillRef(billNo, type)
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomerReturns", "Parse error: ${e.message}, content: ${content.take(200)}")
                emptyList()
            }
        } else emptyList()
    } catch (e: Exception) { Log.e("CustomerReturns", "fetchBillsByType: ${e.message}"); emptyList() }
}

private suspend fun fetchBillsByCustomer(token: String, customerId: Int): List<ReturnBillRef> {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/bills-by-customer-id?customerId=$customerId").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000

        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            @Suppress("UNCHECKED_CAST")
            val list = Gson().fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()
            list.mapNotNull {
                val billNo = (it["billNo"] as? Number)?.toInt() ?: return@mapNotNull null
                val billType = it["billType"] as? String ?: "CREDIT"
                ReturnBillRef(billNo, billType)
            }
        } else emptyList()
    } catch (e: Exception) { Log.e("CustomerReturns", "fetchBills: ${e.message}"); emptyList() }
}

private suspend fun fetchBillDetails(token: String, billNo: Int, billType: String): Pair<List<ReturnBillItem>, Map<String, Any>>? {
    return try {
        // Fetch all items first for name/code lookup
        val itemsMap = mutableMapOf<Int, Pair<String, String>>() // id -> (code, name)
        try {
            val itemsConn = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems").openConnection() as HttpURLConnection
            itemsConn.requestMethod = "GET"
            itemsConn.setRequestProperty("Authorization", "Bearer $token")
            itemsConn.connectTimeout = 30000; itemsConn.readTimeout = 30000
            if (itemsConn.responseCode == 200) {
                val itemsContent = itemsConn.inputStream.bufferedReader().use { it.readText() }
                @Suppress("UNCHECKED_CAST")
                val allItems = Gson().fromJson(itemsContent, List::class.java) as? List<Map<String, Any>> ?: emptyList()
                allItems.forEach { item ->
                    val id = (item["id"] as? Number)?.toInt() ?: return@forEach
                    val code = item["code"] as? String ?: ""
                    val name = item["name"] as? String ?: ""
                    itemsMap[id] = Pair(code, name)
                }
            }
        } catch (e: Exception) {
            Log.e("CustomerReturns", "Error loading items: ${e.message}")
        }

        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/loadBill?billId=$billNo&billType=$billType").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000

        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            val response = Gson().fromJson(content, Map::class.java) as? Map<String, Any> ?: return null

            val cash = (response["cash"] as? Number)?.toDouble() ?: 0.0
            val discount = (response["discount"] as? Number)?.toDouble() ?: 0.0
            val discountValue = (response["discountValue"] as? Number)?.toDouble() ?: 0.0

            @Suppress("UNCHECKED_CAST")
            val items = response["billItems"] as? List<Map<String, Any>> ?: emptyList()
            val billItems = items.mapNotNull { itemMap ->
                try {
                    val itemId = (itemMap["itemId"] as? Number)?.toInt() ?: return@mapNotNull null
                    val itemInfo = itemsMap[itemId]
                    ReturnBillItem(
                        itemId = itemId,
                        itemCode = itemInfo?.first ?: (itemMap["itemCode"] as? String ?: ""),
                        itemName = itemInfo?.second ?: (itemMap["itemName"] as? String ?: "Item #$itemId"),
                        unitPrice = (itemMap["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                        discount = (itemMap["discountValue"] as? Number)?.toDouble() ?: 0.0,
                        qty = (itemMap["qty"] as? Number)?.toDouble() ?: 0.0,
                        returnQty = (itemMap["returnQty"] as? Number)?.toDouble() ?: 0.0,
                        supplierId = (itemMap["supplierId"] as? Number)?.toInt() ?: 0,
                        invoiceId = (itemMap["invoiceId"] as? Number)?.toInt() ?: 0
                    )
                } catch (e: Exception) { null }
            }

            val details = mapOf<String, Any>(
                "cash" to cash, "discount" to discount, "discountValue" to discountValue,
                "customerName" to (response["cashCustomerName"] as? String ?: ""),
                "salesRef" to (response["salesMan"] as? String ?: "")
            )
            Pair(billItems, details)
        } else null
    } catch (e: Exception) { Log.e("CustomerReturns", "fetchBillDetails: ${e.message}"); null }
}

private suspend fun submitReturn(token: String, billNo: Int, billType: String, returnType: String, items: List<ReturnBillItem>): Boolean {
    return try {
        val billData = JsonObject().apply {
            val itemsArray = com.google.gson.JsonArray()
            items.forEach { item ->
                val obj = JsonObject().apply {
                    addProperty("itemId", item.itemId)
                    addProperty("itemCode", item.itemCode)
                    addProperty("itemName", item.itemName)
                    addProperty("unitPrice", item.unitPrice)
                    addProperty("discountValue", item.discount)
                    addProperty("qty", item.qty)
                    addProperty("returnQty", item.returnQty + item.newReturnQty)
                    addProperty("supplierId", item.supplierId)
                    addProperty("invoiceId", item.invoiceId)
                }
                itemsArray.add(obj)
            }
            add("billItems", itemsArray)
            addProperty("billNo", billNo)
            addProperty("billType", billType)
            addProperty("returnType", returnType)
        }

        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/salesReturn").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000
        conn.outputStream.bufferedWriter().use { it.write(billData.toString()) }

        conn.responseCode == 200 || conn.responseCode == 201
    } catch (e: Exception) { Log.e("CustomerReturns", "submitReturn: ${e.message}"); false }
}

private suspend fun fetchReturnHistory(token: String, customerId: Int, startDate: String, endDate: String, returnType: String): ReturnHistorySummary {
    return try {
        val params = mutableListOf<String>()
        if (customerId > 0) params.add("customerId=$customerId")
        if (startDate.isNotBlank()) params.add("startDate=$startDate")
        if (endDate.isNotBlank()) params.add("endDate=$endDate")
        if (returnType.isNotBlank()) params.add("returnType=$returnType")
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""

        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/return-history$query").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000

        if (conn.responseCode == 200) {
            val content = conn.inputStream.bufferedReader().use { it.readText() }
            val response = Gson().fromJson(content, Map::class.java) as? Map<String, Any> ?: return ReturnHistorySummary(emptyList())

            @Suppress("UNCHECKED_CAST")
            val returns = (response["returns"] as? List<Map<String, Any>> ?: emptyList()).mapNotNull {
                try {
                    ReturnHistoryItem(
                        id = (it["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                        createdDate = it["createdDate"] as? String ?: "",
                        billNo = (it["billNo"] as? Number)?.toInt() ?: 0,
                        customerName = it["customerName"] as? String ?: "",
                        itemCode = it["itemCode"] as? String ?: "",
                        itemName = it["itemName"] as? String ?: "",
                        returnQty = (it["returnQty"] as? Number)?.toDouble() ?: 0.0,
                        unitPrice = (it["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                        returnValue = (it["returnValue"] as? Number)?.toDouble() ?: 0.0,
                        returnType = it["returnType"] as? String ?: ""
                    )
                } catch (e: Exception) { null }
            }

            ReturnHistorySummary(
                returns = returns,
                totalDamageQty = (response["totalDamageQty"] as? Number)?.toDouble() ?: 0.0,
                totalDamageValue = (response["totalDamageValue"] as? Number)?.toDouble() ?: 0.0,
                totalStockReturnQty = (response["totalStockReturnQty"] as? Number)?.toDouble() ?: 0.0,
                totalStockReturnValue = (response["totalStockReturnValue"] as? Number)?.toDouble() ?: 0.0,
                totalReturnQty = (response["totalReturnQty"] as? Number)?.toDouble() ?: 0.0,
                totalReturnValue = (response["totalReturnValue"] as? Number)?.toDouble() ?: 0.0
            )
        } else ReturnHistorySummary(emptyList())
    } catch (e: Exception) { Log.e("CustomerReturns", "fetchHistory: ${e.message}"); ReturnHistorySummary(emptyList()) }
}

private suspend fun deleteReturnRecord(token: String, returnId: Int): Boolean {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8080}/bill/delete-return/$returnId").openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 30000; conn.readTimeout = 30000
        conn.responseCode == 200
    } catch (e: Exception) { Log.e("CustomerReturns", "deleteReturn: ${e.message}"); false }
}
