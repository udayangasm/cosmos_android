package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.models.CustomerPayment
import com.pos.cosmos.models.UserPermission
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Cheque status codes used by the backend (mirrors the web cheque component dropdown).
private data class ChequeStatusFilter(val label: String, val code: String?)

private val CHEQUE_STATUS_FILTERS = listOf(
    ChequeStatusFilter("All", null),
    ChequeStatusFilter("Pending", "pending"),
    ChequeStatusFilter("Realized", "Realized"),
    ChequeStatusFilter("Rejected", "Rejected")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChequeScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var allCheques by remember { mutableStateOf<List<CustomerPayment>>(emptyList()) }
    var customers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var salesReps by remember { mutableStateOf<List<SalesRefData>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var canAccept by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(CHEQUE_STATUS_FILTERS.first()) }

    // Extra filters (mirrors the web cheque page: customer + sales rep, plus a date range)
    var customerQuery by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf(0L) }
    var salesRepQuery by remember { mutableStateOf("") }
    var selectedSalesRepId by remember { mutableStateOf(0L) }
    var fromDate by remember { mutableStateOf<Long?>(null) }
    var toDate by remember { mutableStateOf<Long?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<Pair<CustomerPayment, Boolean>?>(null) } // (cheque, realize?)

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    fun refresh() {
        val tk = token ?: return
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                val custs = fetchChequeCustomers(tk)
                customers = custs
                val nameById = custs.associate { it.id to (it.name ?: "") }
                allCheques = fetchAllCheques(tk).map { p ->
                    sanitizeCheque(p, nameById[p.customerId] ?: "")
                }
            }
            loading = false
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            withContext(Dispatchers.IO) {
                canAccept = fetchAcceptChequePermission(token!!)
                val custs = fetchChequeCustomers(token!!)
                customers = custs
                salesReps = fetchSalesRefs(token!!)
                val nameById = custs.associate { it.id to (it.name ?: "") }
                allCheques = fetchAllCheques(token!!).map { p ->
                    sanitizeCheque(p, nameById[p.customerId] ?: "")
                }
            }
            loading = false
        }
    }

    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val cheques = remember(allCheques, selectedFilter, selectedCustomerId, selectedSalesRepId, fromDate, toDate) {
        val code = selectedFilter.code
        val fromStr = fromDate?.let { dateFmt.format(Date(it)) }
        val toStr = toDate?.let { dateFmt.format(Date(it)) }
        allCheques.filter { c ->
            val cd = c.chequeDate?.take(10) ?: ""
            (code == null || c.chequeStatus.equals(code, ignoreCase = true)) &&
                (selectedCustomerId <= 0L || c.customerId == selectedCustomerId) &&
                (selectedSalesRepId <= 0L || c.salesRepId == selectedSalesRepId) &&
                (fromStr == null || (cd.isNotBlank() && cd >= fromStr)) &&
                (toStr == null || (cd.isNotBlank() && cd <= toStr))
        }
    }
    val totalAmount = remember(cheques) { cheques.sumOf { it.paidAmount } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cheques", color = Color.White) },
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
                // Filters: customer, sales rep, date range, status
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Filters", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1A1A2E))
                            Spacer(modifier = Modifier.height(8.dp))

                            SearchableDropdown(
                                label = "Customer",
                                query = customerQuery,
                                onQueryChange = { customerQuery = it; if (it.isBlank()) selectedCustomerId = 0L },
                                options = customers.map { it.id to (it.name ?: "") },
                                onSelect = { id, name -> selectedCustomerId = id; customerQuery = name },
                                onClear = { selectedCustomerId = 0L; customerQuery = "" }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            SearchableDropdown(
                                label = "Sales Rep",
                                query = salesRepQuery,
                                onQueryChange = { salesRepQuery = it; if (it.isBlank()) selectedSalesRepId = 0L },
                                options = salesReps.map { it.id to (it.name ?: "") },
                                onSelect = { id, name -> selectedSalesRepId = id; salesRepQuery = name },
                                onClear = { selectedSalesRepId = 0L; salesRepQuery = "" }
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = fromDate?.let { dateFmt.format(Date(it)) } ?: "",
                                    onValueChange = { },
                                    label = { Text("From") },
                                    readOnly = true,
                                    singleLine = true,
                                    colors = chequeFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showFromPicker = true },
                                    trailingIcon = {
                                        IconButton(onClick = { showFromPicker = true }) {
                                            Icon(Icons.Default.DateRange, contentDescription = "From date", tint = Color(0xFF1A1A2E))
                                        }
                                    }
                                )
                                OutlinedTextField(
                                    value = toDate?.let { dateFmt.format(Date(it)) } ?: "",
                                    onValueChange = { },
                                    label = { Text("To") },
                                    readOnly = true,
                                    singleLine = true,
                                    colors = chequeFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showToPicker = true },
                                    trailingIcon = {
                                        IconButton(onClick = { showToPicker = true }) {
                                            Icon(Icons.Default.DateRange, contentDescription = "To date", tint = Color(0xFF1A1A2E))
                                        }
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Status chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CHEQUE_STATUS_FILTERS.forEach { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter.label, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFFEEEEEE),
                                            labelColor = Color(0xFF1A1A2E),
                                            selectedContainerColor = Color(0xFFE94560),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            if (selectedCustomerId > 0L || selectedSalesRepId > 0L ||
                                fromDate != null || toDate != null || selectedFilter.code != null
                            ) {
                                TextButton(onClick = {
                                    selectedCustomerId = 0L; customerQuery = ""
                                    selectedSalesRepId = 0L; salesRepQuery = ""
                                    fromDate = null; toDate = null
                                    selectedFilter = CHEQUE_STATUS_FILTERS.first()
                                }) { Text("Clear all filters", color = Color(0xFFE94560)) }
                            }
                        }
                    }
                }

                // Total summary
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Cheques", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                                Text("${cheques.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Amount", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                                Text(
                                    String.format(Locale.US, "%.2f", totalAmount),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE94560)
                                )
                            }
                        }
                    }
                }

                if (successMessage.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF34C759).copy(alpha = 0.15f))
                        ) {
                            Text(successMessage, color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
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
                            Text(errorMessage, color = Color(0xFFE94560), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                if (cheques.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text("No cheques found.", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(12.dp))
                        }
                    }
                }

                items(cheques) { cheque ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (cheque.customerName.isNotBlank()) cheque.customerName else "(Unknown customer)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A2E)
                                    )
                                    Text("Cheque No: ${cheque.chequeNumber}", fontSize = 12.sp, color = Color(0xFF555555))
                                    Text("Date: ${formatChequeDate(cheque.chequeDate)}", fontSize = 12.sp, color = Color(0xFF555555))
                                    if (cheque.bankAndBranch.isNotBlank()) {
                                        Text("Bank: ${cheque.bankAndBranch}", fontSize = 12.sp, color = Color(0xFF555555))
                                    }
                                    if (cheque.accountNumber.isNotBlank()) {
                                        Text("Account: ${cheque.accountNumber}", fontSize = 12.sp, color = Color(0xFF555555))
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        String.format(Locale.US, "%.2f", cheque.paidAmount),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFFE94560)
                                    )
                                    ChequeStatusBadge(cheque.chequeStatus)
                                }
                            }

                            // Realize / Reject actions — only for pending cheques and when permitted.
                            if (canAccept && cheque.chequeStatus.equals("pending", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { pendingAction = cheque to true },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Realize")
                                    }
                                    Button(
                                        onClick = { pendingAction = cheque to false },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reject")
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(40.dp)) }
            }
        }
    }

    // Confirm realize / reject
    pendingAction?.let { (cheque, realize) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(if (realize) "Realize Cheque" else "Reject Cheque") },
            text = {
                Text(
                    "Are you sure you want to ${if (realize) "realize" else "reject"} cheque no ${cheque.chequeNumber} " +
                        "of ${String.format(Locale.US, "%.2f", cheque.paidAmount)}?"
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (realize) Color(0xFF34C759) else Color(0xFFE94560)
                    ),
                    onClick = {
                        pendingAction = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (realize) acceptCheque(token!!, cheque) else rejectCheque(token!!, cheque)
                            }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refresh()
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text(if (realize) "Realize" else "Reject") }
            },
            dismissButton = {
                Button(onClick = { pendingAction = null }) { Text("Cancel") }
            }
        )
    }

    // Date range pickers
    if (showFromPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = fromDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { fromDate = state.selectedDateMillis; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { fromDate = null; showFromPicker = false }) { Text("Clear") } }
        ) { DatePicker(state = state) }
    }
    if (showToPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { toDate = state.selectedDateMillis; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { toDate = null; showToPicker = false }) { Text("Clear") } }
        ) { DatePicker(state = state) }
    }
}

