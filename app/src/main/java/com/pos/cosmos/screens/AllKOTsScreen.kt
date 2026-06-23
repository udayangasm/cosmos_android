package com.pos.cosmos.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.google.gson.JsonArray
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

// Data class for active kitchen order
data class ActiveKOT(
    val id: Long,
    val kitchenOrderNo: String,
    val orderDateTime: String,
    val status: String,
    val tableNo: String?,
    val orderType: String,
    val notes: String?,
    val total: Double,
    val orderItems: List<KOTOrderItem>
)

data class KOTOrderItem(
    val id: Long,
    val itemId: Int,
    val itemName: String,
    val qty: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllKOTsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var activeOrders by remember { mutableStateOf<List<ActiveKOT>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var expandedOrderId by remember { mutableStateOf<Long?>(null) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    // Stats
    val stats = remember(activeOrders) {
        mapOf(
            "NEW" to activeOrders.count { it.status == "NEW" },
            "PREPARING" to activeOrders.count { it.status == "PREPARING" },
            "READY" to activeOrders.count { it.status == "READY" }
        )
    }

    // Filtered orders
    val filteredOrders = remember(activeOrders, selectedFilter) {
        if (selectedFilter == "ALL") {
            activeOrders.filter { it.status in listOf("NEW", "PREPARING", "READY") }
        } else {
            activeOrders.filter { it.status == selectedFilter }
        }
    }

    // Load orders on launch
    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                val orders = fetchActiveKOTs(token!!)
                activeOrders = orders
                loading = false
            }
        }
    }

    // Refresh function
    fun refreshOrders() {
        if (token != null) {
            refreshing = true
            scope.launch {
                val orders = fetchActiveKOTs(token!!)
                activeOrders = orders
                refreshing = false
            }
        }
    }

    // Update status function
    fun updateOrderStatus(order: ActiveKOT, newStatus: String) {
        if (token != null) {
            scope.launch {
                val success = updateKOTStatus(token!!, order, newStatus)
                if (success) {
                    successMessage = "Order status updated to $newStatus"
                    showSuccessDialog = true
                    refreshOrders()
                } else {
                    errorMessage = "Failed to update order status"
                    showErrorDialog = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Active Kitchen Orders",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${filteredOrders.size} orders",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshOrders() },
                        enabled = !refreshing
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F5F5))
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label = "New",
                    count = stats["NEW"] ?: 0,
                    color = Color(0xFF2196F3),
                    isSelected = selectedFilter == "NEW",
                    onClick = { selectedFilter = if (selectedFilter == "NEW") "ALL" else "NEW" },
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Preparing",
                    count = stats["PREPARING"] ?: 0,
                    color = Color(0xFFFF9800),
                    isSelected = selectedFilter == "PREPARING",
                    onClick = { selectedFilter = if (selectedFilter == "PREPARING") "ALL" else "PREPARING" },
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Ready",
                    count = stats["READY"] ?: 0,
                    color = Color(0xFF4CAF50),
                    isSelected = selectedFilter == "READY",
                    onClick = { selectedFilter = if (selectedFilter == "READY") "ALL" else "READY" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Content
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF1A237E))
                }
            } else if (filteredOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color(0xFFBDBDBD)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No active orders",
                            fontSize = 18.sp,
                            color = Color(0xFF757575)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { refreshOrders() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        KOTCard(
                            order = order,
                            isExpanded = expandedOrderId == order.id,
                            onExpandToggle = {
                                expandedOrderId = if (expandedOrderId == order.id) null else order.id
                            },
                            onStartCooking = { updateOrderStatus(order, "PREPARING") },
                            onMarkReady = { updateOrderStatus(order, "READY") },
                            onCancel = { updateOrderStatus(order, "CANCELLED") }
                        )
                    }
                }
            }
        }
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50)) },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(70.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color(0xFF757575)
            )
        }
    }
}

