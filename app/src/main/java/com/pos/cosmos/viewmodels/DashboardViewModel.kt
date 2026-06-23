package com.pos.cosmos.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pos.cosmos.api.RetrofitClient
import com.pos.cosmos.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val _salesOverview = MutableStateFlow(SalesOverview(0.0, 0, 0.0, 0.0))
    val salesOverview: StateFlow<SalesOverview> = _salesOverview.asStateFlow()

    private val _purchaseOverview = MutableStateFlow(PurchaseOverview(0.0, 0, 0.0, 0))
    val purchaseOverview: StateFlow<PurchaseOverview> = _purchaseOverview.asStateFlow()

    private val _stockInfo = MutableStateFlow(StockInfo(0, 0, 0, 0))
    val stockInfo: StateFlow<StockInfo> = _stockInfo.asStateFlow()

    private val _salesStatistics = MutableStateFlow<List<SalesStatPoint>>(emptyList())
    val salesStatistics: StateFlow<List<SalesStatPoint>> = _salesStatistics.asStateFlow()

    private val _topSellingItems = MutableStateFlow<List<TopSellingItem>>(emptyList())
    val topSellingItems: StateFlow<List<TopSellingItem>> = _topSellingItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadDashboardData(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                loadAccountSummary(token)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to mock data on error
                loadMockData()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadAccountSummary(token: String) {
        try {
            val response = RetrofitClient.apiService.getAccountSummary("${com.pos.cosmos.api.ApiConfig.BASE_URL_8080}/account/summary", "Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    // Parse credit and cash bills count
                    val creditBillsCount = data.get("creditBillsCount")?.asInt ?: 0
                    val cashBillsCount = data.get("cashBillsCount")?.asInt ?: 0
                    val customerCount = data.get("customerCount")?.asInt ?: 0
                    val todayInvoiceCount = data.get("todayInvoiceCount")?.asInt ?: 0

                    // Parse sales overview
                    _salesOverview.value = SalesOverview(
                        totalSales = 0.0,
                        totalOrders = creditBillsCount + cashBillsCount,
                        totalIncome = 0.0,
                        totalDiscount = 0.0,
                        customerCount = customerCount,
                        todayInvoiceCount = todayInvoiceCount,
                        creditBillsCount = creditBillsCount,
                        cashBillsCount = cashBillsCount
                    )

                    // Parse purchase overview
                    val totalPurchaseCount = data.get("totalPurchaseCount")?.asInt ?: 0
                    val todayPurchaseCount = data.get("todayPurchaseCount")?.asInt ?: 0
                    val suppliersCount = data.get("suppliersCount")?.asInt ?: 0
                    val ownProductsCount = data.get("ownProductsCount")?.asInt ?: 0
                    val customerReturnsCount = data.get("customerReturnsCount")?.asInt ?: 0
                    val supplierReturnsCount = data.get("supplierReturnsCount")?.asInt ?: 0

                    _purchaseOverview.value = PurchaseOverview(
                        totalPurchases = totalPurchaseCount.toDouble(),
                        todayPurchases = todayPurchaseCount,
                        totalAmount = 0.0,
                        totalReturns = 0,
                        suppliersCount = suppliersCount,
                        ownProductsCount = ownProductsCount,
                        customerReturnsCount = customerReturnsCount,
                        supplierReturnsCount = supplierReturnsCount
                    )

                    // Parse stock info
                    val lowStockItemsCount = data.get("lowStockItemsCount")?.asInt ?: 0
                    val normalStockItemsCount = data.get("normalStockItemsCount")?.asInt ?: 0

                    _stockInfo.value = StockInfo(
                        currentStock = normalStockItemsCount,
                        maxStock = normalStockItemsCount + lowStockItemsCount,
                        lowStockCount = lowStockItemsCount,
                        outOfStockCount = 0
                    )

                    // Parse sales statistics from salesStatisticsItems
                    data.getAsJsonArray("salesStatisticsItems")?.let { statsArray ->
                        val stats = mutableListOf<SalesStatPoint>()

                        if (statsArray.size() > 0) {
                            // Get the first item's daily sales for the chart
                            val firstItem = statsArray.get(0).asJsonObject
                            val dailySales = firstItem.getAsJsonArray("dailySales")

                            dailySales?.forEach { dayData ->
                                val obj = dayData.asJsonObject
                                val date = obj.get("date")?.asString ?: ""
                                val value = obj.get("value")?.asDouble ?: 0.0
                                stats.add(SalesStatPoint(date, value))
                            }
                        }

                        _salesStatistics.value = stats
                    }

                    // Parse top selling items
                    data.getAsJsonArray("topSellingItems")?.let { itemsArray ->
                        val colors = listOf(
                            0xFF6BCB77L, 0xFF4D96FFL, 0xFFFFD93DL,
                            0xFFFF6B9DL, 0xFFC780FAL, 0xFF00D4FFL
                        )

                        val items = mutableListOf<TopSellingItem>()

                        // Calculate total sales for percentage
                        var totalSales = 0
                        itemsArray.forEach { element ->
                            val obj = element.asJsonObject
                            totalSales += obj.get("totalSales")?.asInt ?: 0
                        }

                        // Create items with calculated percentages
                        itemsArray.forEachIndexed { index, element ->
                            val obj = element.asJsonObject
                            val quantity = obj.get("totalSales")?.asInt ?: 0
                            val percentage = if (totalSales > 0) {
                                (quantity.toFloat() / totalSales.toFloat()) * 100f
                            } else {
                                0f
                            }

                            items.add(
                                TopSellingItem(
                                    itemName = obj.get("itemName")?.asString ?: "",
                                    quantity = quantity,
                                    percentage = percentage,
                                    color = colors[index % colors.size]
                                )
                            )
                        }

                        _topSellingItems.value = items
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun loadDashboardOverview(token: String) {
        try {
            val response = RetrofitClient.apiService.getDashboardOverview("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    // Parse sales overview
                    data.getAsJsonObject("salesOverview")?.let { sales ->
                        _salesOverview.value = SalesOverview(
                            totalSales = sales.get("totalSales")?.asDouble ?: 0.0,
                            totalOrders = sales.get("totalOrders")?.asInt ?: 0,
                            totalIncome = sales.get("totalIncome")?.asDouble ?: 0.0,
                            totalDiscount = sales.get("totalDiscount")?.asDouble ?: 0.0
                        )
                    }

                    // Parse purchase overview
                    data.getAsJsonObject("purchaseOverview")?.let { purchase ->
                        _purchaseOverview.value = PurchaseOverview(
                            totalPurchases = purchase.get("totalPurchases")?.asDouble ?: 0.0,
                            todayPurchases = purchase.get("todayPurchases")?.asInt ?: 0,
                            totalAmount = purchase.get("totalAmount")?.asDouble ?: 0.0,
                            totalReturns = purchase.get("totalReturns")?.asInt ?: 0
                        )
                    }

                    // Parse stock info
                    data.getAsJsonObject("stockInfo")?.let { stock ->
                        _stockInfo.value = StockInfo(
                            currentStock = stock.get("currentStock")?.asInt ?: 0,
                            maxStock = stock.get("maxStock")?.asInt ?: 0,
                            lowStockCount = stock.get("lowStockCount")?.asInt ?: 0,
                            outOfStockCount = stock.get("outOfStockCount")?.asInt ?: 0
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadSalesStatistics(token: String) {
        try {
            val response = RetrofitClient.apiService.getSalesStatistics("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    val stats = data.map { item ->
                        val obj = item.asJsonObject
                        SalesStatPoint(
                            date = obj.get("date")?.asString ?: "",
                            amount = obj.get("amount")?.asDouble ?: 0.0
                        )
                    }
                    _salesStatistics.value = stats
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun loadTopSellingItems(token: String) {
        try {
            val response = RetrofitClient.apiService.getTopSellingItems("Bearer $token")
            if (response.isSuccessful) {
                response.body()?.let { data ->
                    val colors = listOf(
                        0xFF6BCB77L, 0xFF4D96FFL, 0xFFFFD93DL,
                        0xFFFF6B9DL, 0xFFC780FAL, 0xFF00D4FFL
                    )
                    val items = data.mapIndexed { index, item ->
                        val obj = item.asJsonObject
                        TopSellingItem(
                            itemName = obj.get("itemName")?.asString ?: "",
                            quantity = obj.get("quantity")?.asInt ?: 0,
                            percentage = obj.get("percentage")?.asFloat ?: 0f,
                            color = colors[index % colors.size]
                        )
                    }
                    _topSellingItems.value = items
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadMockData() {
        _salesOverview.value = SalesOverview(
            totalSales = 12456.0,
            totalOrders = 46,
            totalIncome = 11578.0,
            totalDiscount = 880.0,
            customerCount = 125,
            todayInvoiceCount = 12,
            creditBillsCount = 28,
            cashBillsCount = 18
        )

        _purchaseOverview.value = PurchaseOverview(
            totalPurchases = 8248.0,
            todayPurchases = 5,
            totalAmount = 828.0,
            totalReturns = 8,
            suppliersCount = 45,
            ownProductsCount = 230,
            customerReturnsCount = 3,
            supplierReturnsCount = 2
        )

        _stockInfo.value = StockInfo(
            currentStock = 15200,
            maxStock = 20000,
            lowStockCount = 12,
            outOfStockCount = 3
        )

        _salesStatistics.value = listOf(
            SalesStatPoint("Apr 19", 2379.0),
            SalesStatPoint("May 19", 3086.0),
            SalesStatPoint("Jun 19", 2979.0),
            SalesStatPoint("Jul 19", 2739.0),
            SalesStatPoint("Aug 19", 2893.0),
            SalesStatPoint("Sep 19", 4480.0),
            SalesStatPoint("Oct 19", 3879.0),
            SalesStatPoint("Nov 19", 4180.0),
            SalesStatPoint("Dec 19", 4779.0),
            SalesStatPoint("Jan 20", 5286.0),
            SalesStatPoint("Feb 20", 4886.0)
        )

        _topSellingItems.value = listOf(
            TopSellingItem("DIARY / FOODS", 156, 37f, 0xFF6BCB77L),
            TopSellingItem("BREAD / BAKERY", 89, 18f, 0xFF4D96FFL),
            TopSellingItem("VEGETABLES", 67, 21f, 0xFFFFD93DL),
            TopSellingItem("SNACKS / FOODS", 45, 14f, 0xFFFF6B9DL),
            TopSellingItem("VEGETABLES FRUITS", 34, 10f, 0xFFC780FAL)
        )
    }
}
