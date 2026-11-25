package com.pos.cosmos.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.pos.cosmos.api.ApiConfig

// Card colours - the list sits on a dark background, so every Text inside the
// white cards needs an explicit colour. Without one it inherits LocalContentColor
// (near-white under the dark theme) and disappears against the white card.
private val CardTextPrimary = Color(0xFF1A1A2E)
private val CardTextSecondary = Color(0xFF5A5A6E)
private val PriceGreen = Color(0xFF2E7D32)

data class Item(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    // Hide For New Orders - item stays active but is left out of new bills / orders.
    val hideForNewOrders: Boolean = false,
    // Full server payload for this item. /item/saveItem replaces the whole row,
    // so we post back everything we received and only patch the edited fields.
    val raw: JsonObject = JsonObject()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllItemsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var allItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var editingItem by remember { mutableStateOf<Item?>(null) }
    var savingItem by remember { mutableStateOf(false) }
    // Config: Order Form Settings > Enable Hide Items for New Orders
    var enableHideItemsForNewOrders by remember { mutableStateOf(false) }

    val token by appViewModel.token.collectAsState()
    val userPermission by appViewModel.userPermission.collectAsState()
    val canUpdate = userPermission?.updateItems == "Y"
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            allItems = withContext(Dispatchers.IO) { fetchItems(token!!) }
            enableHideItemsForNewOrders = withContext(Dispatchers.IO) { fetchHideItemsForNewOrdersFlag(token!!) }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Items", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F3460))
        ) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search items...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = CardTextPrimary,
                    unfocusedTextColor = CardTextPrimary,
                    cursorColor = CardTextPrimary,
                    focusedLabelColor = CardTextSecondary,
                    unfocusedLabelColor = CardTextSecondary,
                    focusedLeadingIconColor = CardTextSecondary,
                    unfocusedLeadingIconColor = CardTextSecondary
                )
            )

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFE94560))
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            showHiddenBadge = enableHideItemsForNewOrders,
                            onEdit = {
                                if (canUpdate) {
                                    editingItem = item
                                } else {
                                    Toast.makeText(
                                        context,
                                        "You do not have permission to update items",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            saving = savingItem,
            showHideForNewOrders = enableHideItemsForNewOrders,
            onDismiss = { editingItem = null },
            onSave = { code, name, sellingPrice, hideForNewOrders ->
                scope.launch {
                    savingItem = true
                    val result = withContext(Dispatchers.IO) {
                        updateItem(token, item, code, name, sellingPrice, hideForNewOrders)
                    }
                    savingItem = false
                    if (result.success) {
                        allItems = allItems.map {
                            if (it.id == item.id) {
                                it.copy(
                                    code = code,
                                    name = name,
                                    sellingPrice = sellingPrice,
                                    hideForNewOrders = hideForNewOrders,
                                    raw = it.raw.deepCopy().apply {
                                        addProperty("code", code)
                                        addProperty("name", name)
                                        addProperty("sellingPrice", sellingPrice)
                                        addProperty("hideForNewOrders", hideForNewOrders)
                                    }
                                )
                            } else {
                                it
                            }
                        }
                        editingItem = null
                        Toast.makeText(context, "Item is updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun ItemCard(item: Item, onEdit: () -> Unit, showHiddenBadge: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = CardTextPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CardTextPrimary
                )
                if (showHiddenBadge && item.hideForNewOrders) {
                    Text(
                        "Hidden for new orders",
                        fontSize = 11.sp,
                        color = Color(0xFFE94560),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Code: ${item.code}", fontSize = 12.sp, color = CardTextSecondary)
                    Text(
                        "Price: ${String.format("%.2f", item.sellingPrice)}",
                        fontSize = 12.sp,
                        color = PriceGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit ${item.name}",
                    tint = Color(0xFF0F3460)
                )
            }
        }
    }
}

@Composable
private fun EditItemDialog(
    item: Item,
    saving: Boolean,
    showHideForNewOrders: Boolean,
    onDismiss: () -> Unit,
    onSave: (code: String, name: String, sellingPrice: Double, hideForNewOrders: Boolean) -> Unit
) {
    var code by remember(item.id) { mutableStateOf(item.code) }
    var name by remember(item.id) { mutableStateOf(item.name) }
    var price by remember(item.id) { mutableStateOf(String.format("%.2f", item.sellingPrice)) }
    var hideForNewOrders by remember(item.id) { mutableStateOf(item.hideForNewOrders) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    var confirming by remember(item.id) { mutableStateOf(false) }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Confirm") },
            text = { Text("Are you sure to update?") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onSave(code.trim(), name.trim(), price.trim().toDouble(), hideForNewOrders)
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("No") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("Item Code") },
                    singleLine = true,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Item Name") },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it; error = null },
                    label = { Text("Selling Price") },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showHideForNewOrders) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = hideForNewOrders,
                            onCheckedChange = { hideForNewOrders = it },
                            enabled = !saving
                        )
                        Text("Hide for New Orders")
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color(0xFFE94560), fontSize = 12.sp)
                }
                if (saving) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val enteredPrice = price.trim().toDoubleOrNull()
                    error = when {
                        code.isBlank() -> "Item Code is required"
                        name.isBlank() -> "Item Name is required"
                        enteredPrice == null -> "Selling Price must be a number"
                        enteredPrice < 0 -> "Selling Price cannot be negative"
                        else -> null
                    }
                    if (error == null) confirming = true
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class UpdateResult(val success: Boolean, val message: String)

private fun updateItem(
    token: String?,
    item: Item,
    code: String,
    name: String,
    sellingPrice: Double,
    hideForNewOrders: Boolean
): UpdateResult {
    if (token == null) return UpdateResult(false, "Not logged in")

    return try {
        // Post the full item back - the backend replaces the whole row, so any
        // field we drop here (unit cost, barcode, discounts, ...) would be lost.
        val payload = item.raw.deepCopy()
        payload.addProperty("id", item.id)
        payload.addProperty("code", code)
        payload.addProperty("name", name)
        payload.addProperty("sellingPrice", sellingPrice)
        payload.addProperty("hideForNewOrders", hideForNewOrders)
        payload.add("userEvent", JsonObject().apply {
            addProperty("event", "Update Item $name")
            addProperty("latitude", "0")
            addProperty("longitude", "0")
            addProperty("userName", "")
            addProperty("eventTime", "")
            add("mapLocations", null)
        })

        val connection = URL("${ApiConfig.BASE_URL_8080}/item/saveItem")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
            UpdateResult(true, "Item is updated")
        } else {
            // ItemException (duplicate code / name / barcode) comes back as a 400
            // with the message as the plain-text body.
            val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            UpdateResult(false, body.ifBlank { "Update failed ($responseCode)" })
        }
    } catch (e: Exception) {
        e.printStackTrace()
        UpdateResult(false, e.message ?: "Update failed")
    }
}

private fun fetchItems(token: String): List<Item> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JsonParser.parseString(content) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: return@mapNotNull null
                    Item(
                        id = id,
                        code = obj.get("code")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                        sellingPrice = obj.get("sellingPrice")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                        hideForNewOrders = obj.get("hideForNewOrders")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                        raw = obj
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/** Order Form Settings > Enable Hide Items for New Orders (dealer-service /admin/getConfigsByToken). */
private fun fetchHideItemsForNewOrdersFlag(token: String): Boolean {
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
            val obj = JsonParser.parseString(content).asJsonObject
            obj.get("enableHideItemsForNewOrders")?.takeIf { !it.isJsonNull }?.asString == "Y"
        } else false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
