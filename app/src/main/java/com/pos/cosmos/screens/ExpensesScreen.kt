package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.models.Expense
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
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var canDelete by remember { mutableStateOf(false) }

    var displayExpenseDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    fun refreshExpenses() {
        scope.launch {
            withContext(Dispatchers.IO) {
                expenses = fetchAllExpenses(token!!)
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            withContext(Dispatchers.IO) {
                expenses = fetchAllExpenses(token!!)
                canDelete = fetchExpenseDeletePermission(token!!)
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingExpense = null
                    displayExpenseDialog = true
                },
                containerColor = Color(0xFFE94560),
                contentColor = Color.White
            ) { Text("Add Expense") }
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
                // Total expenses
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Expenses", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF333333))
                            Text(
                                String.format(Locale.US, "%.2f", expenses.sumOf { it.amount }),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE94560)
                            )
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

                if (expenses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Text(
                                "No expenses found.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                items(expenses) { expense ->
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (expense.description.isNotBlank()) expense.description else "(No description)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "Amount: ${String.format(Locale.US, "%.2f", expense.amount)}",
                                    fontSize = 13.sp,
                                    color = Color(0xFFE94560)
                                )
                                Text("Date: ${formatExpenseDate(expense.date)}", fontSize = 11.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                editingExpense = expense
                                displayExpenseDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF4D96FF))
                            }
                            if (canDelete) {
                                IconButton(onClick = { expenseToDelete = expense }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE94560))
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Add / Edit dialog
    if (displayExpenseDialog) {
        val existing = editingExpense
        var description by remember(existing) { mutableStateOf(existing?.description ?: "") }
        var amountText by remember(existing) {
            mutableStateOf(if (existing != null && existing.amount > 0) existing.amount.toString() else "")
        }
        var expenseDate by remember(existing) {
            mutableStateOf(parseExpenseDateMillis(existing?.date) ?: System.currentTimeMillis())
        }
        var showDatePicker by remember { mutableStateOf(false) }
        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(expenseDate))
        val enteredAmount = amountText.toDoubleOrNull() ?: 0.0

        AlertDialog(
            onDismissRequest = { displayExpenseDialog = false },
            title = { Text(if (existing == null) "Add Expense" else "Edit Expense") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { },
                        label = { Text("Date") },
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
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount <= 0.0) {
                            errorMessage = "Amount must be greater than 0"
                            successMessage = ""
                            return@Button
                        }
                        val desc = description
                        val dateIso = expenseDateIso(expenseDate)
                        val isEdit = existing != null
                        val expense = Expense(
                            id = existing?.id ?: 0,
                            date = dateIso,
                            amount = amount,
                            description = desc,
                            categoryId = existing?.categoryId ?: 0
                        )
                        displayExpenseDialog = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (isEdit) updateExpense(token!!, expense) else addExpense(token!!, expense)
                            }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refreshExpenses()
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Button(onClick = { displayExpenseDialog = false }) { Text("Cancel") }
            }
        )

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = expenseDate)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        expenseDate = datePickerState.selectedDateMillis ?: expenseDate
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
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense") },
            text = { Text("Are you sure to delete this expense of ${String.format(Locale.US, "%.2f", expense.amount)}?") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                    onClick = {
                        val id = expense.id
                        expenseToDelete = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { deleteExpense(token!!, id) }
                            if (result.first) {
                                successMessage = result.second
                                errorMessage = ""
                                refreshExpenses()
                            } else {
                                errorMessage = result.second
                                successMessage = ""
                            }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { expenseToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

// Date sent to backend: ISO datetime (mirrors SupplierPaymentsScreen.nowIso style, midday to avoid TZ rollover)
private fun expenseDateIso(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    // Normalise to noon UTC so the calendar date is preserved regardless of server TZ
    val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return fmt.format(Date(cal.timeInMillis))
}

private fun parseExpenseDateMillis(dateStr: String?): Long? {
    if (dateStr.isNullOrBlank()) return null
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        try {
            val fmt = SimpleDateFormat(p, Locale.US)
            return fmt.parse(dateStr)?.time
        } catch (_: Exception) { }
    }
    return null
}

private fun formatExpenseDate(dateStr: String): String {
    val millis = parseExpenseDateMillis(dateStr) ?: return dateStr
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
}

// ---------------- API helpers — bill-service (8080) /account/... (mirror SupplierPaymentsScreen style) ----------------

// GET /account/getAllExpenses?companyCode=0
private suspend fun fetchAllExpenses(token: String): List<Expense> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/account/getAllExpenses?companyCode=0")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            Gson().fromJson(content, Array<Expense>::class.java).toList()
        } else emptyList()
    } catch (e: Exception) {
        e.printStackTrace(); emptyList()
    }
}

// POST /account/addExpense
private suspend fun addExpense(token: String, expense: Expense): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/account/addExpense")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        val jsonBody = Gson().toJson(expense)
        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
            Pair(true, "Expense added successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Add failed ($responseCode): $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "Add failed: ${e.message}")
    }
}

// PUT /account/updateExpense
private suspend fun updateExpense(token: String, expense: Expense): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/account/updateExpense")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        val jsonBody = Gson().toJson(expense)
        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
            Pair(true, "Expense updated successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Update failed ($responseCode): $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "Update failed: ${e.message}")
    }
}

// DELETE /account/deleteExpense?expenseId=
private suspend fun deleteExpense(token: String, id: Long): Pair<Boolean, String> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/account/deleteExpense?expenseId=$id")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode in 200..299) {
            Pair(true, "Expense deleted successfully!")
        } else {
            val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            Pair(false, "Delete failed: $errorMsg")
        }
    } catch (e: Exception) {
        e.printStackTrace(); Pair(false, "Delete failed: ${e.message}")
    }
}

// Delete permission — dealer-service (8090) /admin/getUserPermissionsByToken
private suspend fun fetchExpenseDeletePermission(token: String): Boolean {
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