@Composable
fun KOTCard(
    order: ActiveKOT,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onStartCooking: () -> Unit,
    onMarkReady: () -> Unit,
    onCancel: () -> Unit
) {
    val statusColor = when (order.status) {
        "NEW" -> Color(0xFF2196F3)
        "PREPARING" -> Color(0xFFFF9800)
        "READY" -> Color(0xFF4CAF50)
        else -> Color(0xFF9E9E9E)
    }

    val statusBgColor = when (order.status) {
        "NEW" -> Color(0xFFE3F2FD)
        "PREPARING" -> Color(0xFFFFF3E0)
        "READY" -> Color(0xFFE8F5E9)
        else -> Color(0xFFF5F5F5)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // KOT Number and Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "KOT #${order.kitchenOrderNo}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .background(statusBgColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = order.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Order details row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF757575)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatOrderTime(order.orderDateTime),
                                fontSize = 12.sp,
                                color = Color(0xFF757575)
                            )
                        }

                        // Table/Takeaway
                        if (order.tableNo != null && order.tableNo.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF757575)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Table ${order.tableNo}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF757575)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Takeaway",
                                    fontSize = 12.sp,
                                    color = Color(0xFF757575)
                                )
                            }
                        }
                    }
                }

                // Items count badge
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFF5F5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${order.orderItems.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E)
                    )
                }

                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF757575)
                )
            }

            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFAFAFA))
                        .padding(16.dp)
                ) {
                    // Items List
                    Text(
                        text = "Order Items",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    order.orderItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF1A237E), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${item.qty}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.itemName,
                                    fontSize = 13.sp,
                                    color = Color(0xFF424242),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Rs. ${String.format("%.2f", item.totalPrice)}",
                                fontSize = 13.sp,
                                color = Color(0xFF757575)
                            )
                        }
                    }

                    // Notes
                    if (!order.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECB3)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFFF8F00)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = order.notes,
                                    fontSize = 12.sp,
                                    color = Color(0xFF5D4037)
                                )
                            }
                        }
                    }

                    // Total
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        Text(
                            text = "Rs. ${String.format("%.2f", order.total)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }

                    // Action Buttons
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (order.status) {
                            "NEW" -> {
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", fontSize = 13.sp)
                                }
                                Button(
                                    onClick = onStartCooking,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Cooking", fontSize = 13.sp)
                                }
                            }
                            "PREPARING" -> {
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel", fontSize = 13.sp)
                                }
                                Button(
                                    onClick = onMarkReady,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mark Ready", fontSize = 13.sp)
                                }
                            }
                            "READY" -> {
                                Button(
                                    onClick = { /* TODO: Generate bill */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generate Bill", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper function to format order time
private fun formatOrderTime(dateTimeStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = inputFormat.parse(dateTimeStr)
        date?.let { outputFormat.format(it) } ?: dateTimeStr
    } catch (e: Exception) {
        try {
            // Try alternative format
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTimeStr)
            date?.let { outputFormat.format(it) } ?: dateTimeStr
        } catch (e2: Exception) {
            dateTimeStr
        }
    }
}

// Fetch active KOTs from API
private suspend fun fetchActiveKOTs(token: String): List<ActiveKOT> {
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL("${ApiConfig.BASE_URL_8080}/kitchen-order")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            if (connection.responseCode == 200) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d("AllKOTsScreen", "Fetched KOTs: ${content.take(500)}...")

                val gson = Gson()
                val ordersArray = gson.fromJson(content, JsonArray::class.java)

                ordersArray.mapNotNull { element ->
                    try {
                        val orderObj = element.asJsonObject
                        val id = orderObj.get("id")?.asLong ?: return@mapNotNull null
                        val kitchenOrderNo = orderObj.get("kitchenOrderNo")?.asString ?: orderObj.get("orderNo")?.asString ?: "$id"
                        val orderDateTime = orderObj.get("orderDateTime")?.asString ?: ""
                        val status = orderObj.get("status")?.asString ?: "NEW"
                        val tableNo = orderObj.get("tableNo")?.asString
                        val orderType = orderObj.get("orderType")?.asString ?: "takeaway"
                        val notes = orderObj.get("notes")?.asString

                        // Parse order items
                        val orderItemsArray = orderObj.getAsJsonArray("orderItems")
                        val orderItems = orderItemsArray?.mapNotNull { itemElement ->
                            try {
                                val itemObj = itemElement.asJsonObject
                                val qty = itemObj.get("qty")?.asInt ?: 1
                                val unitPrice = itemObj.get("unitPrice")?.asDouble
                                    ?: itemObj.get("price")?.asDouble
                                    ?: 0.0
                                KOTOrderItem(
                                    id = itemObj.get("id")?.asLong ?: 0,
                                    itemId = itemObj.get("itemId")?.asInt ?: 0,
                                    itemName = itemObj.get("itemName")?.asString
                                        ?: itemObj.get("name")?.asString
                                        ?: "Unknown Item",
                                    qty = qty,
                                    unitPrice = unitPrice,
                                    totalPrice = itemObj.get("totalPrice")?.asDouble
                                        ?: (qty * unitPrice)
                                )
                            } catch (e: Exception) {
                                Log.e("AllKOTsScreen", "Error parsing order item: ${e.message}")
                                null
                            }
                        } ?: emptyList()

                        // Calculate total from order items (more reliable than API total)
                        val calculatedTotal = orderItems.sumOf { it.totalPrice }
                        val apiTotal = orderObj.get("total")?.asDouble ?: 0.0
                        val total = if (calculatedTotal > 0) calculatedTotal else apiTotal

                        ActiveKOT(
                            id = id,
                            kitchenOrderNo = kitchenOrderNo,
                            orderDateTime = orderDateTime,
                            status = status,
                            tableNo = tableNo,
                            orderType = orderType,
                            notes = notes,
                            total = total,
                            orderItems = orderItems
                        )
                    } catch (e: Exception) {
                        Log.e("AllKOTsScreen", "Error parsing KOT: ${e.message}")
                        null
                    }
                }
            } else {
                Log.e("AllKOTsScreen", "Failed to fetch KOTs: ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("AllKOTsScreen", "Exception fetching KOTs: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}

// Update KOT status
private suspend fun updateKOTStatus(token: String, order: ActiveKOT, newStatus: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val connection = URL("${ApiConfig.BASE_URL_8080}/kitchen-order")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            // Build update payload
            val updatePayload = JsonObject().apply {
                addProperty("id", order.id)
                addProperty("status", newStatus)
                addProperty("kitchenOrderNo", order.kitchenOrderNo)
                addProperty("orderDateTime", order.orderDateTime)
                addProperty("tableNo", order.tableNo)
                addProperty("orderType", order.orderType)
                addProperty("notes", order.notes)
                addProperty("total", order.total)

                // Add order items
                val itemsArray = JsonArray()
                order.orderItems.forEach { item ->
                    val itemObj = JsonObject().apply {
                        addProperty("id", item.id)
                        addProperty("itemId", item.itemId)
                        addProperty("itemName", item.itemName)
                        addProperty("qty", item.qty)
                        addProperty("unitPrice", item.unitPrice)
                        addProperty("totalPrice", item.totalPrice)
                    }
                    itemsArray.add(itemObj)
                }
                add("orderItems", itemsArray)
            }

            val jsonString = updatePayload.toString()
            Log.d("AllKOTsScreen", "Updating KOT status: $jsonString")

            connection.outputStream.bufferedWriter().use { it.write(jsonString) }

            val responseCode = connection.responseCode
            Log.d("AllKOTsScreen", "Update response code: $responseCode")

            responseCode == 200 || responseCode == 201
        } catch (e: Exception) {
            Log.e("AllKOTsScreen", "Exception updating KOT status: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
