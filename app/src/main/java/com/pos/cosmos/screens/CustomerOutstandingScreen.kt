package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import com.pos.cosmos.api.ApiConfig

data class CreditBill(
    val billNo: String? = "",
    val billingDate: String? = "",
    val netAmount: Double = 0.0,
    val discount: Double = 0.0,
    val returnAmount: Double = 0.0,
    val creditNote: Double = 0.0,
    val paidAmount: Double = 0.0,
    val age: Int = 0
)

data class OutstandingCustomer(
    val customerId: Long = 0,
    val customerName: String? = "",
    val address: String? = "",
    val total: Double = 0.0,
    val noOfInvoices: Int = 0,
    val creditBills: List<CreditBill> = emptyList()
)

data class CustomerInfo(
    val id: Long = 0,
    val companyCode: Int = 0,
    val name: String? = "",
    val address: String? = "",
    val telephone: String? = "",
    val email: String? = "",
    val status: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerOutstandingScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var filteredCustomers by remember { mutableStateOf<List<OutstandingCustomer>>(emptyList()) }
    var allCustomers by remember { mutableStateOf<List<OutstandingCustomer>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expandedCustomers by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    fun toggleBills(customerId: Long) {
        expandedCustomers = if (expandedCustomers.contains(customerId)) {
            expandedCustomers - customerId
        } else {
            expandedCustomers + customerId
        }
    }

    fun handleSearch(newValue: TextFieldValue) {
        query = newValue
        val text = newValue.text
        filteredCustomers = if (text.isEmpty()) {
            allCustomers
        } else {
            allCustomers.filter {
                (it.customerName ?: "").lowercase().contains(text.lowercase()) ||
                        (it.address ?: "").lowercase().contains(text.lowercase())
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            scope.launch {
                loading = true
                try {
                    val customers = withContext(Dispatchers.IO) {
                        try {
                            val gson = Gson()

                            // Fetch outstanding data
                            val outstandingConnection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllCustomerOutStanding?salesRefId=0")
                                .openConnection() as HttpURLConnection
                            outstandingConnection.requestMethod = "GET"
                            outstandingConnection.setRequestProperty("Authorization", "Bearer $token")
                            outstandingConnection.setRequestProperty("Content-Type", "application/json")
                            outstandingConnection.connectTimeout = 30000
                            outstandingConnection.readTimeout = 30000

                            val outstandingData = if (outstandingConnection.responseCode == 200) {
                                val content = outstandingConnection.inputStream.bufferedReader().use { it.readText() }
                                try {
                                    gson.fromJson(content, Array<OutstandingCustomer>::class.java).toList()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }

                            // Fetch customer details
                            val customerConnection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
                                .openConnection() as HttpURLConnection
                            customerConnection.requestMethod = "GET"
                            customerConnection.setRequestProperty("Authorization", "Bearer $token")
                            customerConnection.setRequestProperty("Content-Type", "application/json")
                            customerConnection.connectTimeout = 30000
                            customerConnection.readTimeout = 30000

                            val customerData = if (customerConnection.responseCode == 200) {
                                val content = customerConnection.inputStream.bufferedReader().use { it.readText() }
                                try {
                                    gson.fromJson(content, Array<CustomerInfo>::class.java).toList()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }

                            // Merge outstanding data with customer details
                            val mergedData = outstandingData.map { outstanding ->
                                val customerInfo = customerData.find { it.id == outstanding.customerId }
                                outstanding.copy(
                                    customerName = customerInfo?.name ?: outstanding.customerName,
                                    address = customerInfo?.address ?: outstanding.address
                                )
                            }

                            // Sort and return merged data
                            mergedData.sortedBy { it.customerName ?: "" }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            emptyList()
                        }
                    }
                    allCustomers = customers
                    filteredCustomers = customers
                } finally {
                    loading = false
                }
            }
        } else {
            loading = false
        }
    }

    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F3460)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFE94560))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Outstanding", color = Color.White) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Customer Outstanding",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            )

            // Search Box
            TextField(
                value = query,
                onValueChange = { handleSearch(it) },
                label = { Text("Search") },
                placeholder = { Text("Search by name or address...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Customer List
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (filteredCustomers.isEmpty()) {
                        Text(
                            "No customers found",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(20.dp)
                        )
                    } else {
                        filteredCustomers.forEach { customer ->
                            Column {
                                // Customer Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggleBills(customer.customerId) }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = (customer.customerName ?: "Unknown Customer").ifEmpty { "Unknown Customer" },
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = (customer.address ?: "No address").ifEmpty { "No address" },
                                            color = Color(0xFFB0B0B0),
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Rs. ${"%.2f".format(customer.total)}",
                                            color = Color(0xFF34C759),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${customer.noOfInvoices} invoices",
                                            color = Color(0xFFB0B0B0),
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        if (expandedCustomers.contains(customer.customerId)) "▼" else "►",
                                        color = Color(0xFFB0B0B0),
                                        fontSize = 16.sp
                                    )
                                }

                                // Bills Details (Expanded)
                                if (expandedCustomers.contains(customer.customerId) && customer.creditBills.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F3460), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "Bill Details",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        customer.creditBills.forEach { bill ->
                                            val balance = bill.netAmount - bill.paidAmount - bill.creditNote - bill.returnAmount - bill.discount
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Bill: ${(bill.billNo ?: "N/A").ifEmpty { "N/A" }}",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = (bill.billingDate ?: "No date").ifEmpty { "No date" },
                                                            color = Color(0xFFB0B0B0),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = "Amount: ${"%.2f".format(bill.netAmount)}",
                                                            color = Color(0xFF34C759),
                                                            fontSize = 11.sp
                                                        )
                                                        Text(
                                                            text = "Balance: ${"%.2f".format(balance)}",
                                                            color = if (balance > 0) Color(0xFFFF3B30) else Color(0xFF34C759),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Divider(color = Color(0xFF133C55), thickness = 0.5.dp)
                                        }
                                    }
                                }

                                Divider(color = Color(0xFF133C55), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Footer
            Text(
                "Showing ${filteredCustomers.size} customer${if (filteredCustomers.size != 1) "s" else ""}",
                color = Color(0xFFB0B0B0),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
            )
        }
    }
}