/** Searchable single-select dropdown (typing filters the options). Used for customer & sales rep. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchableDropdown(
    label: String,
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<Pair<Long, String>>,
    onSelect: (Long, String) -> Unit,
    onClear: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(query, options) {
        if (query.isBlank()) options else options.filter { it.second.contains(query, ignoreCase = true) }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            colors = chequeFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onClear(); expanded = false }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear $label", tint = Color(0xFF1A1A2E))
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            }
        )
        val toShow = filtered.take(50)
        ExposedDropdownMenu(
            expanded = expanded && toShow.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            toShow.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(id, name); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun chequeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF1A1A2E),
    unfocusedTextColor = Color(0xFF1A1A2E),
    focusedLabelColor = Color(0xFF1A1A2E),
    unfocusedLabelColor = Color(0xFF555555),
    focusedBorderColor = Color(0xFF1A1A2E),
    unfocusedBorderColor = Color(0xFF999999),
    cursorColor = Color(0xFF1A1A2E)
)

@Composable
private fun ChequeStatusBadge(status: String) {
    val (bg, fg, label) = when (status.lowercase(Locale.US)) {
        "realized" -> Triple(Color(0xFF34C759).copy(alpha = 0.15f), Color(0xFF2E7D32), "Realized")
        "rejected" -> Triple(Color(0xFFE94560).copy(alpha = 0.15f), Color(0xFFC62828), "Rejected")
        else -> Triple(Color(0xFFFFA000).copy(alpha = 0.15f), Color(0xFFE65100), "Pending")
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Display a backend ISO cheque date as yyyy-MM-dd (the date portion only). */
private fun formatChequeDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    return iso.replace('T', ' ').take(10)
}

