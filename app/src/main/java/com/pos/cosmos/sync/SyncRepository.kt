package com.pos.cosmos.sync

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.local.CosmosDatabase
import com.pos.cosmos.local.entities.BillEntity
import com.pos.cosmos.local.entities.BillLineEntity
import com.pos.cosmos.local.entities.BillTemplateEntity
import com.pos.cosmos.local.entities.BillingConfigEntity
import com.pos.cosmos.local.entities.CategoryEntity
import com.pos.cosmos.local.entities.CompanyEntity
import com.pos.cosmos.local.entities.CustomerEntity
import com.pos.cosmos.local.entities.CustomerPaymentEntity
import com.pos.cosmos.local.entities.ExpenseEntity
import com.pos.cosmos.local.entities.InvoiceEntity
import com.pos.cosmos.local.entities.ItemEntity
import com.pos.cosmos.local.entities.SalesRefEntity
import com.pos.cosmos.local.entities.StockItemEntity
import com.pos.cosmos.local.entities.SupplierEntity
import com.pos.cosmos.local.entities.SupplierPaymentEntity
import com.pos.cosmos.utils.SessionManager
import com.pos.cosmos.utils.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Per-table sync status surfaced to SyncDataScreen — mirrors the desktop
 * SyncDataForm DataGridView columns.
 */
data class TableSyncStatus(
    val table: String,
    val status: String = "Idle",        // Idle | Syncing | Done | Error
    val serverCount: Int = 0,
    val processed: Int = 0,
    val total: Int = 0,
    val syncedRecords: Int = 0,
    val localCount: Int = 0,
    val lastSyncAt: Long = 0L,
    val errorMessage: String? = null
)

data class SyncProgress(
    val isRunning: Boolean = false,
    val phase: String = "",             // e.g. "Pulling Items 120/500"
    val tables: Map<String, TableSyncStatus> = emptyMap(),
    val overallPercent: Int = 0
)

data class ImageSyncProgress(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val message: String = ""
)

/**
 * The 17 logical tables synced by the desktop app, in pull order.
 * Order matters: parents (Companies, Categories) before children (Items, StockItems).
 */
object SyncTables {
    const val COMPANIES = "Companies"
    const val CATEGORIES = "Categories"
    const val ITEMS = "Items"
    const val CUSTOMERS = "Customers"
    const val SUPPLIERS = "Suppliers"
    const val SALES_REFS = "SalesRefs"
    const val INVOICES = "Invoices"
    const val STOCK_ITEMS = "StockItems"
    const val BILLS = "Bills"
    const val CUSTOMER_PAYMENTS = "CustomerPayments"
    const val SUPPLIER_PAYMENTS = "SupplierPayments"
    const val EXPENSES = "Expenses"
    const val BILL_TEMPLATES = "BillTemplates"
    const val BILLING_CONFIGS = "BillingConfigs"
    const val USER_PERMISSIONS = "UserPermissions"
    const val UI_CONFIGS = "UiConfigs"
    const val KITCHEN_ORDERS = "KitchenOrders"

    val ALL = listOf(
        COMPANIES, CATEGORIES, ITEMS, CUSTOMERS, SUPPLIERS, SALES_REFS,
        INVOICES, STOCK_ITEMS, BILLS, CUSTOMER_PAYMENTS, SUPPLIER_PAYMENTS,
        EXPENSES, BILL_TEMPLATES, BILLING_CONFIGS, USER_PERMISSIONS,
        UI_CONFIGS, KITCHEN_ORDERS
    )
}

/**
 * SyncRepository — phase 1 scaffold.
 *
 * `pullAllFromServer()` and `exportBillsToServer()` walk the 17 tables and
 * emit per-table progress. Phase 1 wires the local DAOs and progress plumbing;
 * the actual REST calls are stubbed and return zero rows so the screen renders
 * end-to-end. Phase 2 fills in the Retrofit endpoints (Items, Categories,
 * Customers, Suppliers, BillTemplates, UiConfigs first — the ones cash bill /
 * image bill / credit bill / purchasing depend on).
 */
class SyncRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = CosmosDatabase.get(appContext)
    private val settings = AppSettings.get(appContext)

    private val _progress = MutableStateFlow(SyncProgress(tables = emptyStatuses()))
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    private val _imageSync = MutableStateFlow(ImageSyncProgress())
    val imageSync: StateFlow<ImageSyncProgress> = _imageSync.asStateFlow()

    private fun emptyStatuses(): Map<String, TableSyncStatus> =
        SyncTables.ALL.associateWith { TableSyncStatus(table = it) }

    private val isoFormatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private fun formatIso(epochMillis: Long): String = isoFormatter.format(java.util.Date(epochMillis))

    /** Reload local row counts from Room — called on screen open and after sync. */
    suspend fun refreshLocalCounts() {
        val counts = mapOf(
            SyncTables.COMPANIES to db.companyDao().count(),
            SyncTables.CATEGORIES to db.categoryDao().count(),
            SyncTables.ITEMS to db.itemDao().count(),
            SyncTables.CUSTOMERS to db.customerDao().count(),
            SyncTables.SUPPLIERS to db.supplierDao().count(),
            SyncTables.SALES_REFS to db.salesRefDao().count(),
            SyncTables.INVOICES to db.invoiceDao().count(),
            SyncTables.STOCK_ITEMS to db.stockItemDao().count(),
            SyncTables.BILLS to db.billDao().count(),
            SyncTables.CUSTOMER_PAYMENTS to db.customerPaymentDao().count(),
            SyncTables.SUPPLIER_PAYMENTS to db.supplierPaymentDao().count(),
            SyncTables.EXPENSES to db.expenseDao().count(),
            SyncTables.BILL_TEMPLATES to db.billTemplateDao().count(),
            SyncTables.BILLING_CONFIGS to db.billingConfigDao().count(),
            SyncTables.USER_PERMISSIONS to db.userPermissionDao().count(),
            SyncTables.UI_CONFIGS to db.uiConfigDao().count(),
            SyncTables.KITCHEN_ORDERS to db.kitchenOrderDao().count()
        )
        val current = _progress.value
        val next = current.tables.mapValues { (table, status) ->
            status.copy(
                localCount = counts[table] ?: 0,
                lastSyncAt = if (status.lastSyncAt > 0) status.lastSyncAt else settings.getTableLastSync(table)
            )
        }
        _progress.value = current.copy(tables = next)
    }

    /**
     * Pull every entity from the server into Room. Walks the 17 logical tables
     * in order, calling the appropriate REST endpoint for each, and emits
     * per-table progress to the Sync Data screen. Tables without a wired
     * endpoint are marked Skipped.
     */
    suspend fun pullAllFromServer(token: String) {
        startRun()
        try {
            val total = SyncTables.ALL.size
            SyncTables.ALL.forEachIndexed { index, table ->
                _progress.value = _progress.value.copy(
                    phase = "Pulling $table…",
                    overallPercent = (index * 100) / total
                )
                updateTable(table) {
                    it.copy(status = "Syncing", processed = 0, total = 0, errorMessage = null)
                }
                runCatching { pullTable(table, token) }
                    .onFailure { err ->
                        android.util.Log.e("SyncRepository", "pull $table failed", err)
                        updateTable(table) {
                            it.copy(
                                status = "Error",
                                errorMessage = err.message ?: err.javaClass.simpleName
                            )
                        }
                    }
                _progress.value = _progress.value.copy(
                    phase = "Pulled $table (${index + 1}/$total)",
                    overallPercent = ((index + 1) * 100) / total
                )
            }
            refreshLocalCounts()
            settings.setLastSyncAt(System.currentTimeMillis())
        } finally {
            finishRun()
        }
    }

    private suspend fun pullTable(table: String, token: String) {
        val now = System.currentTimeMillis()
        when (table) {
            SyncTables.CATEGORIES -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8080}/category/getAllCategories", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    CategoryEntity(
                        id = id,
                        name = o.optStr("name"),
                        parentId = o.optLong("parentId"),
                        companyCode = o.optInt("companyCode") ?: 0,
                        active = o.optStr("active") ?: "Y",
                        updatedAt = now
                    )
                }
                db.categoryDao().clear()
                db.categoryDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.ITEMS -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/item/sync-items", token, watermark
                ) { page, pageIndex ->
                    val rows = page.mapNotNull { o ->
                        val id = o.optLong("id") ?: return@mapNotNull null
                        ItemEntity(
                            id = id,
                            itemCode = o.optStr("itemCode"),
                            barcode = o.optStr("barcode"),
                            name = o.optStr("name"),
                            categoryId = o.optLong("categoryId"),
                            unit = o.optStr("unit"),
                            unitCost = o.optDouble("unitCost") ?: 0.0,
                            sellingPrice = o.optDouble("sellingPrice") ?: 0.0,
                            sellingPrice2 = o.optDouble("sellingPrice2") ?: 0.0,
                            sellingPrice3 = o.optDouble("sellingPrice3") ?: 0.0,
                            stockQty = o.optDouble("stockQty") ?: 0.0,
                            imageUrl = o.optStr("image") ?: o.optStr("imageUrl"),
                            companyCode = o.optInt("companyCode") ?: 0,
                            active = o.optStr("active") ?: "Y",
                            updatedAt = now
                        )
                    }
                    if (pageIndex == 0 && watermark == null) db.itemDao().clear()
                    db.itemDao().upsertAll(rows)
                    rows.size
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.COMPANIES -> {
                val username = SessionManager.getInstance(appContext).getSavedUsername().orEmpty()
                if (username.isBlank()) return markSkipped(table, "no username")
                val obj = fetchJsonObject(
                    "${ApiConfig.BASE_URL_8090}/company/getCompanyByUser?userName=$username",
                    token
                ) ?: return markSkipped(table, "endpoint unreachable")
                val code = obj.optInt("companyCode") ?: return markSkipped(table, "missing companyCode")
                val row = CompanyEntity(
                    companyCode = code,
                    name = obj.optStr("name"),
                    address = obj.optStr("address"),
                    telephone = obj.optStr("telephone"),
                    email = obj.optStr("email"),
                    active = obj.optStr("active") ?: "Y"
                )
                db.companyDao().clear()
                db.companyDao().upsertAll(listOf(row))
                markDone(table, 1, now)
            }
            SyncTables.INVOICES -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/invoice/sync-invoices", token, watermark
                ) { page, pageIndex ->
                    val rows = page.mapNotNull { o ->
                        val id = o.optLong("id") ?: return@mapNotNull null
                        InvoiceEntity(
                            id = id,
                            invoiceNo = o.optStr("invoiceNo"),
                            invoiceDate = o.optStr("invoiceDate"),
                            supplierId = o.optLong("supplierId") ?: 0L,
                            total = o.optDouble("total") ?: 0.0,
                            paid = o.optDouble("paid") ?: 0.0,
                            companyCode = o.optInt("companyCode") ?: 0,
                            isSynced = true,
                            active = o.optStr("active") ?: "Y",
                            updatedAt = now
                        )
                    }
                    if (pageIndex == 0 && watermark == null) db.invoiceDao().clear()
                    db.invoiceDao().upsertAll(rows)
                    rows.size
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.STOCK_ITEMS -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/stock/sync-stock-items", token, watermark
                ) { page, pageIndex ->
                    val rows = page.mapNotNull { o ->
                        val id = o.optLong("id") ?: return@mapNotNull null
                        StockItemEntity(
                            id = 0,
                            serverId = id,
                            itemId = o.optLong("itemId") ?: 0L,
                            invoiceId = o.optLong("invoiceId"),
                            qty = o.optDouble("qty") ?: 0.0,
                            unitCost = o.optDouble("unitCost") ?: 0.0,
                            expiryDate = o.optStr("expiryDate"),
                            companyCode = o.optInt("companyCode") ?: 0,
                            isSynced = true,
                            updatedAt = now
                        )
                    }
                    if (pageIndex == 0 && watermark == null) db.stockItemDao().clear()
                    db.stockItemDao().upsertAll(rows)
                    rows.size
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.BILLS -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/bill/sync-bills", token, watermark
                ) { page, pageIndex ->
                    // Wipe only synced rows so unsynced (queued local) bills survive — full pulls only.
                    if (pageIndex == 0 && watermark == null) {
                        db.billDao().clearSyncedLines()
                        db.billDao().clearSynced()
                    }
                    var pageInserted = 0
                    for (o in page) {
                        val serverId = o.optLong("id") ?: continue
                        val bill = BillEntity(
                            id = 0,
                            serverId = serverId,
                            billNo = o.optLong("billNo") ?: 0L,
                            billingDate = o.optStr("billingDate"),
                            billType = o.optStr("billType") ?: "CASH",
                            customerId = o.optLong("customerId") ?: 0L,
                            cashCustomerName = o.optStr("customerName"),
                            salesRefId = o.optLong("salesRefId") ?: 0L,
                            total = o.optDouble("total") ?: 0.0,
                            discount = o.optDouble("discount") ?: 0.0,
                            discountValue = o.optDouble("discountValue") ?: 0.0,
                            cash = o.optDouble("cash") ?: 0.0,
                            cardPayment = o.optDouble("cardPayment") ?: 0.0,
                            companyCode = o.optInt("companyCode") ?: 0,
                            createdByUser = o.optStr("createdByUser"),
                            deviceName = null,
                            isSynced = true,
                            active = o.optStr("active") ?: "Y"
                        )
                        val localId = db.billDao().insert(bill)
                        val itemsArr = if (o.has("billItems") && o.get("billItems").isJsonArray) o.getAsJsonArray("billItems") else null
                        val lines = itemsArr?.mapNotNull { el ->
                            val li = el.asJsonObject
                            BillLineEntity(
                                billLocalId = localId,
                                serverId = li.optLong("id"),
                                itemId = li.optLong("itemId") ?: 0L,
                                unitPrice = li.optDouble("unitPrice") ?: 0.0,
                                qty = li.optDouble("qty") ?: 0.0,
                                returnQty = li.optDouble("returnQty") ?: 0.0,
                                discount = li.optDouble("discount") ?: 0.0,
                                discountValue = li.optDouble("discountValue") ?: 0.0,
                                freeIssueQty = li.optDouble("freeIssueQty") ?: 0.0
                            )
                        } ?: emptyList()
                        if (lines.isNotEmpty()) db.billLineDao().insertAll(lines)
                        pageInserted++
                    }
                    pageInserted
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.CUSTOMER_PAYMENTS -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/customer/payment/sync-payments", token, watermark
                ) { page, pageIndex ->
                    val rows = page.mapNotNull { o ->
                        val id = o.optLong("id") ?: return@mapNotNull null
                        CustomerPaymentEntity(
                            id = 0,
                            serverId = id,
                            customerId = o.optLong("customerId") ?: 0L,
                            paymentDate = o.optStr("paymentDate"),
                            amount = o.optDouble("paidAmount") ?: o.optDouble("amount") ?: 0.0,
                            method = o.optStr("type"),
                            note = o.optStr("chequeNumber"),
                            companyCode = o.optInt("companyCode") ?: 0,
                            isSynced = true
                        )
                    }
                    if (pageIndex == 0 && watermark == null) db.customerPaymentDao().clear()
                    db.customerPaymentDao().upsertAll(rows)
                    rows.size
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.SUPPLIER_PAYMENTS -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8080}/supplier/payment/all", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    SupplierPaymentEntity(
                        id = 0,
                        serverId = id,
                        supplierId = o.optLong("supplierId") ?: 0L,
                        paymentDate = o.optStr("paymentDate"),
                        amount = o.optDouble("paidAmount") ?: o.optDouble("amount") ?: 0.0,
                        method = o.optStr("type"),
                        note = o.optStr("chequeNumber"),
                        companyCode = o.optInt("companyCode") ?: 0,
                        isSynced = true
                    )
                }
                db.supplierPaymentDao().clear()
                db.supplierPaymentDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.EXPENSES -> {
                val watermark = settings.getTableLastSync(table).takeIf { it > 0 }?.let { formatIso(it) }
                val inserted = fetchPaginatedStreaming(
                    table, "${ApiConfig.BASE_URL_8080}/account/sync-expenses", token, watermark
                ) { page, pageIndex ->
                    val rows = page.mapNotNull { o ->
                        val id = o.optLong("id") ?: return@mapNotNull null
                        ExpenseEntity(
                            id = 0,
                            serverId = id,
                            expenseDate = o.optStr("expenseDate") ?: o.optStr("date"),
                            category = o.optStr("category") ?: o.optStr("expenseType"),
                            amount = o.optDouble("amount") ?: 0.0,
                            note = o.optStr("note") ?: o.optStr("description"),
                            companyCode = o.optInt("companyCode") ?: 0,
                            isSynced = true
                        )
                    }
                    if (pageIndex == 0 && watermark == null) db.expenseDao().clear()
                    db.expenseDao().upsertAll(rows)
                    rows.size
                } ?: return markSkipped(table, "endpoint unreachable")
                markDone(table, inserted, now)
            }
            SyncTables.BILL_TEMPLATES -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8080}/bill/bill-templates", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    BillTemplateEntity(
                        id = id,
                        name = o.optStr("name") ?: o.optStr("templateName"),
                        templateJson = o.toString(),
                        companyCode = o.optInt("companyCode") ?: 0,
                        updatedAt = now
                    )
                }
                db.billTemplateDao().clear()
                db.billTemplateDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.CUSTOMERS -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    CustomerEntity(
                        id = id,
                        name = o.optStr("name"),
                        address = o.optStr("address"),
                        telephone = o.optStr("telephone"),
                        whatsapp = o.optStr("whatsapp"),
                        email = o.optStr("email"),
                        outstandingBalance = o.optDouble("outstandingBalance") ?: 0.0,
                        companyCode = o.optInt("companyCode") ?: 0,
                        active = o.optStr("active") ?: "Y",
                        updatedAt = now
                    )
                }
                db.customerDao().clear()
                db.customerDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.SUPPLIERS -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8090}/supplier/getAllSuppliers?companyCode=0", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    SupplierEntity(
                        id = id,
                        name = o.optStr("name"),
                        address = o.optStr("address"),
                        telephone = o.optStr("telephone"),
                        email = o.optStr("email"),
                        outstandingBalance = o.optDouble("outstandingBalance") ?: 0.0,
                        companyCode = o.optInt("companyCode") ?: 0,
                        active = o.optStr("active") ?: "Y",
                        updatedAt = now
                    )
                }
                db.supplierDao().clear()
                db.supplierDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.SALES_REFS -> {
                val arr = fetchJsonArray("${ApiConfig.BASE_URL_8090}/salesRef/getAllSalesRef", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val rows = arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    SalesRefEntity(
                        id = id,
                        name = o.optStr("name"),
                        telephone = o.optStr("telephone"),
                        companyCode = o.optInt("companyCode") ?: 0,
                        active = o.optStr("active") ?: "Y",
                        updatedAt = now
                    )
                }
                db.salesRefDao().clear()
                db.salesRefDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            SyncTables.BILLING_CONFIGS -> {
                val obj = fetchJsonObject("${ApiConfig.BASE_URL_8090}/admin/getConfigsByToken", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                // Desktop stores BillingConfig as a single record. Cache the
                // full JSON blob in one row so the count stays at 1.
                val row = BillingConfigEntity(
                    key = "config",
                    value = obj.toString(),
                    companyCode = obj.optInt("companyCode") ?: 0,
                    updatedAt = now
                )
                db.billingConfigDao().clear()
                db.billingConfigDao().upsertAll(listOf(row))
                markDone(table, 1, now)
            }
            SyncTables.USER_PERMISSIONS -> {
                val obj = fetchJsonObject("${ApiConfig.BASE_URL_8090}/admin/getUserPermissionsByToken", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val username = SessionManager.getInstance(appContext).getSavedUsername().orEmpty()
                    .ifBlank { obj.optStr("username") ?: "current" }
                val row = com.pos.cosmos.local.entities.UserPermissionEntity(
                    username = username,
                    permissionsJson = obj.toString(),
                    companyCode = obj.optInt("companyCode") ?: 0,
                    updatedAt = now
                )
                db.userPermissionDao().clear()
                db.userPermissionDao().upsertAll(listOf(row))
                markDone(table, 1, now)
            }
            SyncTables.UI_CONFIGS -> {
                val obj = fetchJsonObject("${ApiConfig.BASE_URL_8090}/company/ui-config-by-token", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val row = com.pos.cosmos.local.entities.UiConfigEntity(
                    key = "ui",
                    value = obj.toString(),
                    companyCode = obj.optInt("companyCode") ?: 0,
                    updatedAt = now
                )
                db.uiConfigDao().clear()
                db.uiConfigDao().upsertAll(listOf(row))
                markDone(table, 1, now)
            }
            SyncTables.KITCHEN_ORDERS -> {
                val parsed = httpGet("${ApiConfig.BASE_URL_8080}/kitchen-order", token)
                    ?: return markSkipped(table, "endpoint unreachable")
                val arr = when {
                    parsed.isJsonArray -> parsed.asJsonArray
                    parsed.isJsonObject && parsed.asJsonObject.has("content") &&
                        parsed.asJsonObject.get("content").isJsonArray ->
                        parsed.asJsonObject.getAsJsonArray("content")
                    else -> return markSkipped(table, "unexpected response")
                }
                val rows = arr.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val o = el.asJsonObject
                    val id = o.optLong("id") ?: return@mapNotNull null
                    com.pos.cosmos.local.entities.KitchenOrderEntity(
                        id = 0,
                        serverId = id,
                        kotNo = o.optStr("kotNo") ?: o.optStr("orderNo"),
                        kotDate = o.optStr("kotDate") ?: o.optStr("createdAt"),
                        orderType = o.optStr("orderType") ?: "TAKEAWAY",
                        status = o.optStr("status") ?: "PENDING",
                        tableNo = o.optStr("tableNo"),
                        subTotal = o.optDouble("subTotal") ?: 0.0,
                        serviceCharge = o.optDouble("serviceCharge") ?: 0.0,
                        grandTotal = o.optDouble("grandTotal") ?: 0.0,
                        notes = o.optStr("notes"),
                        companyCode = o.optInt("companyCode") ?: 0,
                        isSynced = true
                    )
                }
                db.kitchenOrderDao().clear()
                db.kitchenOrderDao().upsertAll(rows)
                markDone(table, rows.size, now)
            }
            else -> markSkipped(table, "no endpoint wired")
        }
    }

    private fun markDone(table: String, count: Int, ts: Long) {
        settings.setTableLastSync(table, ts)
        updateTable(table) {
            it.copy(
                status = "Done",
                processed = count,
                total = count,
                syncedRecords = count,
                serverCount = count,
                lastSyncAt = ts,
                errorMessage = null
            )
        }
    }

    private fun markSkipped(table: String, reason: String) {
        updateTable(table) {
            it.copy(status = "Skipped", processed = 0, total = 0, errorMessage = reason)
        }
    }

    private suspend fun fetchJsonArray(url: String, token: String): JsonArray? {
        val parsed = httpGet(url, token) ?: return null
        return if (parsed.isJsonArray) parsed.asJsonArray else null
    }

    /**
     * Walks a Spring `PagedResponse` endpoint (`?page=N&size=100`), handing each
     * page to [onPage] as it lands and returning the total [onPage] reported.
     * Emits incremental per-table progress. Returns null if the very first
     * request fails, so callers can distinguish "unreachable" from "empty".
     *
     * Each page is dropped as soon as it is written, so peak memory is one page
     * rather than the whole table. The previous version accumulated every page
     * into one list and only then converted it to entities, holding the full
     * Gson tree and the full entity list on the heap simultaneously — which is
     * what put low-RAM devices over their limit on the bills and items tables.
     *
     * [onPage] receives the zero-based page index so callers can defer a
     * destructive `clear()` until the server has actually answered.
     */
    private suspend fun fetchPaginatedStreaming(
        table: String,
        baseUrl: String,
        token: String,
        modifiedAfter: String? = null,
        pageSize: Int = 100,
        onPage: suspend (rows: List<JsonObject>, pageIndex: Int) -> Int
    ): Int? {
        var page = 0
        var processed = 0
        while (true) {
            val sep = if (baseUrl.contains("?")) "&" else "?"
            val watermark = modifiedAfter?.let { "&modifiedAfter=$it" }.orEmpty()
            val url = "$baseUrl${sep}page=$page&size=$pageSize$watermark"
            val resp = fetchJsonObject(url, token) ?: return if (page == 0) null else processed
            val content = if (resp.has("content") && resp.get("content").isJsonArray)
                resp.getAsJsonArray("content") else null

            val rows = ArrayList<JsonObject>(content?.size() ?: 0)
            if (content != null) for (el in content) if (el.isJsonObject) rows.add(el.asJsonObject)
            processed += onPage(rows, page)

            if (page == 0) {
                val totalElements = resp.optInt("totalElements") ?: processed
                updateTable(table) {
                    it.copy(status = "Syncing", total = totalElements, processed = processed, serverCount = totalElements)
                }
            } else {
                updateTable(table) { it.copy(processed = processed) }
            }
            val isLast = resp.optBool("last") ?: true
            val empty = content == null || content.size() == 0
            if (isLast || empty) break
            page++
        }
        return processed
    }

    private suspend fun fetchJsonObject(url: String, token: String): JsonObject? {
        val parsed = httpGet(url, token) ?: return null
        return if (parsed.isJsonObject) parsed.asJsonObject else null
    }

    private suspend fun httpGet(url: String, token: String): JsonElement? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                JsonParser.parseString(body)
            } else {
                android.util.Log.w("SyncRepository", "GET $url -> ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "GET $url failed", e)
            null
        }
    }

    private fun JsonObject.optStr(k: String): String? {
        if (!has(k)) return null
        val el = get(k)
        if (el == null || el.isJsonNull) return null
        return runCatching { el.asString }.getOrNull()
    }

    private fun JsonObject.optLong(k: String): Long? {
        if (!has(k)) return null
        val el = get(k)
        if (el == null || el.isJsonNull) return null
        return runCatching { el.asLong }.getOrNull()
    }

    private fun JsonObject.optInt(k: String): Int? {
        if (!has(k)) return null
        val el = get(k)
        if (el == null || el.isJsonNull) return null
        return runCatching { el.asInt }.getOrNull()
    }

    private fun JsonObject.optDouble(k: String): Double? {
        if (!has(k)) return null
        val el = get(k)
        if (el == null || el.isJsonNull) return null
        return runCatching { el.asDouble }.getOrNull()
    }

    private fun JsonObject.optBool(k: String): Boolean? {
        if (!has(k)) return null
        val el = get(k)
        if (el == null || el.isJsonNull) return null
        return runCatching { el.asBoolean }.getOrNull()
    }

    /**
     * Push locally-created bills (and payments / expenses / KOTs) to the server.
     * POSTs each unsynced bill to `/bill/saveBill`, marks it synced on success.
     */
    suspend fun exportPendingToServer(token: String): Int {
        startRun()
        try {
            val unsynced = db.billDao().unsynced()
            updateTable(SyncTables.BILLS) {
                it.copy(status = "Syncing", total = unsynced.size, processed = 0)
            }
            var pushed = 0
            var failed = 0
            for ((index, bill) in unsynced.withIndex()) {
                val lines = db.billLineDao().forBill(bill.id)
                val serverId = postBill(token, bill, lines)
                if (serverId != null) {
                    db.billDao().markSynced(bill.id, serverId)
                    pushed++
                } else {
                    failed++
                }
                updateTable(SyncTables.BILLS) {
                    it.copy(
                        status = if (index + 1 == unsynced.size) "Done" else "Syncing",
                        processed = index + 1,
                        syncedRecords = pushed
                    )
                }
            }
            val pendingPayments = db.customerPaymentDao().unsyncedCount()
            updateTable(SyncTables.CUSTOMER_PAYMENTS) {
                it.copy(status = "Idle", total = pendingPayments)
            }
            // Push owner→customer refund payments queued by ImageBillScreen
            // when useLocalDatabase is enabled.
            val unsyncedOwnerPayments = db.ownerCustomerPaymentDao().unsynced()
            for (payment in unsyncedOwnerPayments) {
                val serverId = postOwnerCustomerPayment(token, payment)
                if (serverId != null) {
                    db.ownerCustomerPaymentDao().markSynced(payment.id, serverId)
                }
            }
            settings.setLastSyncAt(System.currentTimeMillis())
            refreshLocalCounts()
            return pushed
        } finally {
            finishRun()
        }
    }

    private suspend fun postOwnerCustomerPayment(
        token: String,
        payment: com.pos.cosmos.local.entities.OwnerCustomerPaymentEntity
    ): Long? = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                addProperty("customerId", payment.customerId)
                addProperty("customerName", payment.customerName ?: "")
                addProperty("billId", payment.billId)
                addProperty("billType", payment.billType ?: "")
                addProperty("amount", payment.amount)
                addProperty("method", payment.method ?: "CASH")
                addProperty("note", payment.note ?: "")
                addProperty("active", "Y")
            }
            val conn = URL("${ApiConfig.BASE_URL_8080}/owner-customer/payment/add")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                JsonParser.parseString(body).asJsonObject.optLong("id")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown"
                android.util.Log.e("SyncRepository", "owner-payment push failed: ${conn.responseCode} - $err")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "owner-payment push exception", e)
            null
        }
    }

    private suspend fun postBill(token: String, bill: BillEntity, lines: List<BillLineEntity>): Long? = withContext(Dispatchers.IO) {
        try {
            val payload = JsonObject().apply {
                val itemsArr = com.google.gson.JsonArray()
                for (line in lines) {
                    itemsArr.add(JsonObject().apply {
                        addProperty("itemId", line.itemId)
                        addProperty("qty", line.qty.toString())
                        addProperty("unitPrice", line.unitPrice)
                        addProperty("discount", line.discount)
                        addProperty("discountValue", line.discountValue)
                        addProperty("totalPrice", line.qty * (line.unitPrice - line.discountValue))
                        addProperty("totalQty", line.qty)
                        addProperty("freeIssueQty", line.freeIssueQty)
                        addProperty("isBox", false)
                    })
                }
                add("billItems", itemsArr)
                addProperty("id", 0)
                addProperty("billNo", bill.billNo)
                addProperty("billType", bill.billType)
                addProperty("customerId", bill.customerId)
                addProperty("customerName", bill.cashCustomerName ?: "")
                addProperty("salesRefId", bill.salesRefId)
                addProperty("salesRefName", "")
                addProperty("cash", bill.cash)
                addProperty("paidAmount", bill.cash)
                addProperty("discount", bill.discount)
                addProperty("discountValue", bill.discountValue)
                addProperty("discountPercentage", 0.0)
                addProperty("total", bill.total)
                addProperty("billingDate", bill.billingDate)
                addProperty("paymentMethod", "CASH")
                addProperty("paymentDetails", "")
                add("userEvent", JsonObject().apply {
                    addProperty("event", "Sync queued bill ${bill.billNo}")
                    addProperty("latitude", "")
                    addProperty("longitude", "")
                    addProperty("userName", bill.createdByUser ?: "")
                    addProperty("eventTime", "")
                    add("mapLocations", com.google.gson.JsonNull.INSTANCE)
                })
            }
            val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBill")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val parsed = runCatching { JsonParser.parseString(body) }.getOrNull()
                val obj = parsed?.takeIf { it.isJsonObject }?.asJsonObject
                obj?.let {
                    if (it.has("id") && !it.get("id").isJsonNull) it.get("id").asLong
                    else if (it.has("billId") && !it.get("billId").isJsonNull) it.get("billId").asLong
                    else bill.billNo
                } ?: bill.billNo
            } else {
                android.util.Log.e("SyncRepository", "postBill failed code=$code body=${connection.errorStream?.bufferedReader()?.use { it.readText() }}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "postBill exception", e)
            null
        }
    }

    /**
     * Download item images to filesDir/images/. Mirrors desktop SyncImagesAsync:
     * sequential per item, skips files already on disk, falls back to the
     * static `static/company_X/item_Y.jpeg` URL if the item's own `image`
     * URL fails. Reports progress via [imageSync].
     */
    suspend fun syncImages(token: String): Int = withContext(Dispatchers.IO) {
        _imageSync.value = ImageSyncProgress(isRunning = true, message = "Refreshing item metadata…")
        try {
            // Force a full re-pull of items so the `image` field is current,
            // even if a prior incremental sync skipped them.
            settings.setTableLastSync(SyncTables.ITEMS, 0L)
            runCatching { pullTable(SyncTables.ITEMS, token) }

            val items = db.itemDao().all().filter { !it.imageUrl.isNullOrBlank() }

            // Categories don't store an image column locally, so fetch URLs
            // straight from the server (mirrors desktop SyncImagesAsync).
            _imageSync.value = _imageSync.value.copy(message = "Refreshing categories…")
            val catArr = fetchJsonArray("${ApiConfig.BASE_URL_8080}/category/getAllCategories", token)
            val categories: List<Pair<Long, String>> = catArr?.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.optLong("id") ?: return@mapNotNull null
                val img = o.optStr("image") ?: o.optStr("imageUrl")
                if (img.isNullOrBlank()) null else id to img
            } ?: emptyList()

            val total = items.size + categories.size
            _imageSync.value = _imageSync.value.copy(total = total, message = "Downloading 0/$total")

            var processed = 0
            var downloaded = 0
            var skipped = 0
            var failed = 0

            for (item in items) {
                val file = com.pos.cosmos.local.LocalImageStore.itemFile(appContext, item.id)
                if (file.exists() && file.length() > 0) {
                    skipped++
                } else {
                    if (downloadImageTo(item.imageUrl!!, file)) downloaded++ else failed++
                }
                processed++
                _imageSync.value = _imageSync.value.copy(
                    processed = processed,
                    downloaded = downloaded,
                    skipped = skipped,
                    failed = failed,
                    message = "Items $processed / $total"
                )
            }

            for ((id, url) in categories) {
                val file = com.pos.cosmos.local.LocalImageStore.categoryFile(appContext, id)
                if (file.exists() && file.length() > 0) {
                    skipped++
                } else {
                    if (downloadImageTo(url, file)) downloaded++ else failed++
                }
                processed++
                _imageSync.value = _imageSync.value.copy(
                    processed = processed,
                    downloaded = downloaded,
                    skipped = skipped,
                    failed = failed,
                    message = "Categories $processed / $total"
                )
            }

            _imageSync.value = _imageSync.value.copy(
                isRunning = false,
                message = "Done — $downloaded new, $skipped cached, $failed failed"
            )
            downloaded
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "syncImages failed", e)
            _imageSync.value = _imageSync.value.copy(isRunning = false, message = "Error: ${e.message}")
            0
        }
    }

    private fun downloadImageTo(url: String, dest: File): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            if (conn.responseCode !in 200..299) return false
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (tmp.length() == 0L) {
                tmp.delete(); return false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "image $url failed: ${e.message}")
            false
        }
    }

    /** Wipe every Room table AND per-table watermarks — backs the desktop "Clear & Re-sync" button. */
    suspend fun clearAllLocalData() {
        settings.clearAllTableLastSync()
        db.companyDao().clear()
        db.categoryDao().clear()
        db.itemDao().clear()
        db.customerDao().clear()
        db.supplierDao().clear()
        db.salesRefDao().clear()
        db.invoiceDao().clear()
        db.stockItemDao().clear()
        db.billDao().clear()
        db.customerPaymentDao().clear()
        db.supplierPaymentDao().clear()
        db.expenseDao().clear()
        db.billTemplateDao().clear()
        db.billingConfigDao().clear()
        db.userPermissionDao().clear()
        db.uiConfigDao().clear()
        db.kitchenOrderDao().clear()
        _progress.value = SyncProgress(tables = emptyStatuses())
        refreshLocalCounts()
    }

    private fun startRun() {
        _progress.value = _progress.value.copy(isRunning = true, phase = "Starting…", overallPercent = 0)
    }

    private fun finishRun() {
        _progress.value = _progress.value.copy(isRunning = false, phase = "Done", overallPercent = 100)
    }

    private fun updateTable(table: String, transform: (TableSyncStatus) -> TableSyncStatus) {
        val current = _progress.value
        val existing = current.tables[table] ?: TableSyncStatus(table = table)
        _progress.value = current.copy(tables = current.tables + (table to transform(existing)))
    }

    companion object {
        @Volatile private var instance: SyncRepository? = null

        fun get(context: Context): SyncRepository {
            return instance ?: synchronized(this) {
                instance ?: SyncRepository(context).also { instance = it }
            }
        }
    }
}
