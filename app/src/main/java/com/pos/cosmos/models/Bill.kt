package com.pos.cosmos.models

import java.util.Date

// Main Bill data class
data class Bill(
    val id: Long = 0,
    val billNo: Long = 0,
    val billingDate: String? = "",
    val customerId: Long = 0,
    val cashCustomerName: String? = "",
    val customerName: String? = "",
    val salesRefId: Long = 0,
    val salesMan: String? = "N/A",
    val billType: String = "CASH",
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val cash: Double = 0.0,
    val companyCode: Int = 0,
    val billItems: List<BillItemDetail> = emptyList(),
    val kots: List<KitchenOrder> = emptyList()
)

// Bill item detail
data class BillItemDetail(
    val id: Long = 0,
    val itemId: Long = 0,
    val unitPrice: Double = 0.0,
    val qty: Double = 0.0,
    val returnQty: Double = 0.0,
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val freeIssueQty: Double = 0.0
)

// Kitchen Order
data class KitchenOrder(
    val id: Long = 0,
    val kotNo: String? = "",
    val kotDate: String? = "",
    val orderType: String? = "TAKEAWAY",
    val status: String? = "PENDING",
    val tableNo: String? = "",
    val subTotal: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val grandTotal: Double = 0.0,
    val notes: String? = "",
    val items: List<KotItem> = emptyList()
)

// KOT Item
data class KotItem(
    val id: Long = 0,
    val itemName: String? = "",
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val amount: Double = 0.0,
    val notes: String? = ""
)

// Filter state
data class BillFilterState(
    val billType: BillType = BillType.ALL,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val selectedCustomerId: Long? = null,
    val selectedCustomerName: String? = null,
    val selectedSalesRepId: Long? = null,
    val selectedSalesRepName: String? = null,
    val companyCode: Int = 0
)

// Bill type enum
enum class BillType(val displayName: String, val apiValue: String) {
    ALL("All Bills", "All"),
    CASH("Cash Bills", "CASH"),
    CREDIT("Credit Bills", "CREDIT")
}

// Paginated response
data class BillsResponse(
    val bills: List<Bill> = emptyList(),
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val pageSize: Int = 50
)
