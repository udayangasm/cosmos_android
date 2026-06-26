package com.pos.cosmos.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.models.Config
import com.pos.cosmos.sync.SyncWorker
import com.pos.cosmos.utils.AppSettings
import com.pos.cosmos.utils.SessionManager
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ─── Category definitions (mirrors desktop ConfigsForm.cs) ──────────────────
private val CONFIG_CATEGORIES: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Common Settings" to listOf(
        "useLastUpdatedSellingPrice" to "Use last updated selling price",
        "hideCategoryDropDown" to "Hide Category Drop Down",
        "showExpireDateAndWarningDays" to "Show expire date and warning days",
        "searchItemsWithoutOrder" to "Search items in any order",
        "showCustomerCodeInCustomerRegistration" to "Show Customer Code in Customer Registration",
        "showItemCodeInSmallBill" to "Show Item Code in the Small Bill",
    ),
    "Purchasing Settings" to listOf(
        "showBarcode" to "Show Barcode in purchasing",
        "showBoxItemsInPurchasing" to "Show Box/Item in purchasing",
        "useEnterButtonForExpireDateInPurchasing" to "Use Enter Button For Expire Date in Purchasing",
        "showPrice2InPurchasing" to "Show price 2 in purchasing",
        "showPrice3InPurchasing" to "Show price 3 in purchasing",
        "showInvoiceDateInPurchasing" to "Show invoice date in purchasing",
        "showFreeIssueItems" to "Show Free Issue Items in Purchasing",
        "showMaxDiscountInPurchasing" to "Show Max Discount In Purchasing",
        "itemCodeAutoGenerateInPurchasing" to "Item Code Auto Generate In Purchasing",
        "showDiscount1InPurchasing" to "Show Discount 1 in Purchasing",
        "showDiscount2InPurchasing" to "Show Discount 2 in Purchasing",
    ),
    "Cash Bill & Credit Bill Settings" to listOf(
        "printLargeBillWhenUpdateBill" to "Print large bill when update credit bill",
        "printSmallBillWhenUpdateBill" to "Print small bill when update bill",
        "editSellingPriceWhenBilling" to "Edit selling price when billing",
        "editSellingPriceUpwardWhenBilling" to "Edit selling price upward when billing",
        "blockBillingIfUnitCostIsHigherThanSellingPrice" to "Block billing if unit cost is higher than selling price",
        "useBillTemplate1ForLargeBills" to "Use bill template 1 for large bills",
        "useBillTemplate2ForLargeBills" to "Use Bill Template 2 for large bills",
        "showPrintSmallBill" to "Show print small bill",
        "showPrintLargeBill" to "Show print large bill",
        "hideSalesPersonInCashBill" to "Hide sales person from cash bill",
        "allowBillingStockQtyExceeded" to "Allow Billing when Stock Qty exceeded",
        "useSinhalaBillTemplateForSmallBills" to "Use Sinhala Bill Template for small bills",
        "showManualBillNumberInCreditBills" to "Show Manual Bill Number in Credit Bills",
        "showStockQtyInBills" to "Show Stock Qty in Bills",
        "showMaxDiscountInBilling" to "Show Max Discount In Billing",
        "showInvoiceDate" to "Show invoice date in billing",
        "showUnitCostInBilling" to "Show unit cost in billing",
        "showInvoiceDateInBilling" to "Show invoice date when item selecting in billing",
        "showCardPaymentInCashBill" to "Show card payments in cash bill",
        "showAddNewBillButton" to "Show Add New Bill Button in Bills",
        "printCashierInSmallBills" to "Print Cashier In Small Bill",
        "allowSameItemToAddSameBill" to "Allow Adding Same Item to Same Bill",
        "createNewCustomerWhenBillingIfNotExists" to "Create Customer if Not exists when Billing",
        "showCustomerNameInCashBill" to "Show Customer Name in Cash Bills",
        "showPaymentDialogInCreditBills" to "Show Payment Dialog in Credit Bills",
        "allowCashBillsInCreditBillPage" to "Allow Cash Bills in Credit Bill Page",
        "doNotShowWarningMessageWhenStockQtyExceeded" to "Do not Show Warning Message when Stock Qty exceeded",
        "createNewItemWhenBilling" to "Create New Item When Billing",
        "hideMultipleItemsPopupInBilling" to "Hide Multiple Items Popup in Billing",
        "showItemNamePriceStockQtyInBilling" to "Show Item Name - Price - Stock Qty in Billing",
        "showDiscount1InBilling" to "Show Discount 1 in Billing",
        "showDiscount2InBilling" to "Show Discount 2 in Billing",
        "useDiscount1Default" to "Use Discount 1 Default",
        "useQuotationBillTemplateForLargeBills" to "Use Quotation Bill Template for Large Bills",
        "useOnlineFashionTemplateForOnlineOrders" to "Use Online Fashion Template for online orders",
        "allowBlockCustomerBilling" to "Allow Billing for blocked customers",
        "showReturnInvoicePrintInAllBills" to "Show Return Invoice Print in All Bills",
        "showItemWiseDiscountPercentageInLargeBills" to "Show Item wise Discount Percentage in Large Bills",
        "showLineDiscountInSmallBills" to "Show Line Discount in Small Bills",
        "showCreditBillCheckBoxInCashBills" to "Show Credit Bill Customer in Cash Bills",
        "doNotShowItemCodeInBilling" to "Do not show Item Code in Billing",
        "addItemOnBarcodeScan" to "Add Item to Billing Table Once Barcode Scan",
        "showReturnDateInBills" to "Show Return Date in Credit Bills",
        "showDeliveryDateInBills" to "Show Delivery Date in Credit Bills",
        "showFitOnDateInBills" to "Show Fit on Date in Credit Bills",
        "showExtraBillingItemsInCreditBills" to "Adding extra billing items for credit bills",
        "showWhatsAppNoInCashBills" to "Show WhatsApp Number in Billing",
        "useBillTemplate3ForLargeBills" to "Use Large Bill Template 3 for large bills",
    ),
    "Stock Form Settings" to listOf(
        "showInvoiceDateInStock" to "Show Invoice Date in stock",
        "showReOrderQtyInStock" to "Show re-order qty in stock",
        "showPrice2InStock" to "Show price 2 in stock",
        "showPrice3InStock" to "Show price 3 in stock",
        "doNotManageStockWhenBilling" to "Do Not Manage Stock When Billing",
        "showOnlyNonZeroQtyInStock" to "Show only non zero quantity records in stock page",
        "showDiscount1InStock" to "Show Discount 1 in Stock",
        "showDiscount2InStock" to "Show Discount 2 in Stock",
    ),
    "Order Form Settings" to listOf(
        "showMultipleCustomersInOrderForm" to "Add multiple customers in order form",
        "showMultiplePricesInOrderForm" to "Show multiple prices in order form",
        "useLargeBillTemplateForOrderFormWebApp" to "Use Large Bill Template For Order Form (Web App)",
        "useLargeBillTemplateForOrderFormAndroidApp" to "Use Large Bill Template For Order Form (Android App)",
        "useLargeBillTemplateForOrderFormWhatsApp" to "Use Large Bill Template For Order Form (WhatsApp PDF)",
        "printReceiptWhenCreatingOrder" to "Print Receipt When Creating Order",
        "showFreeIssueInOrderForm" to "Show Free Issue in Order Form",
        "showCustomerAddressInOrderForm" to "Show Customer Address in Order Form",
        "doNotCheckStockQtyWhenMakeOrders" to "Do not Check Stock Qty when Make Orders",
        "doNotReserveStockItemsWhenMakeOrders" to "Do not Reserve Stock Items When Make Orders",
        "useSalesOrderTemplateWhenPrintingOrders" to "Use Sales Order Template when printing orders",
        "showPaymentOptionsInOrderForm" to "Show Payment Options in Order Form",
        "printOrderWhenCreating" to "Print Order When Creating",
        "showMemoInOrderFormAndCreditBill" to "Show Memo in Order Form and Credit Bill",
        "enableReturnItemsWithOrders" to "Enable return items along with orders",
        "showReturnsSeparatelyInBills" to "Show returns separately in bills",
    ),
    "SMS & WhatsApp Settings" to listOf(
        "sendSmsWhenBilling" to "Send SMS when billing",
        "sendSmsToCustomers" to "Send SMS to customers",
        "sendSmsToSalesRep" to "Send SMS to sales rep",
        "sendSmsToOffice" to "Send SMS to office",
        "sendOnly100SmsPerMonth" to "Send only 100 SMS per month",
        "sendSmsAfterCreateOrder" to "Send SMS to Customer After Create Order",
        "sendWhatsAppAfterCreateOrder" to "Send WhatsApp Message to Customer After Create Order (PDF)",
        "sendWhatsAppToSalesRepAfterCreateOrder" to "Send WhatsApp Message to Sales Rep After Create Order (PDF)",
        "sendWhatsAppAfterCreateBill" to "Send WhatsApp Message to Customer After Create Bill (PDF)",
        "sendWhatsAppToSalesRepAfterCreateBill" to "Send WhatsApp Message to Sales Rep After Create Bill (PDF)",
    ),
    "KOT Settings" to listOf(
        "showDiscountValueInKot" to "Show Discount in KOT",
        "hideRawMaterialsInKot" to "Hide Raw Materials in KOT",
        "printCustomerReceiptAfterKot" to "Print Customer Receipt after KOT",
        "addServiceChargesForBills" to "Add Service Charges For Bills",
    ),
    "Image Bill Settings" to listOf(
        "focusQtyWhenClickOnImageInManualBilling" to "Focus QTY input when click on image in image billing",
        "showSellingPriceInImageBill" to "Show Selling Price in Image Bill",
        "doNotShowImageOfItemsInImageBilling" to "Do Not Show Image of Items in Image Billing",
        "showCategoryInImageBilling" to "Show Category in Image Billing",
        "doNotShowImageOfCategoriesInImageBilling" to "Do Not Show Image of Categories in Image Billing",
        "print3CopiesWhenReceiptPrintingInImageBill" to "Print 3 Copies When Receipt Printing in Image Bill",
    ),
    "Item Form Settings" to listOf(
        "showPrice2InAllItems" to "Show price 2 in all items",
        "showPrice3InAllItems" to "Show price 3 in all items",
        "showWeightScaleNoInItems" to "Show weight scale no in items",
        "showStockUpdateFlagInMasterItems" to "Show Stock Update Flag in Master Items",
        "showDescriptionInMasterItems" to "Show Description in Master Items",
        "showDiscount1InAllItems" to "Show Discount 1 in All Items",
        "showDiscount2InAllItems" to "Show Discount 2 in All Items",
    ),
    "Customer Form Settings" to listOf(
        "customerTelephoneAndWhatsappMandatory" to "Set Customer Telephone and WhatsApp number mandatory",
    ),
    "Service Bill Settings" to listOf(
        "useSalonTemplateForServiceBillWhatsApp" to "Use Salon Template Large Bills for WhatsApp Message",
        "useSalonTemplateForServiceBillReceipt" to "Use Salon Template Large Bills when Printing Receipt",
    ),
)

