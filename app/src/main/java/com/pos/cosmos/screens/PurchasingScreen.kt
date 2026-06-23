package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone
import com.pos.cosmos.api.ApiConfig

// Data Models
data class Supplier(
    val id: Int = 0,
    val name: String? = null,
    val contact: String? = null,
    val phone: String? = null,
    val address: String? = null
)

data class Invoice(
    val id: Int = 0,
    val invoiceName: String? = null,
    val invoiceDate: String? = null,
    val supplierId: Int = 0
)

data class PurchasingItem(
    val id: Int = 0,
    val name: String? = null,
    val code: String? = null,
    val unitCost: Double = 0.0,
    val sellingPrice: Double = 0.0
)

data class PurchaseItem(
    val id: Int = 0,
    val name: String? = null,
    val code: String? = null,
    val unitCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val qty: Int = 0,
    val total: Double = 0.0,
    val barcode: String? = null,
    val defaultDiscountForPrice: Double = 0.0
)

data class InvoiceDetail(
    val itemId: Int = 0,
    val itemCode: String? = null,
    val itemName: String? = null,
    val unitCost: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val qty: Int = 0,
    val total: Double = 0.0,
    val barcode: String? = null,
    val defaultDiscountForPrice: Double = 0.0
)

data class InvoiceData(
    val id: Int = 0,
    val shopId: Int = 1,
    val invoiceName: String? = null,
    val invoiceDetails: List<InvoiceDetail> = emptyList(),
    val totalValue: Double = 0.0,
    val totalItems: Int = 0,
    val supplierId: Int = 0,
    val invoiceDate: String? = null
)

