package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
import com.pos.cosmos.api.ApiConfig

data class StockInvoiceItem(
    val invoiceName: String? = null,
    val unitCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val qty: Double = 0.0,
    val alreadyBilledQty: Int = 0,
    val returnQty: Int = 0,
    val reservedQty: Int = 0
)

data class StockItem(
    val itemId: String? = null,
    val itemCode: String? = null,
    val itemName: String? = null,
    val supplier: String? = null,
    val sellingPrice: Double = 0.0,
    val totalQty: Double = 0.0,
    val stockInvoiceItems: List<StockInvoiceItem> = emptyList()
)

data class StockItemsResponse(
    val stockItemList: List<StockItem>,
    val totalElements: Int? = null,
    val totalPages: Int? = null,
    val last: Boolean? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreenFull(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var stockItems by remember { mutableStateOf<List<StockItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expandedItems by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totalElements by remember { mutableStateOf(0) }

    // Calculate dynamic page size based on screen height
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    // Each row is approximately 48dp, header ~56dp, search ~80dp, footer ~50dp
    // Available height for items = screenHeight - 186dp (header + search + footer + padding)
    val pageSize = remember(screenHeightDp) {
        val estimatedRowHeight = 48
        val availableHeight = screenHeightDp - 186
        val itemsPerScreen = (availableHeight / estimatedRowHeight).coerceAtLeast(10)
        // Load 2x items per screen for smooth scrolling buffer
        (itemsPerScreen * 2).coerceIn(20, 100)
    }

    // Debug: Log the calculated page size
    LaunchedEffect(pageSize) {
        android.util.Log.d("StockScreen", "Screen height: ${screenHeightDp}dp, Page size: $pageSize")
    }

    // Pagination state
    var currentPage by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var isLastPage by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    // Function to load items from backend
    suspend fun loadItems(page: Int, search: String, append: Boolean, size: Int) {
        if (token == null) return

        val result = withContext(Dispatchers.IO) {
            fetchStockItems(token!!, page, size, search)
        }

        if (append) {
            stockItems = stockItems + result.items
        } else {
            stockItems = result.items
        }
        isLastPage = result.isLastPage
        if (result.totalElements > 0) {
            totalElements = result.totalElements
        }
    }

    // Load more items when scrolling near the bottom
    LaunchedEffect(listState, pageSize) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItemsCount = layoutInfo.totalItemsCount
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                // Load more when we're within 5 items of the end
                if (lastVisibleItem >= totalItemsCount - 5 && !isLoadingMore && !isLastPage && !loading && totalItemsCount > 0) {
                    isLoadingMore = true
                    currentPage++
                    scope.launch {
                        loadItems(currentPage, searchQuery, append = true, size = pageSize)
                        isLoadingMore = false
                    }
                }
            }
    }

    // Initial load
    LaunchedEffect(token, pageSize) {
        if (token != null) {
            loading = true
            currentPage = 0
            isLastPage = false
            loadItems(0, "", append = false, size = pageSize)
            loading = false
        }
    }

    fun handleSearch(query: String) {
        searchQuery = query
        currentPage = 0
        isLastPage = false
        scope.launch {
            loading = true
            // Filter locally from loaded items for responsiveness
            if (query.isEmpty()) {
                // Reload from backend when clearing search
                loadItems(0, "", append = false, size = pageSize)
            } else {
                // For search, filter locally from current items
                stockItems = stockItems.filter { item ->
                    (item.itemCode?.contains(query, ignoreCase = true) ?: false) ||
                    (item.itemName?.contains(query, ignoreCase = true) ?: false)
                }
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Management", color = Color.White) },
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
        ) {
            // Search input
            TextField(
                value = searchQuery,
                onValueChange = { handleSearch(it) },
                label = { Text("Search items...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
            )

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE94560))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Table header
                    item {
                        StockTableHeader()
                    }

                    // Stock items (displayed with pagination)
                    items(stockItems) { item ->
                        val isExpanded = expandedItems.contains(item.itemId + item.supplier)
                        Column {
                            StockItemRow(
                                item = item,
                                isExpanded = isExpanded,
                                onExpandClick = {
                                    expandedItems = if (isExpanded) {
                                        expandedItems - (item.itemId + item.supplier)
                                    } else {
                                        expandedItems + (item.itemId + item.supplier)
                                    }
                                }
                            )
                            if (isExpanded) {
                                StockInvoiceItemsTable(item.stockInvoiceItems)
                            }
                        }
                    }

                    // Loading indicator when loading more
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

                    // Footer
                    item {
                        val totalText = if (totalElements > 0) totalElements else stockItems.size
                        Text(
                            "Loaded ${stockItems.size} of $totalText item${if (totalText != 1) "s" else ""}${if (!isLastPage) " (scroll for more)" else ""}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StockTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF007BFF))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Code", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Name", modifier = Modifier.weight(1.5f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Supplier", modifier = Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Price", modifier = Modifier.weight(0.8f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Qty", modifier = Modifier.weight(0.7f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun StockItemRow(
    item: StockItem,
    isExpanded: Boolean,
    onExpandClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExpandClick() }
            .background(Color(0xFF1A1A2E))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(item.itemCode ?: "-", modifier = Modifier.weight(1f), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(item.itemName ?: "-", modifier = Modifier.weight(1.5f), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(item.supplier ?: "-", modifier = Modifier.weight(1f), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(String.format("%.2f", item.sellingPrice), modifier = Modifier.weight(0.8f), color = Color.White, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            if (item.totalQty % 1 == 0.0) item.totalQty.toInt().toString() else String.format("%.3f", item.totalQty),
            modifier = Modifier.weight(0.7f),
            color = Color.White,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = "Expand",
            tint = Color(0xFFE94560),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun StockInvoiceItemsTable(invoiceItems: List<StockInvoiceItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F3460))
            .padding(8.dp)
    ) {
        // Header for child table
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF007BFF))
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Invoice", modifier = Modifier.width(120.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Unit Cost", modifier = Modifier.width(80.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Sell Price", modifier = Modifier.width(80.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Qty", modifier = Modifier.width(60.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Billed", modifier = Modifier.width(60.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Return", modifier = Modifier.width(60.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Reserved", modifier = Modifier.width(80.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }

        // Invoice items rows
        invoiceItems.forEach { invoiceItem ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF133C55))
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(invoiceItem.invoiceName ?: "-", modifier = Modifier.width(120.dp), color = Color.White, fontSize = 11.sp)
                Text(String.format("%.2f", invoiceItem.unitCost), modifier = Modifier.width(80.dp), color = Color.White, fontSize = 11.sp)
                Text(String.format("%.2f", invoiceItem.sellingPrice), modifier = Modifier.width(80.dp), color = Color.White, fontSize = 11.sp)
                Text(
                    if (invoiceItem.qty % 1 == 0.0) invoiceItem.qty.toInt().toString() else String.format("%.3f", invoiceItem.qty),
                    modifier = Modifier.width(60.dp),
                    color = Color.White,
                    fontSize = 11.sp
                )
                Text(invoiceItem.alreadyBilledQty.toString(), modifier = Modifier.width(60.dp), color = Color.White, fontSize = 11.sp)
                Text(invoiceItem.returnQty.toString(), modifier = Modifier.width(60.dp), color = Color.White, fontSize = 11.sp)
                Text(invoiceItem.reservedQty.toString(), modifier = Modifier.width(80.dp), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

data class StockPageResult(
    val items: List<StockItem>,
    val isLastPage: Boolean,
    val totalElements: Int = 0
)

private suspend fun fetchStockItems(token: String, page: Int, size: Int, searchQuery: String = ""): StockPageResult {
    return try {
        val baseUrl = "${ApiConfig.BASE_URL_8080}/stock/getAllNestedStockItems?page=$page&size=$size"
        val url = if (searchQuery.isNotEmpty()) {
            "$baseUrl&search=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
        } else {
            baseUrl
        }

        android.util.Log.d("StockScreen", "Fetching: $url")

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val response = gson.fromJson(content, StockItemsResponse::class.java)

            // Determine if this is the last page
            val isLast = response.last ?: (response.stockItemList.size < size)

            android.util.Log.d("StockScreen", "Received ${response.stockItemList.size} items, total: ${response.totalElements}, isLast: $isLast")

            StockPageResult(
                items = response.stockItemList,
                isLastPage = isLast,
                totalElements = response.totalElements ?: 0
            )
        } else {
            StockPageResult(emptyList(), true, 0)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        StockPageResult(emptyList(), true)
    }
}
