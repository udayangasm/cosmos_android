package com.pos.cosmos.models

data class UserPermission(
    val id: Long = 0,
    val userId: Long = 0,
    val profit: String = "N",
    val addCustomers: String = "N",
    val addItems: String = "N",
    val addSuppliers: String = "N",
    val billSettings: String = "N",
    val customerPayments: String = "N",
    val deleteCustomers: String = "N",
    val deleteItems: String = "N",
    val deleteSuppliers: String = "N",
    val supplierPayments: String = "N",
    val updateUserPermissions: String = "N",
    val userEvents: String = "N",
    val viewUserPermissions: String = "N",
    val viewSmallBillSettings: String = "N",
    val viewLargeBillSettings: String = "N",
    val viewAllItems: String = "N",
    val viewDashboard: String = "N",
    val viewTotalOutStanding: String = "N",
    val viewIndividualCustomerOutStanding: String = "N",
    val viewSupplierRegistration: String = "N",
    val viewCreditNote: String = "N",
    val viewCashBill: String = "N",
    val viewSalesRepRegistration: String = "N",
    val viewCreditBill: String = "N",
    val viewImageBill: String = "N",
    val viewOrderForm: String = "N",
    val viewAllBills: String = "N",
    val viewCustomerItemReturns: String = "N",
    val viewAllOrders: String = "N",
    val viewExpenses: String = "N",
    val viewSupplierItemReturns: String = "N",
    val viewStock: String = "N",
    val viewCheque: String = "N",
    val viewPurchasing: String = "N",
    val viewSalesRepCollection: String = "N",
    val viewAcceptPayments: String = "N",
    val acceptPayments: String = "N",
    val updateCreditCustomerInvoice: String = "N",
    val updatePurchaseInvoice: String = "N",
    val deletePurchaseInvoice: String = "N",
    val viewConfigs: String = "N",
    val updateItems: String = "N",
    val updateStock: String = "N",
    val updateOrders: String = "N",
    val viewCashBook: String = "N",
    val updateReOrderQty: String = "N",
    val allowBlockCustomerBilling: String = "N",
    val allowReadyStateKotCancel: String = "N",
    val initialPage: String = "home",
    val viewHomePage: String = "N",
    val viewItemMovement: String = "N",
    val viewStockMovement: String = "N",
    val showCashBookSummary: String = "N",
    val showDailySalesSettlements: String = "N",
    val viewAllServices: String = "N",
    val editServices: String = "N",
    val deleteServices: String = "N",
    val viewKOT: String = "N"
) {
    // Helper functions to check permissions
    fun hasPermission(permission: String): Boolean {
        return when (permission) {
            "profit" -> profit == "Y"
            "viewCashBill" -> viewCashBill == "Y"
            "viewCreditBill" -> viewCreditBill == "Y"
            "viewOrderForm" -> viewOrderForm == "Y"
            "viewAllOrders" -> viewAllOrders == "Y"
            "viewPurchasing" -> viewPurchasing == "Y"
            "customerPayments" -> customerPayments == "Y"
            "supplierPayments" -> supplierPayments == "Y"
            "viewExpenses" -> viewExpenses == "Y"
            "viewDashboard" -> viewDashboard == "Y"
            "viewHomePage" -> viewHomePage == "Y"
            "viewStock" -> viewStock == "Y"
            "viewAllItems" -> viewAllItems == "Y"
            "viewAllBills" -> viewAllBills == "Y"
            "viewTotalOutStanding" -> viewTotalOutStanding == "Y"
            "viewIndividualCustomerOutStanding" -> viewIndividualCustomerOutStanding == "Y"
            "addCustomers" -> addCustomers == "Y"
            "viewSupplierRegistration" -> viewSupplierRegistration == "Y"
            "viewKOT" -> viewKOT == "Y"
            "updateStock" -> updateStock == "Y"
            "viewConfigs" -> viewConfigs == "Y"
            "viewCheque" -> viewCheque == "Y"
            else -> false
        }
    }
}
