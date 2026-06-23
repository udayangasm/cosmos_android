package com.pos.cosmos.models

data class MobileUiConfig(
    val id: Int = 0,
    val companyCode: Int = 0,
    // All mobile settings default to "Y" (enabled)
    val showCashBillInMobile: String = "Y",
    val showCreditBillInMobile: String = "Y",
    val showKOTInMobile: String = "Y",
    val showAllKOTsInMobile: String = "Y",
    val showAllItemsInMobile: String = "Y",
    val showStockInMobile: String = "Y",
    val showOrderFormInMobile: String = "Y",
    val showAllOrdersInMobile: String = "Y",
    val showPurchasingInMobile: String = "Y",
    val showProfitInMobile: String = "Y",
    val showCustomerOutstandingInMobile: String = "Y",
    val showCustomerPaymentsInMobile: String = "Y",
    val showAllBillsInMobile: String = "Y",
    val showPrinterInMobile: String = "Y",
    val showSuppliersInMobile: String = "Y",
    val showCustomersInMobile: String = "Y",
    val showStockAdjustInMobile: String = "Y",
    val showImageBillInMobile: String = "N",
    val showReturnsInMobile: String = "N",
    val showItemReturnsInMobile: String = "N",
    val showSettingsInMobile: String = "N",
    val showConfigInMobile: String = "N",
    val showSyncDataInMobile: String = "N",
    val showSupplierPaymentsInMobile: String = "N",
    val showExpensesInMobile: String = "N",
    val showChequesInMobile: String = "N"
) {
    /**
     * Check if a specific mobile UI feature is enabled
     * @param key The config key (e.g., "showCashBillInMobile")
     * @return true if enabled (value is "Y"), false otherwise
     */
    fun isEnabled(key: String): Boolean {
        return when (key) {
            "showCashBillInMobile" -> showCashBillInMobile == "Y"
            "showCreditBillInMobile" -> showCreditBillInMobile == "Y"
            "showKOTInMobile" -> showKOTInMobile == "Y"
            "showAllKOTsInMobile" -> showAllKOTsInMobile == "Y"
            "showAllItemsInMobile" -> showAllItemsInMobile == "Y"
            "showStockInMobile" -> showStockInMobile == "Y"
            "showOrderFormInMobile" -> showOrderFormInMobile == "Y"
            "showAllOrdersInMobile" -> showAllOrdersInMobile == "Y"
            "showPurchasingInMobile" -> showPurchasingInMobile == "Y"
            "showProfitInMobile" -> showProfitInMobile == "Y"
            "showCustomerOutstandingInMobile" -> showCustomerOutstandingInMobile == "Y"
            "showCustomerPaymentsInMobile" -> showCustomerPaymentsInMobile == "Y"
            "showAllBillsInMobile" -> showAllBillsInMobile == "Y"
            "showPrinterInMobile" -> showPrinterInMobile == "Y"
            "showSuppliersInMobile" -> showSuppliersInMobile == "Y"
            "showCustomersInMobile" -> showCustomersInMobile == "Y"
            "showStockAdjustInMobile" -> showStockAdjustInMobile == "Y"
            "showImageBillInMobile" -> showImageBillInMobile == "Y"
            "showReturnsInMobile" -> showReturnsInMobile == "Y"
            "showItemReturnsInMobile" -> showItemReturnsInMobile == "Y"
            "showSettingsInMobile" -> showSettingsInMobile == "Y"
            "showConfigInMobile" -> showConfigInMobile == "Y"
            "showSyncDataInMobile" -> showSyncDataInMobile == "Y"
            "showSupplierPaymentsInMobile" -> showSupplierPaymentsInMobile == "Y"
            "showExpensesInMobile" -> showExpensesInMobile == "Y"
            "showChequesInMobile" -> showChequesInMobile == "Y"
            else -> true // Unknown keys are enabled by default
        }
    }
}
