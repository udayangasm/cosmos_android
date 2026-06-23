package com.pos.cosmos.models

data class DashboardData(
    val salesOverview: SalesOverview,
    val purchaseOverview: PurchaseOverview,
    val stockInfo: StockInfo,
    val salesStatistics: List<SalesStatPoint>,
    val topSellingItems: List<TopSellingItem>
)

data class SalesOverview(
    val totalSales: Double,
    val totalOrders: Int,
    val totalIncome: Double,
    val totalDiscount: Double,
    val customerCount: Int = 0,
    val todayInvoiceCount: Int = 0,
    val creditBillsCount: Int = 0,
    val cashBillsCount: Int = 0
)

data class PurchaseOverview(
    val totalPurchases: Double,
    val todayPurchases: Int,
    val totalAmount: Double,
    val totalReturns: Int,
    val suppliersCount: Int = 0,
    val ownProductsCount: Int = 0,
    val customerReturnsCount: Int = 0,
    val supplierReturnsCount: Int = 0
)

data class StockInfo(
    val currentStock: Int,
    val maxStock: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int
)

data class SalesStatPoint(
    val date: String,
    val amount: Double
)

data class TopSellingItem(
    val itemName: String,
    val quantity: Int,
    val percentage: Float,
    val color: Long
)