/**
 * Rebuild a CustomerPayment with all String fields guaranteed non-null and a display
 * customer name filled in. The backend sends null for fields like bankAndBranch on some
 * rows; Gson stores those nulls in the non-null Kotlin fields, so reads like .isNotBlank()
 * would crash without coalescing.
 */
private fun sanitizeCheque(p: CustomerPayment, displayName: String): CustomerPayment {
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
        chequeStatus = s(p.chequeStatus).ifBlank { "pending" },
        customerName = displayName.ifBlank { s(p.customerName) },
        paymentDateStr = s(p.paymentDateStr),
        chequeDateStr = s(p.chequeDateStr)
    )
}

// ---------------- API helpers ----------------

// GET /customer/payment/all (bill-service 8080) — returns all payments; keep only cheques.
private suspend fun fetchAllCheques(token: String): List<CustomerPayment> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/customer/payment/all")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<CustomerPayment>::class.java).toList()
                .filter { !it.chequeNumber.isNullOrBlank() && !it.chequeDate.isNullOrBlank() }
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// GET /customer/getAllCustomers (dealer-service 8090) — for customerId -> name mapping.
private suspend fun fetchChequeCustomers(token: String): List<CustomerData> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<CustomerData>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// POST /customer/payment/acceptCheque (bill-service 8080) — mark cheque realized.
private suspend fun acceptCheque(token: String, cheque: CustomerPayment): Pair<Boolean, String> =
    postCheque(token, "${ApiConfig.BASE_URL_8080}/customer/payment/acceptCheque", cheque, "Cheque realized successfully!", "Realize")

// POST /customer/payment/rejectCheque (bill-service 8080) — mark cheque rejected.
private suspend fun rejectCheque(token: String, cheque: CustomerPayment): Pair<Boolean, String> =
    postCheque(token, "${ApiConfig.BASE_URL_8080}/customer/payment/rejectCheque", cheque, "Cheque rejected successfully!", "Reject")

private suspend fun postCheque(
    token: String,
    urlStr: String,
    cheque: CustomerPayment,
    successMsg: String,
    actionLabel: String
): Pair<Boolean, String> {
    return try {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.outputStream.bufferedWriter().use { it.write(Gson().toJson(cheque)) }
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
            Pair(true, successMsg)
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "$actionLabel failed ($responseCode): $err")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "$actionLabel failed: ${e.message}")
    }
}

private data class SalesRefData(
    val id: Long = 0,
    val name: String? = null
)

// GET /salesRef/getAllSalesRef (dealer-service 8090) — for the sales-rep filter dropdown.
private suspend fun fetchSalesRefs(token: String): List<SalesRefData> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/salesRef/getAllSalesRef")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<SalesRefData>::class.java).toList()
                .filter { !it.name.isNullOrBlank() }
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// acceptPayments permission — dealer-service (8090) /admin/getUserPermissionsByToken.
private suspend fun fetchAcceptChequePermission(token: String): Boolean {
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
            val perm = Gson().fromJson(content, UserPermission::class.java)
            perm?.acceptPayments == "Y"
        } else false
    } catch (e: Exception) {
        e.printStackTrace(); false
    }
}
