package com.pos.cosmos.screens

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.net.Uri
import android.util.Base64
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.annotation.OptIn as AnnotationOptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.input.pointer.pointerInput
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlin.math.abs
import com.pos.cosmos.utils.BitmapUtils
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.pos.cosmos.api.ApiConfig

data class StockAdjustmentItem(
    val id: Long = 0,
    val itemId: Int = 0,
    val itemCode: String = "",
    val itemName: String = "",
    val barcode: String = "",
    val supplierId: Long = 0,
    val supplierName: String = "",
    val unitCost: Double = 0.0,
    val totalQty: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val stockInvoiceItems: List<Any> = emptyList()
)

data class SimpleStockItem(
    val itemId: String = "",
    val itemCode: String = "",
    val itemName: String = "",
    val barcode: String = ""
)

private data class ItemMasterData(
    val master: Map<Int, Map<String, String>>,
    val names: Set<String>,
    val codes: Set<String>,
    val barcodes: Set<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<SimpleStockItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<SimpleStockItem>>(emptyList()) }
    var selectedItem by remember { mutableStateOf<StockAdjustmentItem?>(null) }
    var adjustmentQty by remember { mutableStateOf(TextFieldValue("")) }
    var unitCost by remember { mutableStateOf(TextFieldValue("")) }
    var sellingPrice by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(false) }
    var isLoadingItems by remember { mutableStateOf(true) }
    var stockItems by remember { mutableStateOf<List<StockAdjustmentItem>>(emptyList()) }
    var allItemsMaster by remember { mutableStateOf<Map<Int, Map<String, String>>>(emptyMap()) }
    var message by remember { mutableStateOf("") }
    var messageType by remember { mutableStateOf("") } // "success" or "error"
    var isNewItemMode by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf(TextFieldValue("")) }
    var newItemCode by remember { mutableStateOf(TextFieldValue("")) }
    var newItemBarcode by remember { mutableStateOf(TextFieldValue("")) }
    // Inline validation errors for new-item mode
    var newItemNameError by remember { mutableStateOf<String?>(null) }
    var newItemCodeError by remember { mutableStateOf<String?>(null) }
    var newItemBarcodeError by remember { mutableStateOf<String?>(null) }
    // Lowercased lookup sets for duplicate detection (populated from /item/getAllItems)
    var existingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var existingCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var existingBarcodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showScanner by remember { mutableStateOf(false) }
    var showNewItemScanner by remember { mutableStateOf(false) }
    var showTextScanner by remember { mutableStateOf(false) }
    var showItemCounter by remember { mutableStateOf(false) }

    // Item image state
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var itemImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var imageCacheBuster by remember { mutableStateOf(System.currentTimeMillis()) }

    val token by appViewModel.token.collectAsState()
    val mobileUiConfig by appViewModel.mobileUiConfig.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Camera launcher for item photo
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            selectedImageUri = cameraPhotoUri
            itemImageUrl = null
            try {
                val inputStream = context.contentResolver.openInputStream(cameraPhotoUri!!)
                selectedImageBitmap = BitmapUtils.decodeSampled(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Gallery launcher for item photo
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            itemImageUrl = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                selectedImageBitmap = BitmapUtils.decodeSampled(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createItemImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchItemCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createItemImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun clearImageState() {
        selectedImageBitmap = null
        selectedImageUri = null
        itemImageUrl = null
    }

    suspend fun attachImageToItem(itemId: Int): Boolean {
        val uri = selectedImageUri ?: return true
        if (itemId <= 0) return false
        val cc = mobileUiConfig.companyCode
        isUploadingImage = true
        return try {
            withContext(Dispatchers.IO) {
                // 1. Persist the image URL onto the Item entity (mirrors web app flow).
                //    Without this, list views that read item.image won't show the photo.
                val itemPersisted = persistItemImageUrl(itemId, cc, token ?: "")
                if (!itemPersisted) {
                    android.util.Log.w("StockAdjust", "saveItem failed for itemId=$itemId — uploading file anyway")
                }
                // 2. Upload the actual JPEG file.
                uploadItemImageToServer(context, uri, itemId, token ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            isUploadingImage = false
        }
    }

    fun handleBarcodeResult(barcode: String) {
        showScanner = false
        val matched = items.find { it.barcode == barcode || it.itemCode == barcode }
        if (matched != null) {
            searchQuery = matched.itemName
            val matchedStockItem = stockItems.find { it.itemId == matched.itemId.toIntOrNull() ?: 0 }
            selectedItem = matchedStockItem
            val qty = matchedStockItem?.totalQty ?: 0.0
            adjustmentQty = TextFieldValue(((Math.round(qty * 1000) / 1000.0)).toString())
            unitCost = TextFieldValue(matchedStockItem?.unitCost?.toString() ?: "0")
            sellingPrice = TextFieldValue(matchedStockItem?.sellingPrice?.toString() ?: "0")
            filteredItems = emptyList()

            // Load existing item image
            selectedImageBitmap = null
            selectedImageUri = null
            val cc = mobileUiConfig.companyCode
            itemImageUrl = if (matchedStockItem != null && cc > 0) {
                "https://cosmospos.com/static/company_${cc}/item_${matchedStockItem.itemId}.jpeg?t=$imageCacheBuster"
            } else null

            // Fetch last invoice selling price
            if (matchedStockItem != null) {
                scope.launch {
                    try {
                        val lastInvoice = withContext(Dispatchers.IO) {
                            try {
                                val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getLastInvoiceByItemId?itemId=${matchedStockItem.itemId}")
                                    .openConnection() as HttpURLConnection
                                connection.requestMethod = "GET"
                                connection.setRequestProperty("Authorization", "Bearer $token")
                                connection.setRequestProperty("Content-Type", "application/json")
                                connection.connectTimeout = 30000
                                connection.readTimeout = 30000
                                if (connection.responseCode == 200) {
                                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                                    Gson().fromJson(content, Map::class.java) as? Map<String, Any>
                                } else null
                            } catch (e: Exception) { null }
                        }
                        if (lastInvoice != null) {
                            val sp = (lastInvoice["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                            val uc = (lastInvoice["unitCost"] as? Number)?.toDouble() ?: 0.0
                            if (sp > 0) sellingPrice = TextFieldValue(sp.toString())
                            if (uc > 0) unitCost = TextFieldValue(uc.toString())
                        }
                    } catch (_: Exception) {}
                }
            }

            message = "Item found: ${matched.itemName}"
            messageType = "success"
        } else {
            message = "No item found for barcode: $barcode"
            messageType = "error"
        }
    }

    fun toggleNewItemMode() {
        isNewItemMode = !isNewItemMode
        selectedItem = null
        searchQuery = ""
        adjustmentQty = TextFieldValue("")
        unitCost = TextFieldValue("")
        sellingPrice = TextFieldValue("")
        newItemName = TextFieldValue("")
        newItemCode = TextFieldValue("")
        newItemBarcode = TextFieldValue("")
        newItemNameError = null
        newItemCodeError = null
        newItemBarcodeError = null
        showNewItemScanner = false
        showTextScanner = false
        message = ""
        messageType = ""
        filteredItems = emptyList()
        clearImageState()
    }

    fun fetchAllItems() {
        scope.launch {
            isLoadingItems = true
            try {
                // Fetch both stock items and item master in parallel
                val result = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL("${ApiConfig.BASE_URL_8080}/stock/getAllNonZeroNestedStockItems")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            try {
                                val itemList = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>
                                itemList?.map { item ->
                                    StockAdjustmentItem(
                                        id = (item["id"] as? Number)?.toLong() ?: 0,
                                        itemId = (item["itemId"] as? Number)?.toInt() ?: 0,
                                        itemCode = item["itemCode"]?.toString() ?: "",
                                        itemName = item["itemName"]?.toString() ?: "",
                                        supplierId = (item["supplierId"] as? Number)?.toLong() ?: 0,
                                        supplierName = item["supplier"]?.toString() ?: "",
                                        unitCost = (item["unitCost"] as? Number)?.toDouble() ?: 0.0,
                                        totalQty = (item["totalQty"] as? Number)?.toDouble() ?: 0.0,
                                        sellingPrice = (item["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                                    )
                                } ?: emptyList()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }

                // Fetch item master for barcode data + duplicate-detection lookup sets
                val masterFetch = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val itemList = Gson().fromJson(content, List::class.java) as? List<Map<String, Any>>
                            val map = mutableMapOf<Int, Map<String, String>>()
                            val names = mutableSetOf<String>()
                            val codes = mutableSetOf<String>()
                            val barcodes = mutableSetOf<String>()
                            itemList?.forEach { item ->
                                val id = (item["id"] as? Number)?.toInt() ?: 0
                                if (id > 0) {
                                    val itemBarcode = item["barcode"]?.toString() ?: ""
                                    val itemCode = item["code"]?.toString() ?: ""
                                    val itemName = item["name"]?.toString() ?: ""
                                    map[id] = mapOf(
                                        "barcode" to itemBarcode,
                                        "code" to itemCode
                                    )
                                    if (itemName.isNotBlank()) names += itemName.trim().lowercase()
                                    if (itemCode.isNotBlank()) codes += itemCode.trim().lowercase()
                                    if (itemBarcode.isNotBlank()) barcodes += itemBarcode.trim().lowercase()
                                }
                            }
                            ItemMasterData(map, names, codes, barcodes)
                        } else ItemMasterData(emptyMap(), emptySet(), emptySet(), emptySet())
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ItemMasterData(emptyMap(), emptySet(), emptySet(), emptySet())
                    }
                }

                val masterMap = masterFetch.master
                allItemsMaster = masterMap
                existingNames = masterFetch.names
                existingCodes = masterFetch.codes
                existingBarcodes = masterFetch.barcodes

                // Merge barcode from item master into stock items
                stockItems = result.map { item ->
                    val master = masterMap[item.itemId]
                    item.copy(barcode = master?.get("barcode") ?: "")
                }

                items = stockItems.map { item ->
                    SimpleStockItem(
                        itemId = item.itemId.toString(),
                        itemCode = item.itemCode,
                        itemName = item.itemName,
                        barcode = item.barcode
                    )
                }
            } catch (e: Exception) {
                println("Error in fetchAllItems: ${e.message}")
                e.printStackTrace()
            } finally {
                isLoadingItems = false
            }
        }
    }

    fun handleItemSelect(item: SimpleStockItem) {
        val matchedStockItem = stockItems.find { it.itemId == item.itemId.toIntOrNull() ?: 0 }
        selectedItem = matchedStockItem
        val qty = matchedStockItem?.totalQty ?: 0.0
        adjustmentQty = TextFieldValue(((Math.round(qty * 1000) / 1000.0)).toString())
        unitCost = TextFieldValue(matchedStockItem?.unitCost?.toString() ?: "0")
        sellingPrice = TextFieldValue(matchedStockItem?.sellingPrice?.toString() ?: "0")
        searchQuery = item.itemName
        filteredItems = emptyList()

        // Load existing item image (may 404 if not uploaded yet — AsyncImage handles gracefully)
        selectedImageBitmap = null
        selectedImageUri = null
        val cc = mobileUiConfig.companyCode
        itemImageUrl = if (matchedStockItem != null && cc > 0) {
            "https://cosmospos.com/static/company_${cc}/item_${matchedStockItem.itemId}.jpeg?t=$imageCacheBuster"
        } else null

        // Fetch last invoice selling price
        if (matchedStockItem != null) {
            scope.launch {
                try {
                    val lastInvoice = withContext(Dispatchers.IO) {
                        try {
                            val connection = URL("${ApiConfig.BASE_URL_8080}/invoice/getLastInvoiceByItemId?itemId=${matchedStockItem.itemId}")
                                .openConnection() as HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.setRequestProperty("Authorization", "Bearer $token")
                            connection.setRequestProperty("Content-Type", "application/json")
                            connection.connectTimeout = 30000
                            connection.readTimeout = 30000

                            if (connection.responseCode == 200) {
                                val content = connection.inputStream.bufferedReader().use { it.readText() }
                                Gson().fromJson(content, Map::class.java) as? Map<String, Any>
                            } else null
                        } catch (e: Exception) { null }
                    }
                    if (lastInvoice != null) {
                        val invoiceSellingPrice = (lastInvoice["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                        val invoiceUnitCost = (lastInvoice["unitCost"] as? Number)?.toDouble() ?: 0.0
                        if (invoiceSellingPrice > 0) {
                            sellingPrice = TextFieldValue(invoiceSellingPrice.toString())
                        }
                        if (invoiceUnitCost > 0) {
                            unitCost = TextFieldValue(invoiceUnitCost.toString())
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun handleUpdateStock() {
        // For new item mode: all fields are required (itemName, qty, unitCost, sellingPrice)
        if (isNewItemMode) {
            if (newItemName.text.isBlank()) {
                message = "Item name is required"
                messageType = "error"
                return
            }
            if (adjustmentQty.text.isBlank()) {
                message = "Quantity is required"
                messageType = "error"
                return
            }
            if (unitCost.text.isBlank()) {
                message = "Unit cost is required"
                messageType = "error"
                return
            }
            if (sellingPrice.text.isBlank()) {
                message = "Selling price is required"
                messageType = "error"
                return
            }
        } else {
            // For existing item: all fields required
            if (selectedItem == null || adjustmentQty.text.isBlank() || unitCost.text.isBlank() || sellingPrice.text.isBlank()) {
                return
            }
        }

        scope.launch {
            loading = true
            try {
                val resultPair = withContext(Dispatchers.IO) {
                    try {
                        // Create payload with correct types
                        val stockData = linkedMapOf<String, Any>()

                        if (isNewItemMode) {
                            // For new items, itemId = 0
                            stockData["itemId"] = 0
                            stockData["qty"] = adjustmentQty.text.toDoubleOrNull() ?: 0.0
                            stockData["unitCost"] = unitCost.text.toDoubleOrNull() ?: 0.0
                            stockData["sellingPrice"] = sellingPrice.text.toDoubleOrNull() ?: 0.0
                            stockData["itemName"] = newItemName.text.trim()
                            if (newItemCode.text.isNotBlank()) {
                                stockData["itemCode"] = newItemCode.text.trim()
                            }
                            if (newItemBarcode.text.isNotBlank()) {
                                stockData["barcode"] = newItemBarcode.text.trim()
                            }
                        } else {
                            // For existing items, use selected item data
                            stockData["itemId"] = selectedItem!!.itemId.toInt()  // Now directly use the numeric itemId
                            stockData["qty"] = adjustmentQty.text.toDoubleOrNull() ?: 0.0
                            stockData["unitCost"] = unitCost.text.toDoubleOrNull() ?: 0.0
                            stockData["sellingPrice"] = sellingPrice.text.toDoubleOrNull() ?: 0.0
                            stockData["itemName"] = selectedItem!!.itemName
                        }

                        val connection = URL("${ApiConfig.BASE_URL_8080}/stock/adjust-stock")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000
                        connection.doOutput = true

                        val gson = Gson()
                        val json = gson.toJson(stockData)
                        android.util.Log.d("StockAdjust", "Request JSON: $json")
                        connection.outputStream.bufferedWriter().use { it.write(json) }

                        val responseCode = connection.responseCode
                        val responseBody = if (responseCode != 200 && responseCode != 201) {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        } else {
                            connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                        }

                        android.util.Log.d("StockAdjust", "Response code=$responseCode body=$responseBody")

                        if (responseCode != 200 && responseCode != 201) {
                            println("Stock update error: Code=$responseCode, Response=$responseBody")
                            Pair(false, 0)
                        } else {
                            // Parse out the itemId from the StockItem response so we can attach an image
                            val returnedItemId = try {
                                val map = Gson().fromJson(responseBody, Map::class.java) as? Map<String, Any>
                                (map?.get("itemId") as? Number)?.toInt() ?: 0
                            } catch (_: Exception) { 0 }
                            Pair(true, returnedItemId)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Pair(false, 0)
                    }
                }

                val success = resultPair.first
                val returnedItemId = resultPair.second

                if (success) {
                    // Upload image if one was selected (camera or gallery)
                    var itemIdForUpload = if (returnedItemId > 0) returnedItemId else (selectedItem?.itemId ?: 0)
                    val hadImage = selectedImageUri != null

                    // /stock/adjust-stock returns an empty StockItem when creating a NEW item,
                    // so the itemId comes back as 0. Look it up by code/name from /item/getAllItems.
                    if (hadImage && itemIdForUpload <= 0 && isNewItemMode) {
                        val nameToFind = newItemName.text.trim()
                        val codeToFind = newItemCode.text.trim()
                        val barcodeToFind = newItemBarcode.text.trim()
                        itemIdForUpload = withContext(Dispatchers.IO) {
                            lookupItemId(token ?: "", nameToFind, codeToFind, barcodeToFind)
                        }
                        android.util.Log.d("StockAdjust", "Looked up new item id by name='$nameToFind' code='$codeToFind' -> $itemIdForUpload")
                    }

                    var imageUploaded = true
                    if (hadImage && itemIdForUpload > 0) {
                        android.util.Log.d("StockAdjust", "Attaching image for itemId=$itemIdForUpload (saveItem + uploadItem)")
                        imageUploaded = attachImageToItem(itemIdForUpload)
                        android.util.Log.d("StockAdjust", "Image attach result=$imageUploaded for itemId=$itemIdForUpload")
                    } else if (hadImage) {
                        android.util.Log.w("StockAdjust", "Image was attached but itemId could not be resolved — skipping upload")
                        imageUploaded = false
                    }

                    val baseMsg = if (isNewItemMode) "New item created successfully!" else "Stock updated successfully!"
                    val msg = when {
                        hadImage && imageUploaded -> "$baseMsg Image uploaded."
                        hadImage && !imageUploaded -> "$baseMsg (Image upload failed)"
                        else -> baseMsg
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    message = msg
                    messageType = if (imageUploaded) "success" else "error"

                    // Bump cache buster so the just-uploaded image bypasses any cached 404 from earlier
                    if (hadImage && imageUploaded) {
                        imageCacheBuster = System.currentTimeMillis()
                    }

                    searchQuery = ""
                    selectedItem = null
                    adjustmentQty = TextFieldValue("")
                    unitCost = TextFieldValue("")
                    sellingPrice = TextFieldValue("")
                    newItemName = TextFieldValue("")
                    newItemCode = TextFieldValue("")
                    newItemBarcode = TextFieldValue("")
                    filteredItems = emptyList()
                    clearImageState()
                    // Don't toggle mode - stay in current mode
                    fetchAllItems()
                } else {
                    message = if (isNewItemMode) "Failed to create new item. Please check the item details and try again." else "Failed to update stock. Please check the item details and try again."
                    messageType = "error"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            fetchAllItems()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Adjustment", color = Color.White) },
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
                .background(Color(0xFF0F3460)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with title and toggle button
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isNewItemMode) "Add New Item" else "Stock Adjustment",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { toggleNewItemMode() },
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6c757d)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        if (isNewItemMode) "Back" else "Add Item",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isNewItemMode) "Add New Item" else "Stock Information",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isLoadingItems && !isNewItemMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE94560))
                        }
                    } else if (isNewItemMode) {
                        // New Item Mode - show form to create new item
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                TextField(
                                    value = newItemName,
                                    onValueChange = {
                                        newItemName = it
                                        val v = it.text.trim().lowercase()
                                        newItemNameError = if (v.isNotEmpty() && v in existingNames) "An item with this name already exists" else null
                                    },
                                    label = { Text("Item Name *") },
                                    placeholder = { Text("Enter item name...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    isError = newItemNameError != null
                                )
                                Button(
                                    onClick = { showTextScanner = !showTextScanner },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showTextScanner) Color(0xFFE94560) else Color(0xFF6366F1)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        if (showTextScanner) "Close" else "Scan",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (newItemNameError != null) {
                                Text(
                                    text = newItemNameError!!,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                                )
                            }

                            if (showTextScanner) {
                                Spacer(modifier = Modifier.height(4.dp))
                                InlineTextScanner(
                                    onTextScanned = { text ->
                                        showTextScanner = false
                                        newItemName = TextFieldValue(text)
                                        val v = text.trim().lowercase()
                                        newItemNameError = if (v.isNotEmpty() && v in existingNames) "An item with this name already exists" else null
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            TextField(
                                value = newItemCode,
                                onValueChange = {
                                    newItemCode = it
                                    val v = it.text.trim().lowercase()
                                    newItemCodeError = if (v.isNotEmpty() && v in existingCodes) "An item with this code already exists" else null
                                },
                                label = { Text("Item Code") },
                                placeholder = { Text("Enter item code...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (newItemCodeError == null) 12.dp else 0.dp),
                                singleLine = true,
                                isError = newItemCodeError != null
                            )
                            if (newItemCodeError != null) {
                                Text(
                                    text = newItemCodeError!!,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                TextField(
                                    value = newItemBarcode,
                                    onValueChange = {
                                        newItemBarcode = it
                                        val v = it.text.trim().lowercase()
                                        newItemBarcodeError = if (v.isNotEmpty() && v in existingBarcodes) "An item with this barcode already exists" else null
                                    },
                                    label = { Text("Barcode") },
                                    placeholder = { Text("Enter barcode...") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    isError = newItemBarcodeError != null
                                )
                                Button(
                                    onClick = { showNewItemScanner = !showNewItemScanner },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showNewItemScanner) Color(0xFFE94560) else Color(0xFF6366F1)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        if (showNewItemScanner) "Close" else "Scan",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (newItemBarcodeError != null) {
                                Text(
                                    text = newItemBarcodeError!!,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                                )
                            }

                            if (showNewItemScanner) {
                                Spacer(modifier = Modifier.height(4.dp))
                                InlineBarcodeScanner(
                                    onBarcodeScanned = { barcode ->
                                        showNewItemScanner = false
                                        newItemBarcode = TextFieldValue(barcode)
                                        val v = barcode.trim().lowercase()
                                        newItemBarcodeError = if (v.isNotEmpty() && v in existingBarcodes) "An item with this barcode already exists" else null
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                TextField(
                                    value = adjustmentQty,
                                    onValueChange = { adjustmentQty = it },
                                    label = { Text("Initial Quantity") },
                                    placeholder = { Text("Default: 0") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Button(
                                    onClick = { showItemCounter = true },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text("Count", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            TextField(
                                value = unitCost,
                                onValueChange = { unitCost = it },
                                label = { Text("Unit Cost") },
                                placeholder = { Text("Default: 0") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            TextField(
                                value = sellingPrice,
                                onValueChange = { sellingPrice = it },
                                label = { Text("Selling Price") },
                                placeholder = { Text("Default: 0") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            ItemImagePicker(
                                selectedImageBitmap = selectedImageBitmap,
                                itemImageUrl = itemImageUrl,
                                isUploadingImage = isUploadingImage,
                                onCameraClick = { launchItemCamera() },
                                onGalleryClick = { galleryLauncher.launch("image/*") },
                                onRemoveClick = { clearImageState() }
                            )
                        }
                    } else {
                        // Existing Item Mode - show search and adjustment
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    filteredItems = if (it.isEmpty()) {
                                        emptyList()
                                    } else {
                                        val query = it.lowercase()
                                        items.filter { item ->
                                            item.itemName.lowercase().contains(query)
                                                || item.itemCode.lowercase().contains(query)
                                                || item.barcode.lowercase().contains(query)
                                        }
                                    }
                                },
                                label = { Text("Item Name") },
                                placeholder = { Text("Search by name, code or barcode...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = { showScanner = !showScanner },
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (showScanner) Color(0xFFE94560) else Color(0xFF6366F1)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    if (showScanner) "Close" else "Scan",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (showScanner) {
                            Spacer(modifier = Modifier.height(8.dp))
                            InlineBarcodeScanner(
                                onBarcodeScanned = { barcode ->
                                    showScanner = false
                                    handleBarcodeResult(barcode)
                                }
                            )
                        }

                        // Show dropdown list
                        if (filteredItems.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .background(Color(0xFF133C55)),
                                color = Color(0xFF133C55)
                            ) {
                                LazyColumn {
                                    items(filteredItems.size) { index ->
                                        val item = filteredItems[index]
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { handleItemSelect(item) }
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = item.itemName,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "Code: ${item.itemCode} | Barcode: ${item.barcode}",
                                                color = Color(0xFFB0B0B0),
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (index < filteredItems.size - 1) {
                                            Divider(
                                                color = Color(0xFF0F3460),
                                                thickness = 0.5.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Selected Item Details (for existing item mode)
                        if (selectedItem != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            ) {
                                Text(
                                    "Adjust Stock for ${selectedItem!!.itemName}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        "Code: ${selectedItem!!.itemCode}",
                                        color = Color(0xFFB0B0B0),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Barcode: ${selectedItem!!.barcode}",
                                        color = Color(0xFFB0B0B0),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Current Stock: ${selectedItem!!.totalQty}",
                                        color = Color(0xFFB0B0B0),
                                        fontSize = 12.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    TextField(
                                        value = adjustmentQty,
                                        onValueChange = { adjustmentQty = it },
                                        label = { Text("New Quantity") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    Button(
                                        onClick = { showItemCounter = true },
                                        modifier = Modifier.height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("Count", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextField(
                                    value = unitCost,
                                    onValueChange = {},
                                    label = { Text("Unit Cost") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    singleLine = true,
                                    enabled = false,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                TextField(
                                    value = sellingPrice,
                                    onValueChange = { sellingPrice = it },
                                    label = { Text("Selling Price") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                ItemImagePicker(
                                    selectedImageBitmap = selectedImageBitmap,
                                    itemImageUrl = itemImageUrl,
                                    isUploadingImage = isUploadingImage,
                                    onCameraClick = { launchItemCamera() },
                                    onGalleryClick = { galleryLauncher.launch("image/*") },
                                    onRemoveClick = { clearImageState() }
                                )
                            }
                        }
                    }

                    // Message Display
                    if (message.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (messageType == "success")
                                    Color(0xFF34C759).copy(alpha = 0.2f)
                                else
                                    Color(0xFFFF3B30).copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = message,
                                color = if (messageType == "success") Color(0xFF34C759) else Color(0xFFFF3B30),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

            // Save/Update Button - Always visible at bottom
            val isButtonEnabled = if (isNewItemMode) {
                // For new item: all fields required AND no duplicate-validation errors
                newItemName.text.isNotBlank() && adjustmentQty.text.isNotBlank() && unitCost.text.isNotBlank() && sellingPrice.text.isNotBlank()
                    && newItemNameError == null && newItemCodeError == null && newItemBarcodeError == null
            } else {
                // For existing item: all fields required
                selectedItem != null && adjustmentQty.text.isNotBlank() && unitCost.text.isNotBlank() && sellingPrice.text.isNotBlank()
            }

            Button(
                onClick = { handleUpdateStock() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(16.dp),
                enabled = isButtonEnabled && !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF007AFF),
                    disabledContainerColor = Color(0xFF6c757d)
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        if (isNewItemMode) "Create Item" else "Update Stock",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    // Barcode scanner is now inline (InlineBarcodeScanner), no full-screen overlay needed

    // Item Counter Overlay
    if (showItemCounter) {
        ItemCounterOverlay(
            onCountConfirmed = { count ->
                adjustmentQty = TextFieldValue(count.toString())
                showItemCounter = false
            },
            onDismiss = { showItemCounter = false }
        )
    }
}
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun InlineBarcodeScanner(
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
        ) {
            val previewView = remember { PreviewView(context) }
            var scanned by remember { mutableStateOf(false) }

            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            )

            DisposableEffect(previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val barcodeScanner = BarcodeScanning.getClient()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && !scanned) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val barcode = barcodes.firstOrNull()?.rawValue
                                    if (barcode != null && !scanned) {
                                        scanned = true
                                        onBarcodeScanned(barcode)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(context))

                onDispose {
                    try {
                        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                        cameraProvider.unbindAll()
                    } catch (_: Exception) {}
                }
            }

            Text(
                "Point at barcode",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    } else {
        Text(
            "Camera permission required",
            fontSize = 12.sp,
            color = Color.Red,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun InlineTextScanner(
    onTextScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        if (capturedBitmap == null) {
            // Step 1: Camera preview with capture button
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    val previewView = remember {
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    }
                    val imageCapture = remember { ImageCapture.Builder().build() }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    )

                    DisposableEffect(previewView) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(context))

                        onDispose {
                            try {
                                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                                cameraProvider.unbindAll()
                            } catch (_: Exception) {}
                        }
                    }

                    // Capture button
                    Button(
                        onClick = {
                            isProcessing = true
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        try {
                                            val buffer = imageProxy.planes[0].buffer
                                            val bytes = ByteArray(buffer.remaining())
                                            buffer.get(bytes)
                                            val bmp = BitmapUtils.decodeSampled(bytes)
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            // rotateAndRecycle/cropCentreStripAndRecycle free each
                                            // intermediate immediately — the old code kept the
                                            // decoded, rotated and cropped copies alive at once,
                                            // which is three full-size bitmaps on the heap.
                                            val rotated = bmp?.let { BitmapUtils.rotateAndRecycle(it, rotation) }

                                            // Crop to exactly match the preview viewport (FILL_CENTER):
                                            // the preview shows a centered horizontal strip whose
                                            // height/width ratio equals the PreviewView's ratio.
                                            val cropped = rotated?.let {
                                                val pvW = previewView.width.coerceAtLeast(1)
                                                val pvH = previewView.height.coerceAtLeast(1)
                                                BitmapUtils.cropCentreStripAndRecycle(it, pvH.toFloat() / pvW.toFloat())
                                            }

                                            if (cropped != null) {
                                                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                                val inputImage = InputImage.fromBitmap(cropped, 0)
                                                textRecognizer.process(inputImage)
                                                    .addOnSuccessListener { result ->
                                                        val lines = result.textBlocks
                                                            .flatMap { it.lines }
                                                            .map { it.text.trim() }
                                                            .filter { it.isNotBlank() }
                                                        detectedLines = lines
                                                        capturedBitmap = cropped
                                                        isProcessing = false
                                                    }
                                                    .addOnFailureListener {
                                                        capturedBitmap = cropped
                                                        detectedLines = emptyList()
                                                        isProcessing = false
                                                    }
                                            } else {
                                                isProcessing = false
                                            }
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                        isProcessing = false
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .height(26.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        enabled = !isProcessing
                    ) {
                        Text(
                            if (isProcessing) "..." else "Capture",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Step 2: Show captured image and detected text lines for selection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E), shape = RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                // Show captured image
                Image(
                    bitmap = capturedBitmap!!.asImageBitmap(),
                    contentDescription = "Captured image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black, shape = RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (detectedLines.isEmpty()) {
                    Text(
                        "No text detected. Try again.",
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Text(
                        "Tap a line to use as item name:",
                        color = Color(0xFFB0B0B0),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    detectedLines.forEach { line ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onTextScanned(line) },
                            color = Color(0xFF2A2A4A),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = line,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Retake button
                Button(
                    onClick = {
                        capturedBitmap = null
                        detectedLines = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Retake", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Text(
            "Camera permission required",
            fontSize = 12.sp,
            color = Color.Red,
            modifier = Modifier.padding(8.dp)
        )
    }
}

data class VisionBox(
    val name: String,
    val score: Float,
    val left: Float,   // normalized 0-1
    val top: Float,
    val right: Float,
    val bottom: Float
)

@AnnotationOptIn(ExperimentalGetImage::class)
@Composable
fun ItemCounterOverlay(
    onCountConfirmed: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) onDismiss()
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    if (!hasCameraPermission) return

    var detectedObjects by remember { mutableStateOf<List<VisionBox>>(emptyList()) }
    var selectedBox by remember { mutableStateOf<VisionBox?>(null) }
    var matchedCount by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var detecting by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("Point camera at items, then tap Detect") }
    var capturedImageRef = remember { mutableStateOf<ByteArray?>(null) }
    var tapMarkers by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isTapMode by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val apiKey = "AIzaSyCJJxAvWqQigfyD1VKLu0Nq4QdUs4KBH3Y"

    fun findSimilar(tapped: VisionBox): Int {
        val tappedW = tapped.right - tapped.left
        val tappedH = tapped.bottom - tapped.top
        val tappedRatio = tappedW / tappedH.coerceAtLeast(0.001f)
        val tappedArea = tappedW * tappedH

        return detectedObjects.count { box ->
            val w = box.right - box.left
            val h = box.bottom - box.top
            val ratio = w / h.coerceAtLeast(0.001f)
            val area = w * h
            // Same label OR similar shape
            val labelMatch = box.name == tapped.name && tapped.name != "Unknown"
            val ratioMatch = abs(ratio - tappedRatio) / tappedRatio.coerceAtLeast(0.001f) < 0.5f
            val areaMatch = abs(area - tappedArea) / tappedArea.coerceAtLeast(0.001f) < 0.6f
            labelMatch || (ratioMatch && areaMatch)
        }
    }

    fun isBoxMatched(box: VisionBox, selected: VisionBox): Boolean {
        val selW = selected.right - selected.left
        val selH = selected.bottom - selected.top
        val selRatio = selW / selH.coerceAtLeast(0.001f)
        val selArea = selW * selH
        val w = box.right - box.left
        val h = box.bottom - box.top
        val ratio = w / h.coerceAtLeast(0.001f)
        val area = w * h
        val labelMatch = box.name == selected.name && selected.name != "Unknown"
        val ratioMatch = abs(ratio - selRatio) / selRatio.coerceAtLeast(0.001f) < 0.5f
        val areaMatch = abs(area - selArea) / selArea.coerceAtLeast(0.001f) < 0.6f
        return labelMatch || (ratioMatch && areaMatch)
    }

    fun detectObjects(imageBytes: ByteArray) {
        detecting = true
        statusMsg = "Analyzing image..."
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    val requestBody = """
                        {"requests":[{"image":{"content":"$base64Image"},
                        "features":[{"type":"OBJECT_LOCALIZATION","maxResults":100}]}]}
                    """.trimIndent()

                    val connection = URL("https://vision.googleapis.com/v1/images:annotate?key=$apiKey")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.doOutput = true
                    connection.outputStream.bufferedWriter().use { it.write(requestBody) }

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        val content = connection.inputStream.bufferedReader().use { it.readText() }
                        val response = Gson().fromJson(content, Map::class.java) as Map<String, Any>
                        val responses = response["responses"] as? List<Map<String, Any>> ?: emptyList()
                        val firstResponse = responses.firstOrNull() ?: emptyMap()

                        // Check for API-level errors in response
                        val error = firstResponse["error"] as? Map<String, Any>
                        if (error != null) {
                            throw Exception("API: ${error["message"]}")
                        }

                        val annotations = firstResponse["localizedObjectAnnotations"] as? List<Map<String, Any>> ?: emptyList()

                        annotations.mapNotNull { ann ->
                            val name = ann["name"]?.toString() ?: "Unknown"
                            val score = (ann["score"] as? Number)?.toFloat() ?: 0f
                            val poly = ann["boundingPoly"] as? Map<String, Any> ?: return@mapNotNull null
                            val vertices = poly["normalizedVertices"] as? List<Map<String, Any>> ?: return@mapNotNull null
                            if (vertices.size < 4) return@mapNotNull null

                            val xs = vertices.map { (it["x"] as? Number)?.toFloat() ?: 0f }
                            val ys = vertices.map { (it["y"] as? Number)?.toFloat() ?: 0f }

                            VisionBox(
                                name = name,
                                score = score,
                                left = xs.min(),
                                top = ys.min(),
                                right = xs.max(),
                                bottom = ys.max()
                            )
                        }
                    } else {
                        val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        throw Exception("HTTP $responseCode: $err")
                    }
                }

                detectedObjects = result
                selectedBox = null
                matchedCount = 0
                count = 0
                statusMsg = "Found ${result.size} objects. Tap one to count similar."
            } catch (e: Exception) {
                e.printStackTrace()
                statusMsg = "Error: ${e.message}"
            } finally {
                detecting = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview or frozen image
        val previewView = remember { PreviewView(context) }

        if (isTapMode && frozenBitmap != null) {
            Image(
                bitmap = frozenBitmap!!.asImageBitmap(),
                contentDescription = "Captured image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        LaunchedEffect(previewView) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                // Capture frames for detection
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    try {
                        val bitmap = imageProxy.toBitmap()
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        capturedImageRef.value = out.toByteArray()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        // Draw bounding boxes with labels + tap markers
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detectedObjects, isTapMode) {
                    detectTapGestures { offset ->
                        if (isTapMode) {
                            // Tap-to-count mode: tap to add/remove markers
                            val removeIndex = tapMarkers.indexOfFirst { marker ->
                                val dx = marker.x - offset.x
                                val dy = marker.y - offset.y
                                (dx * dx + dy * dy) < (45f * 45f)
                            }
                            if (removeIndex >= 0) {
                                tapMarkers = tapMarkers.toMutableList().apply { removeAt(removeIndex) }
                                count--
                            } else {
                                tapMarkers = tapMarkers + offset
                                count++
                            }
                        } else {
                            // Detection mode: tap a detected box to count similar
                            val viewW = size.width.toFloat()
                            val viewH = size.height.toFloat()
                            val tapped = detectedObjects.find { box ->
                                val l = box.left * viewW
                                val t = box.top * viewH
                                val r = box.right * viewW
                                val b = box.bottom * viewH
                                offset.x in l..r && offset.y in t..b
                            }
                            if (tapped != null) {
                                selectedBox = tapped
                                matchedCount = findSimilar(tapped)
                                count = matchedCount
                            }
                        }
                    }
                }
        ) {
            val viewW = maxWidth
            val viewH = maxHeight

            // Show detected boxes (always visible)
            if (!isTapMode) {
                for (box in detectedObjects) {
                    val isSelected = selectedBox != null &&
                        selectedBox!!.left == box.left && selectedBox!!.top == box.top
                    val isMatched = selectedBox != null && isBoxMatched(box, selectedBox!!)

                    val borderColor = when {
                        isSelected -> Color(0xFF00BFFF)
                        isMatched -> Color(0xFF00FF00)
                        else -> Color(0xFFFFFF00).copy(alpha = 0.6f)
                    }
                    val borderW = if (isSelected || isMatched) 3.dp else 1.5.dp

                    Box(
                        modifier = Modifier
                            .offset(x = viewW * box.left, y = viewH * box.top)
                            .size(
                                width = viewW * (box.right - box.left),
                                height = viewH * (box.bottom - box.top)
                            )
                            .background(Color.Transparent)
                            .then(
                                Modifier.drawBehind {
                                    drawRect(
                                        color = borderColor,
                                        style = Stroke(width = borderW.toPx())
                                    )
                                }
                            )
                    ) {
                        Text(
                            text = box.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(borderColor.copy(alpha = 0.7f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Show tap markers in tap mode
            if (isTapMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (marker in tapMarkers) {
                        drawCircle(
                            color = Color(0xFF00FF00),
                            radius = 28f,
                            center = marker,
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = Color(0xFF00FF00).copy(alpha = 0.25f),
                            radius = 28f,
                            center = marker
                        )
                    }
                }
            }
        }

        // Top bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(statusMsg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { Text("Close", color = Color.White, fontWeight = FontWeight.Bold) }
            }

            if (isTapMode) {
                Text(
                    "Tap each item to count. Tap marker to remove.",
                    color = Color(0xFF00BFFF),
                    fontSize = 13.sp
                )
            } else if (selectedBox != null) {
                Text(
                    "${selectedBox!!.name}: $matchedCount found",
                    color = Color(0xFF00FF00),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode toggle + Detect button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isTapMode = !isTapMode
                        if (isTapMode) {
                            // Freeze current frame
                            val bytes = capturedImageRef.value
                            if (bytes != null) {
                                frozenBitmap = BitmapUtils.decodeSampled(bytes)
                            }
                            tapMarkers = emptyList()
                            count = 0
                            statusMsg = "Tap mode: tap each item to count"
                        } else {
                            frozenBitmap = null
                            tapMarkers = emptyList()
                            statusMsg = "Auto mode: tap Detect to find items"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTapMode) Color(0xFF00BFFF) else Color(0xFF6c757d)
                    )
                ) {
                    Text(
                        if (isTapMode) "Tap Mode" else "Tap Mode",
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        isTapMode = false
                        val bytes = capturedImageRef.value
                        if (bytes != null) detectObjects(bytes)
                        else statusMsg = "No frame captured yet"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !detecting && !isTapMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9500),
                        disabledContainerColor = Color(0xFF6c757d)
                    )
                ) {
                    if (detecting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Detect", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Count controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { if (count > 0) count-- },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE94560)),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }

                Text("$count", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)

                Button(
                    onClick = { count++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        selectedBox = null
                        detectedObjects = emptyList()
                        tapMarkers = emptyList()
                        frozenBitmap = null
                        count = 0
                        matchedCount = 0
                        isTapMode = false
                        statusMsg = "Point camera at items, then tap Detect"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6c757d))
                ) { Text("Reset", color = Color.White, fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { onCountConfirmed(count) },
                    modifier = Modifier.weight(2f),
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF007AFF),
                        disabledContainerColor = Color(0xFF6c757d)
                    )
                ) {
                    Text("Apply Count: $count", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ItemImagePicker(
    selectedImageBitmap: Bitmap?,
    itemImageUrl: String?,
    isUploadingImage: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val context = LocalContext.current
    var imageLoadFailed by remember(itemImageUrl) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Item Image (Optional)",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF2A2A4E), RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    selectedImageBitmap != null -> {
                        Image(
                            bitmap = selectedImageBitmap.asImageBitmap(),
                            contentDescription = "Item Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    itemImageUrl != null && !imageLoadFailed -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(itemImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Item Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                            onError = { imageLoadFailed = true }
                        )
                    }
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "No Photo",
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                "No Image",
                                color = Color(0xFF555555),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (isUploadingImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCameraClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera", color = Color.White, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onGalleryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = "Gallery",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery", color = Color.White, fontSize = 13.sp)
                }

                if (selectedImageBitmap != null || (itemImageUrl != null && !imageLoadFailed)) {
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove Photo",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun createItemImageUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "item_photos")
    if (!photoDir.exists()) photoDir.mkdirs()
    val photoFile = File(photoDir, "item_photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

/**
 * Mirrors the web app's flow: fetch the existing Item, set its `image` URL field
 * to the canonical static path, and POST it back to /item/saveItem so list views
 * that render `item.image` can find the uploaded photo.
 */
private fun persistItemImageUrl(itemId: Int, fallbackCompanyCode: Int, token: String): Boolean {
    return try {
        // 1. Fetch the existing item so we don't blank out other fields.
        val getConn = URL("${ApiConfig.BASE_URL_8080}/item/getSelectedItem?id=$itemId")
            .openConnection() as HttpURLConnection
        getConn.requestMethod = "GET"
        getConn.setRequestProperty("Authorization", "Bearer $token")
        getConn.setRequestProperty("Content-Type", "application/json")
        getConn.connectTimeout = 30000
        getConn.readTimeout = 30000
        disableSslVerification(getConn)

        val getCode = getConn.responseCode
        if (getCode != 200) {
            android.util.Log.w("StockAdjust", "getSelectedItem itemId=$itemId returned $getCode")
            return false
        }
        val itemJson = getConn.inputStream.bufferedReader().use { it.readText() }
        android.util.Log.d("StockAdjust", "getSelectedItem itemId=$itemId body=$itemJson")
        val gson = Gson()
        @Suppress("UNCHECKED_CAST")
        val item = (gson.fromJson(itemJson, Map::class.java) as? Map<String, Any?>)?.toMutableMap()
            ?: return false

        // 2. Set the canonical image URL the rest of the app expects.
        //    Prefer the item's own companyCode; fall back to the mobile UI config value.
        val itemCompanyCode = (item["companyCode"] as? Number)?.toInt()
            ?.takeIf { it > 0 } ?: fallbackCompanyCode
        val imageUrl = "https://cosmospos.com/static/company_${itemCompanyCode}/item_${itemId}.jpeg"
        item["image"] = imageUrl
        android.util.Log.d("StockAdjust", "saveItem will set image=$imageUrl")

        // 3. POST it back via /item/saveItem.
        val postConn = URL("${ApiConfig.BASE_URL_8080}/item/saveItem")
            .openConnection() as HttpURLConnection
        postConn.requestMethod = "POST"
        postConn.setRequestProperty("Authorization", "Bearer $token")
        postConn.setRequestProperty("Content-Type", "application/json")
        postConn.connectTimeout = 30000
        postConn.readTimeout = 30000
        postConn.doOutput = true
        disableSslVerification(postConn)

        val payload = gson.toJson(item)
        postConn.outputStream.bufferedWriter().use { it.write(payload) }

        val code = postConn.responseCode
        val respBody = if (code in 200..299) {
            postConn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } else {
            postConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        android.util.Log.d("StockAdjust", "saveItem responded code=$code body=$respBody")
        code in 200..299
    } catch (e: Exception) {
        android.util.Log.e("StockAdjust", "persistItemImageUrl failed", e)
        false
    }
}

/**
 * Resolve the just-created item's id by querying /item/getAllItems.
 * The /stock/adjust-stock endpoint returns an empty StockItem when creating
 * a new item, so we have to look it up afterwards.
 *
 * Match priority: barcode > code > name (case-insensitive). Most-recently-created
 * (highest id) wins on a tie so we don't pick up an older same-named item.
 */
private fun lookupItemId(token: String, name: String, code: String, barcode: String): Int {
    return try {
        val conn = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        disableSslVerification(conn)

        if (conn.responseCode != 200) return 0
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        @Suppress("UNCHECKED_CAST")
        val items = (Gson().fromJson(body, List::class.java) as? List<Map<String, Any>>) ?: return 0

        fun matches(item: Map<String, Any>): Int {
            val itemBarcode = item["barcode"]?.toString().orEmpty()
            val itemCode = item["code"]?.toString().orEmpty()
            val itemName = item["name"]?.toString().orEmpty()
            return when {
                barcode.isNotBlank() && itemBarcode.equals(barcode, ignoreCase = true) -> 3
                code.isNotBlank() && itemCode.equals(code, ignoreCase = true) -> 2
                name.isNotBlank() && itemName.equals(name, ignoreCase = true) -> 1
                else -> 0
            }
        }

        items
            .map { it to matches(it) }
            .filter { it.second > 0 }
            .maxByOrNull { (item, score) ->
                // Prefer stronger match, then most recent (highest id)
                score * 1_000_000L + ((item["id"] as? Number)?.toLong() ?: 0L)
            }
            ?.first
            ?.let { (it["id"] as? Number)?.toInt() } ?: 0
    } catch (e: Exception) {
        android.util.Log.e("StockAdjust", "lookupItemId failed", e)
        0
    }
}

private fun disableSslVerification(connection: HttpURLConnection) {
    if (connection is javax.net.ssl.HttpsURLConnection) {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        connection.sslSocketFactory = sslContext.socketFactory
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
    }
}

private fun uploadItemImageToServer(context: Context, imageUri: Uri, itemId: Int, token: String): Boolean {
    val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
    val lineEnd = "\r\n"

    try {
        val url = URL("${ApiConfig.BASE_URL_8080}/uploadItem")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.doOutput = true
        connection.doInput = true

        if (connection is javax.net.ssl.HttpsURLConnection) {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }

        val outputStream = DataOutputStream(connection.outputStream)

        // id field
        outputStream.writeBytes("--$boundary$lineEnd")
        outputStream.writeBytes("Content-Disposition: form-data; name=\"id\"$lineEnd")
        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes("$itemId$lineEnd")

        // file field
        outputStream.writeBytes("--$boundary$lineEnd")
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"item_$itemId.jpeg\"$lineEnd")
        outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
        outputStream.writeBytes(lineEnd)

        val inputStream = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapUtils.decodeSampled(inputStream)
        inputStream?.close()

        if (originalBitmap != null) {
            val maxSize = 1024
            val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * ratio).toInt(),
                    (originalBitmap.height * ratio).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            val byteArrayOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
            outputStream.write(byteArrayOutputStream.toByteArray())
            byteArrayOutputStream.close()

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
        }

        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes("--$boundary--$lineEnd")
        outputStream.flush()
        outputStream.close()

        val responseCode = connection.responseCode
        val respBody = if (responseCode == 200) {
            connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        android.util.Log.d("StockAdjust", "uploadItem itemId=$itemId code=$responseCode body=$respBody")
        return responseCode == 200
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}
