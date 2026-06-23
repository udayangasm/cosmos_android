package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone
import android.util.Log
import com.pos.cosmos.api.ApiConfig

data class ProfitData(
    val salesValue: Double = 0.0,
    val unitCost: Double = 0.0
)

data class CashSummaryData(
    val cashReceived: Double = 0.0,
    val chequeReceived: Double = 0.0,
    val cashExpenses: Double = 0.0,
    val chequeExpenses: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var selectedPeriod by remember { mutableStateOf("daily") }
    var fromDate by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.time) }
    var toDate by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time) }

    var sales by remember { mutableStateOf("0.00") }
    var costs by remember { mutableStateOf("0.00") }
    var profit by remember { mutableStateOf("0.00") }
    var profitPercentageBySales by remember { mutableStateOf("0.00") }
    var profitPercentageByCosts by remember { mutableStateOf("0.00") }
    var cashCollections by remember { mutableStateOf("0.00") }
    var checkCollections by remember { mutableStateOf("0.00") }
    var cashExpenses by remember { mutableStateOf("0.00") }
    var chequePayments by remember { mutableStateOf("0.00") }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerMode by remember { mutableStateOf<String?>(null) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    // Format number with commas and 2 decimal places
    fun formatNumber(value: Double): String {
        return String.format("%,.2f", value)
    }

    // Format date for API in ISO 8601 UTC format
    fun formatDateForAPI(date: Date): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    // Calculate percentages
    fun calculatePercentages(salesValue: Double, costValue: Double, profitValue: Double) {
        val sales = salesValue
        val cost = costValue
        val profit = profitValue

        val percentageBySales = if (sales > 0) (profit / sales) * 100 else 0.0
        val percentageByCosts = if (cost > 0) (profit / cost) * 100 else 0.0

        profitPercentageBySales = formatNumber(percentageBySales)
        profitPercentageByCosts = formatNumber(percentageByCosts)
    }

    // Helper function to create date at start of day in local timezone (00:00:00)
    fun createDateAtStartOfDay(year: Int, month: Int, day: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    // Helper function to create date at end of day in local timezone (23:59:59.999)
    fun createDateAtEndOfDay(year: Int, month: Int, day: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, 23, 59, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.time
    }

    // Update date range when period changes
    LaunchedEffect(selectedPeriod) {
        val today = Calendar.getInstance()
        val newFromDate = Calendar.getInstance()
        val newToDate = Calendar.getInstance()

        when (selectedPeriod) {
            "daily" -> {
                newFromDate.time = today.time
                newFromDate.set(Calendar.HOUR_OF_DAY, 0)
                newFromDate.set(Calendar.MINUTE, 0)
                newFromDate.set(Calendar.SECOND, 0)

                newToDate.time = today.time
                newToDate.set(Calendar.HOUR_OF_DAY, 23)
                newToDate.set(Calendar.MINUTE, 59)
                newToDate.set(Calendar.SECOND, 59)
            }
            "weekly" -> {
                val day = today.get(Calendar.DAY_OF_WEEK)
                val diff = today.get(Calendar.DATE) - day + (if (day == Calendar.SUNDAY) -6 else 1)
                newFromDate.set(Calendar.DATE, diff)
                newFromDate.set(Calendar.HOUR_OF_DAY, 0)
                newFromDate.set(Calendar.MINUTE, 0)
                newFromDate.set(Calendar.SECOND, 0)

                newToDate.time = newFromDate.time
                newToDate.add(Calendar.DATE, 6)
                newToDate.set(Calendar.HOUR_OF_DAY, 23)
                newToDate.set(Calendar.MINUTE, 59)
                newToDate.set(Calendar.SECOND, 59)
            }
            "monthly" -> {
                newFromDate.set(Calendar.DATE, 1)
                newFromDate.set(Calendar.HOUR_OF_DAY, 0)
                newFromDate.set(Calendar.MINUTE, 0)
                newFromDate.set(Calendar.SECOND, 0)

                newToDate.set(Calendar.DATE, 1)
                newToDate.add(Calendar.MONTH, 1)
                newToDate.add(Calendar.DATE, -1)
                newToDate.set(Calendar.HOUR_OF_DAY, 23)
                newToDate.set(Calendar.MINUTE, 59)
                newToDate.set(Calendar.SECOND, 59)
            }
            "yearly" -> {
                newFromDate.set(Calendar.MONTH, Calendar.JANUARY)
                newFromDate.set(Calendar.DATE, 1)
                newFromDate.set(Calendar.HOUR_OF_DAY, 0)
                newFromDate.set(Calendar.MINUTE, 0)
                newFromDate.set(Calendar.SECOND, 0)

                newToDate.set(Calendar.MONTH, Calendar.DECEMBER)
                newToDate.set(Calendar.DATE, 31)
                newToDate.set(Calendar.HOUR_OF_DAY, 23)
                newToDate.set(Calendar.MINUTE, 59)
                newToDate.set(Calendar.SECOND, 59)
            }
        }

        fromDate = newFromDate.time
        toDate = newToDate.time
    }

    fun handleProfitRequest() {
        scope.launch {
            loading = true
            errorMessage = ""

            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val gson = Gson()

                        // Profit API call
                        val profitConnection = URL("${ApiConfig.BASE_URL_8080}/profit/getAllProfit")
                            .openConnection() as HttpURLConnection
                        profitConnection.requestMethod = "POST"
                        profitConnection.setRequestProperty("Authorization", "Bearer $token")
                        profitConnection.setRequestProperty("Content-Type", "application/json")
                        profitConnection.connectTimeout = 30000
                        profitConnection.readTimeout = 30000
                        profitConnection.doOutput = true

                        val profitPayload = mapOf(
                            "companyCode" to 0,
                            "fromDate" to formatDateForAPI(fromDate),
                            "toDate" to formatDateForAPI(toDate),
                            "salesRefId" to 0
                        )
                        val profitJsonPayload = gson.toJson(profitPayload)
                        Log.d("ProfitScreen", "Profit Request Payload: $profitJsonPayload")
                        profitConnection.outputStream.bufferedWriter().use {
                            it.write(profitJsonPayload)
                        }

                        // Cash summary API call
                        val cashConnection = URL("${ApiConfig.BASE_URL_8080}/profit/cashSummary")
                            .openConnection() as HttpURLConnection
                        cashConnection.requestMethod = "POST"
                        cashConnection.setRequestProperty("Authorization", "Bearer $token")
                        cashConnection.setRequestProperty("Content-Type", "application/json")
                        cashConnection.connectTimeout = 30000
                        cashConnection.readTimeout = 30000
                        cashConnection.doOutput = true

                        val cashPayload = mapOf(
                            "fromDate" to formatDateForAPI(fromDate),
                            "toDate" to formatDateForAPI(toDate),
                            "username" to ""
                        )
                        cashConnection.outputStream.bufferedWriter().use {
                            it.write(gson.toJson(cashPayload))
                        }

                        // Check responses
                        Log.d("ProfitScreen", "Profit Response Code: ${profitConnection.responseCode}")
                        Log.d("ProfitScreen", "Cash Response Code: ${cashConnection.responseCode}")

                        if (profitConnection.responseCode == 200 && cashConnection.responseCode == 200) {
                            val profitContent = profitConnection.inputStream.bufferedReader().use { it.readText() }
                            val cashContent = cashConnection.inputStream.bufferedReader().use { it.readText() }

                            Log.d("ProfitScreen", "Profit Response: $profitContent")
                            Log.d("ProfitScreen", "Cash Response: $cashContent")

                            try {
                                val profitData = gson.fromJson(profitContent, ProfitData::class.java)
                                val cashData = gson.fromJson(cashContent, CashSummaryData::class.java)

                                // Process profit data
                                val salesValue = profitData.salesValue
                                val costValue = profitData.unitCost
                                val profitValue = salesValue - costValue

                                sales = formatNumber(salesValue)
                                costs = formatNumber(costValue)
                                profit = formatNumber(profitValue)
                                calculatePercentages(salesValue, costValue, profitValue)

                                // Process cash summary data
                                cashCollections = formatNumber(cashData.cashReceived)
                                checkCollections = formatNumber(cashData.chequeReceived)
                                cashExpenses = formatNumber(cashData.cashExpenses)
                                chequePayments = formatNumber(cashData.chequeExpenses)

                                true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                        } else {
                            Log.e("ProfitScreen", "API Error - Profit: ${profitConnection.responseCode}, Cash: ${cashConnection.responseCode}")
                            false
                        }
                    } catch (e: Exception) {
                        Log.e("ProfitScreen", "Exception: ${e.message}", e)
                        e.printStackTrace()
                        false
                    }
                }

                if (!result) {
                    errorMessage = "No profit data found for the selected date range. Please try a different date range."
                }
            } finally {
                loading = false
            }
        }
    }

    fun formatDate(date: Date): String {
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profit Report", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F3460))
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Period Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("daily", "weekly", "monthly", "yearly").forEach { period ->
                        Button(
                            onClick = { selectedPeriod = period },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedPeriod == period) Color(0xFF00D4FF) else Color(0xFF16213E)
                            ),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Text(
                                period.replaceFirstChar { it.uppercase() },
                                color = if (selectedPeriod == period) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Range Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("From Date", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Button(
                            onClick = { datePickerMode = "from"; showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16213E))
                        ) {
                            Text(formatDate(fromDate), color = Color.White)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("To Date", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Button(
                            onClick = { datePickerMode = "to"; showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16213E))
                        ) {
                            Text(formatDate(toDate), color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Get Report Button
                Button(
                    onClick = { handleProfitRequest() },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Text("Get Profit Report", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color(0xFFFF3B30), modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Results
                if (!loading && errorMessage.isEmpty() && sales != "0.00") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Sales Information", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Sales:", sales)
                            ResultRow("Costs:", costs)
                            ResultRow("Profit:", profit)

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Profit Percentages", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Profit % (by Sales):", "$profitPercentageBySales%")
                            ResultRow("Profit % (by Costs):", "$profitPercentageByCosts%")

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Collections", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Cash Collections:", cashCollections)
                            ResultRow("Cheque Collections:", checkCollections)

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Expenses", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Cash Expenses:", cashExpenses)
                            ResultRow("Cheque Payments:", chequePayments)
                        }
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker && datePickerMode != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (datePickerMode == "from") fromDate.time else toDate.time
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = millis
                            val year = calendar.get(Calendar.YEAR)
                            val month = calendar.get(Calendar.MONTH)
                            val day = calendar.get(Calendar.DAY_OF_MONTH)

                            if (datePickerMode == "from") {
                                fromDate = createDateAtStartOfDay(year, month, day)
                            } else {
                                toDate = createDateAtEndOfDay(year, month, day)
                            }
                        }
                        showDatePicker = false
                    }
                ) {
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
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFB0B0B0), fontSize = 14.sp)
        Text(value, color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