data class InvoiceResponse(
    val invoiceDetails: List<InvoiceDetail>? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasingScreenFull(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var supplierInput by remember { mutableStateOf("") }
    var invoiceInput by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf(TextFieldValue("")) }
    var itemCode by remember { mutableStateOf(TextFieldValue("")) }
    var unitCost by remember { mutableStateOf(TextFieldValue("")) }
    var sellingPrice by remember { mutableStateOf(TextFieldValue("")) }
    var quantity by remember { mutableStateOf(TextFieldValue("")) }
    var barcode by remember { mutableStateOf(TextFieldValue("")) }
    var defaultDiscountForPrice by remember { mutableStateOf(TextFieldValue("")) }

    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var filteredSuppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var invoices by remember { mutableStateOf<List<Invoice>>(emptyList()) }
    var filteredInvoices by remember { mutableStateOf<List<Invoice>>(emptyList()) }
    var existingItems by remember { mutableStateOf<List<PurchasingItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<PurchasingItem>>(emptyList()) }
    var purchaseItems by remember { mutableStateOf<List<PurchaseItem>>(emptyList()) }

    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    var selectedInvoice by remember { mutableStateOf<Invoice?>(null) }
    var expandedSupplier by remember { mutableStateOf(false) }
    var expandedInvoice by remember { mutableStateOf(false) }
    var isNewInvoice by remember { mutableStateOf(false) }
    var newInvoiceName by remember { mutableStateOf("") }

    var showItemModal by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PurchaseItem?>(null) }
    var selectedExistingItemId by remember { mutableStateOf(0) }

    var itemNameSuggestions by remember { mutableStateOf<List<PurchasingItem>>(emptyList()) }
    var showItemNameSuggestions by remember { mutableStateOf(false) }
    var itemCodeSuggestions by remember { mutableStateOf<List<PurchasingItem>>(emptyList()) }
    var showItemCodeSuggestions by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var isLoadingSuppliers by remember { mutableStateOf(true) }
    var isLoadingInvoices by remember { mutableStateOf(false) }
    var isLoadingInvoiceDetails by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessMessage by remember { mutableStateOf(true) }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun generateNextItemCode(): String {
        val allCodes = mutableListOf<String>()
        existingItems.forEach { it.code?.let(allCodes::add) }
        purchaseItems.forEach { it.code?.let(allCodes::add) }
        var maxNum = 0
        allCodes.forEach { code ->
            val digits = code.filter { it.isDigit() }
            val num = digits.toIntOrNull()
            if (num != null && num > maxNum) maxNum = num
        }
        return (maxNum + 1).toString().padStart(3, '0')
    }

    fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun fetchItems() {
        scope.launch {
            try {
                isLoading = true
                val items = withContext(Dispatchers.IO) {
                    val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val response = if (connection.responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val gson = Gson()
                        gson.fromJson(content, Array<PurchasingItem>::class.java).toList()
                    } else {
                        emptyList()
                    }
                    response
                }
                existingItems = items
                filteredItems = items
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }

    fun fetchSuppliers() {
        scope.launch {
            try {
                isLoadingSuppliers = true
                val supplierList = withContext(Dispatchers.IO) {
                    val connection = URL("${ApiConfig.BASE_URL_8090}/supplier/getAllSuppliers?companyCode=0")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val response = if (connection.responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val gson = Gson()
                        gson.fromJson(content, Array<Supplier>::class.java).toList()
                    } else {
                        emptyList()
                    }
                    response
                }
                suppliers = supplierList
                filteredSuppliers = supplierList
                isLoadingSuppliers = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoadingSuppliers = false
            }
        }
    }

    fun fetchInvoicesBySupplier(supplierId: Int) {
        scope.launch {
            try {
                isLoadingInvoices = true
                val invoiceList = withContext(Dispatchers.IO) {
                    val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getAllInvoicesBySupplier?supplierId=$supplierId")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val response = if (connection.responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val gson = Gson()
                        gson.fromJson(content, Array<Invoice>::class.java).toList()
                    } else {
                        emptyList()
                    }
                    response
                }
                invoices = invoiceList
                filteredInvoices = invoiceList
                isLoadingInvoices = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoadingInvoices = false
            }
        }
    }

    fun fetchSelectedInvoice(invoiceId: Int) {
        scope.launch {
            try {
                isLoadingInvoiceDetails = true
                withContext(Dispatchers.IO) {
                    val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getSelectedInvoice?id=$invoiceId")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    if (connection.responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val gson = Gson()
                        val invoiceResponse = gson.fromJson(content, InvoiceResponse::class.java)

                        purchaseItems = invoiceResponse.invoiceDetails?.map { detail ->
                            PurchaseItem(
                                id = detail.itemId,
                                name = detail.itemName,
                                code = detail.itemCode,
                                unitCost = detail.unitCost,
                                sellingPrice = detail.sellingPrice,
                                qty = detail.qty,
                                total = detail.total,
                                barcode = detail.barcode,
                                defaultDiscountForPrice = detail.defaultDiscountForPrice
                            )
                        } ?: emptyList()
                    }
                }
                isLoadingInvoiceDetails = false
            } catch (e: Exception) {
                e.printStackTrace()
                isLoadingInvoiceDetails = false
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            fetchItems()
            fetchSuppliers()
            // Load config so flags like itemCodeAutoGenerateInPurchasing are available
            val appConfig = withContext(Dispatchers.IO) {
                fetchPurchasingConfig(context, token!!)
            }
            if (appConfig != null) {
                appViewModel.setConfig(appConfig)
            }
        }
    }

    fun filterSuppliers(query: String) {
        supplierInput = query
        if (query.isEmpty()) {
            filteredSuppliers = suppliers
            expandedSupplier = false
        } else {
            val filtered = suppliers.filter { supplier ->
                (supplier.name?.contains(query, ignoreCase = true) ?: false)
            }
            filteredSuppliers = filtered
            expandedSupplier = filtered.isNotEmpty()
        }
    }

    fun onSelectSupplier(supplier: Supplier) {
        supplierInput = supplier.name ?: ""
        selectedSupplier = supplier
        expandedSupplier = false
        invoiceInput = ""
        selectedInvoice = null
        purchaseItems = emptyList()
        filteredInvoices = emptyList()
        fetchInvoicesBySupplier(supplier.id)
    }

    fun filterInvoices(query: String) {
        invoiceInput = query
        if (query.isEmpty()) {
            filteredInvoices = invoices
            expandedInvoice = false
            isNewInvoice = false
        } else {
            val filtered = invoices.filter { invoice ->
                (invoice.invoiceName?.contains(query, ignoreCase = true) ?: false)
            }
            filteredInvoices = filtered
            expandedInvoice = filtered.isNotEmpty()
            // Check if user is trying to create a new invoice
            isNewInvoice = filtered.isEmpty() && query.isNotEmpty()
            newInvoiceName = query
        }
    }

    fun onSelectInvoice(invoice: Invoice) {
        invoiceInput = invoice.invoiceName ?: ""
        selectedInvoice = invoice
        expandedInvoice = false
        isNewInvoice = false
        fetchSelectedInvoice(invoice.id)
    }

    fun onCreateNewInvoice(invoiceName: String) {
        invoiceInput = invoiceName
        // Create a temporary invoice object for new invoices
        selectedInvoice = Invoice(id = 0, invoiceName = invoiceName, supplierId = selectedSupplier?.id ?: 0)
        expandedInvoice = false
        isNewInvoice = true
        newInvoiceName = invoiceName
        purchaseItems = emptyList()
    }

    fun calculateTotal(): Double {
        return purchaseItems.sumOf { it.total }
    }

    fun addOrUpdateItem() {
        val name = itemName.text.trim()
        val code = itemCode.text.trim()
        val cost = unitCost.text.toDoubleOrNull() ?: 0.0
        val price = sellingPrice.text.toDoubleOrNull() ?: 0.0
        val qty = quantity.text.toIntOrNull() ?: 0

        if (name.isEmpty() || code.isEmpty() || qty <= 0 || cost <= 0) {
            return  // Show validation error
        }

        val total = cost * qty

        // Find itemId from existing items if not already selected
        var itemId = selectedExistingItemId
        if (itemId == 0) {
            existingItems.find { it.code == code && it.name == name }?.let {
                itemId = it.id
            }
        }

        val newItem = PurchaseItem(
            id = if (editingItem != null) editingItem!!.id else itemId,
            name = name,
            code = code,
            unitCost = cost,
            sellingPrice = price,
            qty = qty,
            total = total,
            barcode = barcode.text.trim().ifEmpty { null },
            defaultDiscountForPrice = defaultDiscountForPrice.text.toDoubleOrNull() ?: 0.0
        )

        if (editingItem != null) {
            // Update existing item
            purchaseItems = purchaseItems.map { if (it.id == editingItem!!.id) newItem else it }
            editingItem = null
        } else {
            // Add new item
            purchaseItems = purchaseItems + newItem
        }

        // Clear form
        itemName = TextFieldValue("")
        itemCode = TextFieldValue("")
        unitCost = TextFieldValue("")
        sellingPrice = TextFieldValue("")
        quantity = TextFieldValue("")
        barcode = TextFieldValue("")
        defaultDiscountForPrice = TextFieldValue("")
        selectedExistingItemId = 0
        showItemModal = false
    }

    fun deleteItem(itemId: Int) {
        purchaseItems = purchaseItems.filter { it.id != itemId }
    }

    fun onItemNameChange(newValue: TextFieldValue) {
        itemName = newValue
        val query = newValue.text
        if (query.isEmpty()) {
            showItemNameSuggestions = false
            itemNameSuggestions = emptyList()
        } else {
            val filtered = existingItems.filter { item ->
                (item.name?.contains(query, ignoreCase = true) ?: false)
            }
            itemNameSuggestions = filtered
            showItemNameSuggestions = filtered.isNotEmpty()
        }
    }

    fun onItemCodeChange(newValue: TextFieldValue) {
        itemCode = newValue
        val query = newValue.text
        if (query.isEmpty()) {
            showItemCodeSuggestions = false
            itemCodeSuggestions = emptyList()
        } else {
            val filtered = existingItems.filter { item ->
                (item.code?.contains(query, ignoreCase = true) ?: false)
            }
            itemCodeSuggestions = filtered
            showItemCodeSuggestions = filtered.isNotEmpty()
        }
    }

    fun onSelectItemByName(item: PurchasingItem) {
        itemName = TextFieldValue(item.name ?: "")
        itemCode = TextFieldValue(item.code ?: "")
        unitCost = TextFieldValue(item.unitCost.toString())
        sellingPrice = TextFieldValue(item.sellingPrice.toString())
        selectedExistingItemId = item.id
        showItemNameSuggestions = false
        itemNameSuggestions = emptyList()
    }

    fun onSelectItemByCode(item: PurchasingItem) {
        itemName = TextFieldValue(item.name ?: "")
        itemCode = TextFieldValue(item.code ?: "")
        unitCost = TextFieldValue(item.unitCost.toString())
        sellingPrice = TextFieldValue(item.sellingPrice.toString())
        selectedExistingItemId = item.id
        showItemCodeSuggestions = false
        itemCodeSuggestions = emptyList()
    }

    fun savePurchase() {
        if (selectedSupplier == null || selectedInvoice == null || purchaseItems.isEmpty()) {
            return
        }

        isSaving = true
        scope.launch {
            var success = false
            var errorMessage = ""
            val isUpdate = selectedInvoice!!.id != 0

            try {
                val responseCode = withContext(Dispatchers.IO) {
                    // Build invoice details array - matching web frontend structure exactly
                    val invoiceDetailsArray = JsonArray()
                    purchaseItems.forEachIndexed { index, item ->
                        val detailObj = JsonObject()
                        detailObj.addProperty("index", index)
                        detailObj.addProperty("itemId", item.id)
                        detailObj.addProperty("itemName", item.name)
                        detailObj.addProperty("itemCode", item.code)
                        detailObj.addProperty("unitCost", item.unitCost)
                        detailObj.addProperty("sellingPrice", item.sellingPrice)
                        detailObj.addProperty("qty", item.qty)
                        detailObj.addProperty("totalCost", item.total)
                        detailObj.addProperty("discountForUnitCost", 0)
                        detailObj.addProperty("unitCostAfterDiscount", item.unitCost)
                        detailObj.add("sellingPrice2", null)
                        detailObj.add("sellingPrice3", null)
                        detailObj.add("noOfWarningDays", null)
                        detailObj.addProperty("barcode", item.barcode ?: "")
                        detailObj.addProperty("defaultDiscountForPrice", item.defaultDiscountForPrice)
                        invoiceDetailsArray.add(detailObj)
                    }

                    // Build main invoice object - matching web frontend structure exactly
                    val invoiceData = JsonObject()
                    invoiceData.addProperty("shopId", 1)
                    invoiceData.addProperty("invoiceName", selectedInvoice!!.invoiceName?.trim())
                    invoiceData.add("invoiceDetails", invoiceDetailsArray)
                    invoiceData.addProperty("totalValue", calculateTotal())
                    invoiceData.addProperty("totalItems", purchaseItems.size)
                    invoiceData.addProperty("supplierId", selectedSupplier!!.id)
                    invoiceData.addProperty("invoiceDate", getCurrentDate())

                    // Add id for update
                    if (isUpdate) {
                        invoiceData.addProperty("id", selectedInvoice!!.id)
                    }

                    // Build userEvent object - matching web frontend structure
                    val eventAction = if (isUpdate) "Update Invoice" else "Save Invoice"
                    val userEvent = JsonObject()
                    userEvent.addProperty("event", "$eventAction, supplier : [${selectedSupplier!!.name}], invoice : [${selectedInvoice!!.invoiceName}]")
                    userEvent.addProperty("latitude", "0")
                    userEvent.addProperty("longitude", "0")
                    userEvent.addProperty("userName", "")
                    userEvent.addProperty("eventTime", "")
                    userEvent.add("mapLocations", null)

                    // Build payload: { userEvent: {...}, invoice: {...} }
                    val payload = JsonObject()
                    payload.add("userEvent", userEvent)
                    payload.add("invoice", invoiceData)

                    // Use different endpoint for save vs update
                    val endpoint = if (isUpdate) {
                        "${ApiConfig.BASE_URL_8080}/invoice/updateInvoice"
                    } else {
                        "${ApiConfig.BASE_URL_8080}/invoice/saveInvoice"
                    }

                    val connection = URL(endpoint)
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000

                    val jsonString = payload.toString()
                    android.util.Log.d("PurchasingScreen", "Sending to $endpoint: $jsonString")

                    connection.outputStream.bufferedWriter().use { it.write(jsonString) }

                    val code = connection.responseCode
                    if (code != 200 && code != 201) {
                        // Try to read error message
                        try {
                            val errorStream = connection.errorStream
                            if (errorStream != null) {
                                errorMessage = errorStream.bufferedReader().use { it.readText() }
                                android.util.Log.e("PurchasingScreen", "Error response: $errorMessage")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PurchasingScreen", "Error reading error stream", e)
                        }
                    } else {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        android.util.Log.d("PurchasingScreen", "Success response: $response")
                    }
                    code
                }

                success = responseCode == 200 || responseCode == 201

                if (success) {
                    // Success - reset form on main thread
                    purchaseItems = emptyList()
                    selectedSupplier = null
                    selectedInvoice = null
                    supplierInput = ""
                    invoiceInput = ""
                    isNewInvoice = false
                    saveMessage = if (isUpdate) "Purchase updated successfully!" else "Purchase saved successfully!"
                    isSuccessMessage = true
                } else {
                    saveMessage = "Error: $errorMessage"
                    isSuccessMessage = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("PurchasingScreen", "Exception saving purchase", e)
                saveMessage = "Error: ${e.message}"
                isSuccessMessage = false
            } finally {
                isSaving = false
            }

            // Show snackbar
            saveMessage?.let {
                snackbarHostState.showSnackbar(it)
                saveMessage = null
            }
        }
    }

    fun jsonObjectOf(vararg pairs: Pair<String, Any?>): JsonObject {
        val obj = JsonObject()
        pairs.forEach { (key, value) ->
            when (value) {
                is String -> obj.addProperty(key, value)
                is Number -> obj.addProperty(key, value)
                is Boolean -> obj.addProperty(key, value)
                is JsonObject -> obj.add(key, value)
                is List<*> -> {
                    val array = JsonArray()
                    value.forEach { item ->
                        when (item) {
                            is JsonObject -> array.add(item)
                            is String -> array.add(item)
                            is Number -> array.add(item)
                            is Boolean -> array.add(item)
                            else -> array.add(item?.toString())
                        }
                    }
                    obj.add(key, array)
                }
                null -> obj.addProperty(key, null as String?)
                else -> obj.addProperty(key, value.toString())
            }
        }
        return obj
    }

    if (isLoading || isLoadingSuppliers) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F3460)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFE94560))
        }
        return
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (isSuccessMessage) Color(0xFF4CAF50) else Color(0xFFF44336),
                    contentColor = Color.White
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Purchasing", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F3460))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Supplier Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Supplier", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = supplierInput,
                                onValueChange = { filterSuppliers(it) },
                                label = { Text("Select Supplier") },
                                placeholder = { Text("Type supplier name...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                singleLine = true
                            )

                            if (expandedSupplier && filteredSuppliers.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .background(Color(0xFF133C55))
                                ) {
                                    Column {
                                        filteredSuppliers.forEachIndexed { index, supplier ->
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSelectSupplier(supplier)
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = supplier.name ?: "Unknown",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (!supplier.phone.isNullOrEmpty()) {
                                                    Text(
                                                        text = "Tel: ${supplier.phone}",
                                                        color = Color(0xFFB0B0B0),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            if (index < filteredSuppliers.size - 1) {
                                                HorizontalDivider(
                                                    color = Color(0xFF0F3460),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedSupplier != null) {
                            Text(
                                "Selected: ${selectedSupplier!!.name}",
                                color = Color(0xFFB0B0B0),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Invoice Selection
            if (selectedSupplier != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Invoice", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            if (isLoadingInvoices) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(16.dp),
                                    color = Color(0xFFE94560)
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    TextField(
                                        value = invoiceInput,
                                        onValueChange = { filterInvoices(it) },
                                        label = { Text("Select or Enter Invoice") },
                                        placeholder = { Text("Type invoice name...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        singleLine = true
                                    )

                                    if ((expandedInvoice && filteredInvoices.isNotEmpty()) || isNewInvoice) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 250.dp)
                                                .background(Color(0xFF133C55)),
                                            color = Color(0xFF133C55)
                                        ) {
                                            Column {
                                                // Show existing invoices
                                                if (filteredInvoices.isNotEmpty()) {
                                                    LazyColumn {
                                                        items(filteredInvoices.size) { index ->
                                                            val invoice = filteredInvoices[index]
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable {
                                                                        onSelectInvoice(invoice)
                                                                    }
                                                                    .padding(12.dp)
                                                            ) {
                                                                Text(
                                                                    text = invoice.invoiceName ?: "Unknown",
                                                                    color = Color.White,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                            if (index < filteredInvoices.size - 1) {
                                                                HorizontalDivider(
                                                                    color = Color(0xFF0F3460),
                                                                    thickness = 0.5.dp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Show "Create New Invoice" option
                                                if (isNewInvoice) {
                                                    if (filteredInvoices.isNotEmpty()) {
                                                        HorizontalDivider(
                                                            color = Color(0xFF0F3460),
                                                            thickness = 0.5.dp
                                                        )
                                                    }
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                onCreateNewInvoice(newInvoiceName)
                                                            }
                                                            .padding(12.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = "+ Create New Invoice: \"$newInvoiceName\"",
                                                            color = Color(0xFF4CAF50),
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedInvoice != null) {
                                Text(
                                    "Selected: ${selectedInvoice!!.invoiceName}",
                                    color = Color(0xFFB0B0B0),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Items Section
            if (selectedSupplier != null && selectedInvoice != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            if (isLoadingInvoiceDetails) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(16.dp),
                                    color = Color(0xFFE94560)
                                )
                            } else {
                                Button(
                                    onClick = {
                                        if (config?.itemCodeAutoGenerateInPurchasing == "Y") {
                                            itemCode = TextFieldValue(generateNextItemCode())
                                        }
                                        showItemModal = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text("+ Add Item")
                                }

                                if (purchaseItems.isNotEmpty()) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        purchaseItems.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF133C55))
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${item.code} - ${item.name}", color = Color.White, fontSize = 11.sp)
                                                    Text("Cost: ${item.unitCost} x${item.qty} = ${item.total}", color = Color(0xFFB0B0B0), fontSize = 9.sp)
                                                }
                                                Row(
                                                    modifier = Modifier.padding(start = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            editingItem = item
                                                            itemName = TextFieldValue(item.name ?: "")
                                                            itemCode = TextFieldValue(item.code ?: "")
                                                            unitCost = TextFieldValue(item.unitCost.toString())
                                                            sellingPrice = TextFieldValue(item.sellingPrice.toString())
                                                            quantity = TextFieldValue(item.qty.toString())
                                                            barcode = TextFieldValue(item.barcode ?: "")
                                                            defaultDiscountForPrice = TextFieldValue(
                                                                if (item.defaultDiscountForPrice != 0.0) item.defaultDiscountForPrice.toString() else ""
                                                            )
                                                            selectedExistingItemId = item.id
                                                            showItemModal = true
                                                        },
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .width(50.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                                        contentPadding = PaddingValues(4.dp)
                                                    ) {
                                                        Text("Edit", fontSize = 10.sp)
                                                    }
                                                    Button(
                                                        onClick = { deleteItem(item.id) },
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .width(50.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                                        contentPadding = PaddingValues(4.dp)
                                                    ) {
                                                        Text("Delete", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF007BFF))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total:", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("LKR ${String.format("%.2f", calculateTotal())}", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Save Button
            if (purchaseItems.isNotEmpty()) {
                item {
                    Button(
                        onClick = { savePurchase() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isSaving && selectedSupplier != null && selectedInvoice != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedInvoice?.id != 0) Color(0xFF007AFF) else Color(0xFF34C759)
                        )
                    ) {
                        Text(
                            if (isSaving) "Saving..." else if (selectedInvoice?.id != 0) "Update Purchase" else "Save Purchase",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Item Modal Dialog with Autocomplete
    if (showItemModal) {
        Dialog(
            onDismissRequest = { showItemModal = false },
            properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(Color(0xFF1A1A2E)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        if (editingItem != null) "Edit Item" else "Add Item",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Item Name with Autocomplete
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = itemName,
                            onValueChange = { onItemNameChange(it) },
                            label = { Text("Item Name") },
                            placeholder = { Text("Type to search...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        if (showItemNameSuggestions && itemNameSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .background(Color(0xFF133C55)),
                                color = Color(0xFF133C55)
                            ) {
                                LazyColumn {
                                    items(itemNameSuggestions.size) { index ->
                                        val item = itemNameSuggestions[index]
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectItemByName(item)
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = item.name ?: "Unknown",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Code: ${item.code}",
                                                color = Color(0xFFB0B0B0),
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (index < itemNameSuggestions.size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFF0F3460),
                                                thickness = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Item Code with Autocomplete
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = itemCode,
                            onValueChange = { onItemCodeChange(it) },
                            label = { Text("Item Code") },
                            placeholder = { Text("Type to search...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        if (showItemCodeSuggestions && itemCodeSuggestions.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                                    .background(Color(0xFF133C55)),
                                color = Color(0xFF133C55)
                            ) {
                                LazyColumn {
                                    items(itemCodeSuggestions.size) { index ->
                                        val item = itemCodeSuggestions[index]
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectItemByCode(item)
                                                }
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = item.code ?: "Unknown",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "Name: ${item.name}",
                                                color = Color(0xFFB0B0B0),
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (index < itemCodeSuggestions.size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFF0F3460),
                                                thickness = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Unit Cost
                    TextField(
                        value = unitCost,
                        onValueChange = { unitCost = it },
                        label = { Text("Unit Cost (LKR)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    // Selling Price
                    TextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("Selling Price (LKR)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    // Default Discount For Price (config-gated)
                    if (config?.setDefaultDiscountForPrice == "Y") {
                        TextField(
                            value = defaultDiscountForPrice,
                            onValueChange = { defaultDiscountForPrice = it },
                            label = { Text("Default Discount For Price") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    // Quantity
                    TextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Barcode (config-gated)
                    if (config?.showBarcode == "Y") {
                        TextField(
                            value = barcode,
                            onValueChange = { barcode = it },
                            label = { Text("Barcode") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )
                    }

                    // Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showItemModal = false
                                editingItem = null
                                itemName = TextFieldValue("")
                                itemCode = TextFieldValue("")
                                unitCost = TextFieldValue("")
                                sellingPrice = TextFieldValue("")
                                quantity = TextFieldValue("")
                                barcode = TextFieldValue("")
                                defaultDiscountForPrice = TextFieldValue("")
                                selectedExistingItemId = 0
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666))
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = { addOrUpdateItem() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) {
                            Text(if (editingItem != null) "Update" else "Add", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchPurchasingConfig(context: Context, token: String): com.pos.cosmos.models.Config? {
    if (com.pos.cosmos.utils.AppSettings.get(context).useLocalDatabase.value) {
        return try {
            val rows = com.pos.cosmos.local.CosmosDatabase.get(context).billingConfigDao().all()
            val json = rows.firstOrNull { it.key == "config" }?.value ?: return null
            Gson().fromJson(json, com.pos.cosmos.models.Config::class.java)
        } catch (e: Exception) {
            android.util.Log.e("PurchasingScreen", "local fetchConfig failed", e)
            null
        }
    }
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
            Gson().fromJson(content, com.pos.cosmos.models.Config::class.java)
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("PurchasingScreen", "remote fetchConfig failed", e)
        null
    }
}
