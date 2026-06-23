package com.pos.cosmos.models

data class Config(
    val hideSalesPersonInCashBill: String? = null,
    val showCardPaymentInCashBill: String? = null,
    val maxDiscountPercentage: String? = null,
    val sendSmsToCustomers: String? = null,
    val sendSmsToSalesRep: String? = null,
    val showInvoiceDate: String? = null,
    val printLargeBillWhenUpdateBill: String? = null,
    val printSmallBillWhenUpdateBill: String? = null,
    val editSellingPriceWhenBilling: String? = null,
    val editSellingPriceUpwardWhenBilling: String? = null,
    val showSellingPriceInImageBill: String? = null,
    val showPrice2InPurchasing: String? = null,
    val showPrice3InPurchasing: String? = null,
    val blockBillingIfUnitCostIsHigherThanSellingPrice: String? = null,
    val allowBillingStockQtyExceeded: String? = null,
    val allowSameItemToAddSameBill: String? = null,
    val showManualBillNumberInCreditBills: String? = null,
    val showPaymentDialogInCreditBills: String? = null,
    val allowCashBillsInCreditBillPage: String? = null,
    val createNewCustomerWhenBillingIfNotExists: String? = null,
    val enableMultiplePaymentSameTimeInBilling: String? = null,
    // WhatsApp and SMS notification configs
    val sendWhatsAppAfterCreateOrder: String? = null,
    val sendSmsAfterCreateOrder: String? = null,
    val companyWhatsAppNumber: String? = null,
    // Order form settings
    val useSmallBillTemplateForOrderForm: String? = null,
    val printReceiptWhenCreatingOrder: String? = null,
    val showFreeIssueInOrderForm: String? = null,
    val enablePromotions: String? = null,
    val showCustomerAddressInOrderForm: String? = null,
    val showPaymentOptionsInOrderForm: String? = null,
    val doNotCheckStockQtyWhenMakeOrders: String? = null,
    val showMemoInOrderFormAndCreditBill: String? = null,
    val showDeliveryDateInOrderForm: String? = null,
    val customerTelephoneAndWhatsappMandatory: String? = null,
    val use58mmBillTemplateForAndroidApp: String? = null,
    val showCustomerNameInCashBill: String? = null,
    val showWhatsAppNoInCashBills: String? = null,
    val showReturnQtyItemWiseInImageBillsAndroidApp: String? = null,
    val sendReturnItemFromImageBillsToDamagesAndroidApp: String? = null,
    val print2ReceiptsWhenPrintBillAndroidApp: String? = null,
    val print2ReceiptsOnlyForCreditBillsAndroidApp: String? = null,
    val showReceiptPrintPreviewAndroidApp: String? = null,
    val enableShareAndroidApp: String? = null,
    val itemCodeAutoGenerateInPurchasing: String? = null,
    val showBarcode: String? = null,
    val setDefaultDiscountForPrice: String? = null,
    val showLineDiscountInSmallBills: String? = null,
    // When "N", credit bill uses the per-purchase-invoice selling price; otherwise
    // (checked/"Y" or unset) it uses the item's last-updated (master) selling price,
    // matching the web credit-bill behaviour.
    val useLastUpdatedSellingPrice: String? = null
)

// Notification result from order creation
data class NotificationResult(
    val sent: Boolean = false,
    val channel: String? = null,  // "WHATSAPP", "SMS", "NONE"
    val message: String? = null,
    val recipient: String? = null
)

// Order response with notification result
data class OrderResponse(
    val order: Any? = null,
    val notificationResult: NotificationResult? = null
)
