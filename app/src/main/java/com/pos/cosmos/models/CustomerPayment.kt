package com.pos.cosmos.models

data class CustomerPayment(
    val id: Long = 0,
    val customerId: Long = 0,
    val billId: Long = 0,
    val paidAmount: Double = 0.0,
    val type: String = "",
    val chequeNumber: String = "",
    val chequeDate: String? = null,
    val bankAndBranch: String = "",
    val accountNumber: String = "",
    val paymentDate: String = "",
    val active: String = "Y",
    val salesRepId: Long = 0,
    val trn: String = "",
    val cashTRN: String = "",
    val chequeStatus: String = "pending",
    val customerName: String = "",
    val paymentDateStr: String = "",
    val chequeDateStr: String = ""
)

data class BillItemData(
    val id: Int = 0,
    val qty: Double = 0.0,
    val unitPrice: Double = 0.0,
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val returnQty: Double = 0.0,
    val itemId: Int = 0
)

data class BillWithDetails(
    val id: Long = 0,
    val billNo: Long = 0,
    val customerId: Long = 0,
    val billingDate: String? = null,
    val billItems: List<BillItemData> = emptyList(),
    val cash: Double = 0.0,
    val billType: String = "",
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val discountPercentage: Double = 0.0,
    val salesRefId: Long = 0
)

data class CustomerCreditSummary(
    val totalOutstanding: Double = 0.0,
    val customerCredit: Double = 0.0
)