private val LANGUAGES = listOf("English", "Sinhala", "Tamil", "German", "French")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val scope = rememberCoroutineScope()

    val useLocalDb by settings.useLocalDatabase.collectAsState()
    val autoSync by settings.autoSyncEnabled.collectAsState()
    val intervalMinutes by settings.autoSyncIntervalMinutes.collectAsState()

    var intervalInput by remember(intervalMinutes) { mutableStateOf(intervalMinutes.toString()) }
    var snackbar by remember { mutableStateOf<String?>(null) }

    // Server-backed config state
    var loaded by remember { mutableStateOf<JsonObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    var mobileNumber by remember { mutableStateOf("") }
    var maxDiscount by remember { mutableStateOf("") }
    var whatsApp by remember { mutableStateOf("") }
    var whatsAppKey by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("English") }
    var langExpanded by remember { mutableStateOf(false) }

    val flags = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        val token = SessionManager.getInstance(context).getToken()
        if (token.isNullOrBlank()) {
            loading = false
            snackbar = "Not logged in — server configs unavailable"
            return@LaunchedEffect
        }
        val json = withContext(Dispatchers.IO) { fetchConfigJson(token) }
        if (json != null) {
            loaded = json
            mobileNumber = json.optStr("mobileNumber")
            maxDiscount = json.optStr("maxDiscountPercentage")
            whatsApp = json.optStr("companyWhatsAppNumber")
            whatsAppKey = json.optStr("shopWhatsAppApiKey")
            language = json.optStr("language").ifEmpty { "English" }
            CONFIG_CATEGORIES.flatMap { it.second }.forEach { (prop, _) ->
                flags[prop] = json.optStr(prop).uppercase() == "Y"
            }
        } else {
            snackbar = "Failed to load configs from server"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Local database (desktop "Desktop App Settings")
            SectionCard(title = "Local Database") {
                ToggleRow(
                    label = "Use Local Database",
                    description = "Store data on the device so the app works without internet. " +
                            "Bills, payments and orders created offline will queue for sync.",
                    checked = useLocalDb,
                    onCheckedChange = { settings.setUseLocalDatabase(it) }
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "Enable Auto-Sync",
                    description = "Periodically push offline bills and pull updated master data in the background.",
                    enabled = useLocalDb,
                    checked = autoSync,
                    onCheckedChange = { enabled ->
                        settings.setAutoSyncEnabled(enabled)
                        if (enabled) {
                            SyncWorker.schedule(context, intervalMinutes)
                            snackbar = "Auto-sync scheduled every ${intervalMinutes.coerceAtLeast(15)} min"
                        } else {
                            SyncWorker.cancel(context)
                            snackbar = "Auto-sync disabled"
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = intervalInput,
                    onValueChange = { intervalInput = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("Auto-Sync Interval (minutes)") },
                    supportingText = { Text("Min 15 (Android background limit). Default 5 (foreground only).") },
                    enabled = useLocalDb,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = useLocalDb,
                    onClick = {
                        val mins = intervalInput.toIntOrNull() ?: 5
                        settings.setAutoSyncIntervalMinutes(mins)
                        if (autoSync) SyncWorker.schedule(context, mins)
                        snackbar = "Interval saved"
                    }
                ) { Text("Save Interval") }
            }

            val userPermission by appViewModel.userPermission.collectAsState()
            val hasViewConfigs = userPermission?.hasPermission("viewConfigs") == true

            if (hasViewConfigs && loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (hasViewConfigs) {
                // ── Company info (desktop top-left text inputs)
                SectionCard(title = "Company Info") {
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { mobileNumber = it },
                        label = { Text("Company mobile number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxDiscount,
                        onValueChange = { maxDiscount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Maximum discount percentage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = whatsApp,
                        onValueChange = { whatsApp = it },
                        label = { Text("Company WhatsApp number") },
                        placeholder = { Text("e.g. 94771234567") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = whatsAppKey,
                        onValueChange = { whatsAppKey = it },
                        label = { Text("WhatsApp API Key") },
                        placeholder = { Text("e.g. f647173261101b...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = !langExpanded }
                    ) {
                        OutlinedTextField(
                            value = language,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("System Language") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false }
                        ) {
                            LANGUAGES.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = {
                                        language = lang
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // ── All flag categories (desktop left + right columns)
                CONFIG_CATEGORIES.forEach { (category, items) ->
                    SectionCard(title = category) {
                        items.forEach { (prop, label) ->
                            CheckRow(
                                label = label,
                                checked = flags[prop] ?: false,
                                onCheckedChange = { flags[prop] = it }
                            )
                        }
                    }
                }

                Button(
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        saving = true
                        scope.launch {
                            val token = SessionManager.getInstance(context).getToken()
                            if (token.isNullOrBlank()) {
                                snackbar = "Not logged in"
                                saving = false
                                return@launch
                            }
                            val body = buildConfigJson(
                                base = loaded,
                                mobileNumber = mobileNumber,
                                maxDiscount = maxDiscount,
                                whatsApp = whatsApp,
                                whatsAppKey = whatsAppKey,
                                language = language,
                                flags = flags
                            )
                            val ok = withContext(Dispatchers.IO) { postConfigJson(token, body) }
                            if (ok) {
                                loaded = body
                                // Refresh shared Config in AppViewModel (best effort)
                                runCatching {
                                    val cfg = Gson().fromJson(body, Config::class.java)
                                    appViewModel.setConfig(cfg)
                                }
                                snackbar = "Configs updated successfully!"
                            } else {
                                snackbar = "Failed to update configs"
                            }
                            saving = false
                        }
                    }
                ) {
                    Text(if (saving) "Updating..." else "Update Configs")
                }
            }

            snackbar?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2500)
                    snackbar = null
                }
                Surface(
                    color = Color(0xFF323232),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(it, color = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

// ─── Sub-composables ────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color(0xFF1976D2))
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 12.sp, color = Color(0xFF666666))
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}

// ─── Network / JSON helpers ─────────────────────────────────────────────────

private fun JsonObject.optStr(key: String): String {
    if (!has(key)) return ""
    val el = get(key)
    if (el == null || el.isJsonNull) return ""
    return try { el.asString ?: "" } catch (e: Exception) { el.toString() }
}

private fun buildConfigJson(
    base: JsonObject?,
    mobileNumber: String,
    maxDiscount: String,
    whatsApp: String,
    whatsAppKey: String,
    language: String,
    flags: Map<String, Boolean>
): JsonObject {
    val body = if (base != null) base.deepCopy() else JsonObject()
    body.addProperty("mobileNumber", mobileNumber.trim())
    val disc = maxDiscount.trim().toDoubleOrNull() ?: 100.0
    body.addProperty("maxDiscountPercentage", disc)
    body.addProperty("companyWhatsAppNumber", whatsApp.trim())
    body.addProperty("shopWhatsAppApiKey", whatsAppKey.trim())
    body.addProperty("language", language)
    for ((prop, checked) in flags) {
        if (checked) body.addProperty(prop, "Y") else body.add(prop, com.google.gson.JsonNull.INSTANCE)
    }
    return body
}

private fun fetchConfigJson(token: String): JsonObject? {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/admin/getConfigsByToken")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val parsed = JsonParser.parseString(content)
            if (parsed.isJsonObject) parsed.asJsonObject else null
        } else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun postConfigJson(token: String, body: JsonObject): Boolean {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8090}/admin/updateConfigs")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        code in 200..299
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
