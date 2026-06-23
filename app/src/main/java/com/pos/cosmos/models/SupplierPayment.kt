package com.pos.cosmos.models

data class SupplierPayment(
    val id: Int = 0,
    val supplierId: Int = 0,
    val invoiceId: Int = 0,
    val paidAmount: Double = 0.0,
    val type: String = "",
    val chequeNumber: String? = null,
    val chequeDate: String? = null,
    val bank: String = "",
    val trn: String = "",
    val paymentDate: String = "",
    val active: String = "Y"
)

data class SupplierInvoice(
    val id: Int = 0,
    val invoiceName: String = ""
)

data class SupplierInvoiceDetail(
    val qty: Double = 0.0,
    val unitCost: Double = 0.0
)

data class SupplierInvoiceFull(
    val id: Int = 0,
    val invoiceName: String = "",
    val invoiceDetails: List<SupplierInvoiceDetail> = emptyList()
)
