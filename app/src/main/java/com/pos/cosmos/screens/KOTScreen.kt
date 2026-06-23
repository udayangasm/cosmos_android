package com.pos.cosmos.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import com.pos.cosmos.api.ApiConfig
import android.util.Log
import com.pos.cosmos.services.TranslationService

data class KOTMenuItem(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double,
    var quantity: Int = 1,
    val invoiceId: Int = 0,
    val supplierId: Int = 0,
    val totalQty: Int = 0,
    val image: String = "",
    val categoryId: Int = 0,
    val description: String = ""
) {
    val amount: Double
        get() = sellingPrice * quantity
}

data class MenuCategory(
    val id: Int,
    val name: String,
    val image: String = ""
)

// Language data class
data class Language(
    val code: String,
    val name: String,
    val flag: String
)

// Available languages - code matches backend targetLanguage values
val availableLanguages = listOf(
    Language("English", "English", "🇬🇧"),
    Language("Russian", "Russian", "🇷🇺"),
    Language("Ukrainian", "Ukrainian", "🇺🇦"),
    Language("German", "German", "🇩🇪"),
    Language("French", "French", "🇫🇷"),
    Language("Chinese", "Chinese", "🇨🇳"),
    Language("Japanese", "Japanese", "🇯🇵"),
    Language("Hindi", "Hindi", "🇮🇳")
)

// UI translation function for static UI text
fun translateText(text: String, targetLanguage: String): String {
    val translations = mapOf(
        "Russian" to mapOf(
            "Add to Plate" to "Добавить",
            "Close" to "Закрыть",
            "Your Plate" to "Ваша тарелка",
            "Total" to "Итого",
            "Place Order" to "Оформить заказ",
            "Continue" to "Продолжить",
            "Clear All" to "Очистить",
            "Your plate is empty" to "Ваша тарелка пуста",
            "Tap items to add them" to "Нажмите на товары",
            "Description" to "Описание"
        ),
        "Ukrainian" to mapOf(
            "Add to Plate" to "Додати",
            "Close" to "Закрити",
            "Your Plate" to "Ваша тарілка",
            "Total" to "Всього",
            "Place Order" to "Оформити",
            "Continue" to "Продовжити",
            "Clear All" to "Очистити",
            "Your plate is empty" to "Ваша тарілка порожня",
            "Tap items to add them" to "Натисніть на товари",
            "Description" to "Опис"
        ),
        "German" to mapOf(
            "Add to Plate" to "Hinzufügen",
            "Close" to "Schließen",
            "Your Plate" to "Ihr Teller",
            "Total" to "Gesamt",
            "Place Order" to "Bestellen",
            "Continue" to "Weiter",
            "Clear All" to "Leeren",
            "Your plate is empty" to "Ihr Teller ist leer",
            "Tap items to add them" to "Tippen Sie auf Artikel",
            "Description" to "Beschreibung"
        ),
        "French" to mapOf(
            "Add to Plate" to "Ajouter",
            "Close" to "Fermer",
            "Your Plate" to "Votre assiette",
            "Total" to "Total",
            "Place Order" to "Commander",
            "Continue" to "Continuer",
            "Clear All" to "Vider",
            "Your plate is empty" to "Votre assiette est vide",
            "Tap items to add them" to "Appuyez sur les articles",
            "Description" to "Description"
        ),
        "Chinese" to mapOf(
            "Add to Plate" to "添加",
            "Close" to "关闭",
            "Your Plate" to "您的餐盘",
            "Total" to "总计",
            "Place Order" to "下单",
            "Continue" to "继续",
            "Clear All" to "清空",
            "Your plate is empty" to "您的餐盘是空的",
            "Tap items to add them" to "点击添加菜品",
            "Description" to "描述"
        ),
        "Japanese" to mapOf(
            "Add to Plate" to "追加",
            "Close" to "閉じる",
            "Your Plate" to "お皿",
            "Total" to "合計",
            "Place Order" to "注文する",
            "Continue" to "続ける",
            "Clear All" to "クリア",
            "Your plate is empty" to "お皿は空です",
            "Tap items to add them" to "タップして追加",
            "Description" to "説明"
        ),
        "Hindi" to mapOf(
            "Add to Plate" to "जोड़ें",
            "Close" to "बंद करें",
            "Your Plate" to "आपकी प्लेट",
            "Total" to "कुल",
            "Place Order" to "ऑर्डर करें",
            "Continue" to "जारी रखें",
            "Clear All" to "सब हटाएं",
            "Your plate is empty" to "आपकी प्लेट खाली है",
            "Tap items to add them" to "जोड़ने के लिए टैप करें",
            "Description" to "विवरण"
        )
    )

    return translations[targetLanguage]?.get(text) ?: text
}

// Get translated item name from TranslationService (falls back to original if not available)
// The version parameter is used to trigger recomposition when translations are loaded
@Composable
fun getTranslatedItemName(itemId: Int, originalName: String, language: String, version: Int = 0): String {
    // version is used to trigger recomposition, not in actual logic
    return remember(itemId, originalName, language, version) {
        if (language == "English") {
            originalName
        } else {
            TranslationService.getTranslatedName(itemId, originalName)
        }
    }
}

// Get translated item description from TranslationService (falls back to original if not available)
// The version parameter is used to trigger recomposition when translations are loaded
@Composable
fun getTranslatedItemDescription(itemId: Int, originalDescription: String, language: String, version: Int = 0): String {
    // version is used to trigger recomposition, not in actual logic
    return remember(itemId, originalDescription, language, version) {
        if (language == "English") {
            originalDescription
        } else {
            TranslationService.getTranslatedDescription(itemId, originalDescription)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KOTScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var orderItems by remember { mutableStateOf<List<KOTMenuItem>>(emptyList()) }
    var availableMenuItems by remember { mutableStateOf<List<KOTMenuItem>>(emptyList()) }
    var categories by remember { mutableStateOf<List<MenuCategory>>(emptyList()) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var tableNumber by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var showTableDialog by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<KOTMenuItem?>(null) }
    var showPlateDialog by remember { mutableStateOf(false) }
    var showLanguageSelector by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(availableLanguages[0]) }
    // Track translation version to trigger recomposition when translations are loaded
    var translationVersion by remember { mutableStateOf(0) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        if (token != null) {
            loading = true
            scope.launch {
                withContext(Dispatchers.Default) {
                    categories = fetchCategories(token!!)
                    availableMenuItems = fetchAllMenuItems(token!!)
                }
                loading = false
            }
        }
    }

    // Load translations when language changes
    LaunchedEffect(selectedLanguage, token) {
        if (token != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        if (selectedLanguage.code == "English") {
                            // Clear translations for English
                            TranslationService.clearCache()
                            Log.d("KOTScreen", "Cleared translations for English")
                        } else {
                            TranslationService.loadTranslations(
                                baseUrl = "${ApiConfig.BASE_URL_8080}",
                                token = token!!,
                                sourceLanguage = "English",
                                targetLanguage = selectedLanguage.code
                            )
                            Log.d("KOTScreen", "Loaded translations for ${selectedLanguage.code}")
                        }
                    } catch (e: Exception) {
                        Log.e("KOTScreen", "Failed to load translations", e)
                    }
                }
                // Increment version to trigger recomposition
                translationVersion++
            }
        }
    }

    val filteredItems = remember(selectedCategoryId, searchQuery, availableMenuItems) {
        when {
            searchQuery.isNotBlank() -> {
                availableMenuItems.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true)
                }
            }
            selectedCategoryId != null && selectedCategoryId != 0 -> {
                availableMenuItems.filter { item ->
                    item.categoryId == selectedCategoryId
                }
            }
            else -> availableMenuItems
        }
    }

    val totalAmount = orderItems.sumOf { it.amount }
    val totalItems = orderItems.sumOf { it.quantity }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kitchen Order Ticket", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (tableNumber.isNotBlank()) {
                            Text("Table: $tableNumber", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showTableDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Table", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E)
                )
            )
        }
    ) { padding ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1A237E))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Top Row: Language Selector + Search Bar + Food Plate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Language Selector
                        Card(
                            modifier = Modifier.clickable { showLanguageSelector = true },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = selectedLanguage.flag,
                                    fontSize = 24.sp
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Language",
                                    tint = Color(0xFF757575),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Search Bar
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFF1A237E),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        isSearching = it.isNotBlank()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        cursorColor = Color(0xFF1A237E)
                                    ),
                                    singleLine = true
                                )
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearching = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            }
                        }

                        // Food Plate Button
                        FoodPlateButton(
                            itemCount = totalItems,
                            totalAmount = totalAmount,
                            hasItems = orderItems.isNotEmpty(),
                            onClick = { showPlateDialog = true }
                        )
                    }

                    // Categories Row - Full Width
                    if (!isSearching) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(categories) { category ->
                                    CategoryChip(
                                        category = category,
                                        isSelected = selectedCategoryId == category.id,
                                        onClick = { selectedCategoryId = category.id }
                                    )
                                }
                                item {
                                    CategoryChip(
                                        category = MenuCategory(0, "ALL ITEMS", ""),
                                        isSelected = selectedCategoryId == null || selectedCategoryId == 0,
                                        onClick = { selectedCategoryId = 0 }
                                    )
                                }
                            }
                        }
                    }

                    // Items Grid - 2 columns for bigger images
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredItems) { item ->
                            LargeMenuItemCard(
                                item = item,
                                isInCart = orderItems.any { it.id == item.id },
                                cartQuantity = orderItems.find { it.id == item.id }?.quantity ?: 0,
                                language = selectedLanguage,
                                translationVersion = translationVersion,
                                onAddToCart = {
                                    val existingItem = orderItems.find { it.id == item.id }
                                    orderItems = if (existingItem != null) {
                                        orderItems.map {
                                            if (it.id == item.id) it.copy(quantity = it.quantity + 1)
                                            else it
                                        }
                                    } else {
                                        orderItems + item.copy(quantity = 1)
                                    }
                                },
                                onShowDetail = {
                                    selectedItemForDetail = item
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Language Selector Dialog
    if (showLanguageSelector) {
        LanguageSelectorDialog(
            currentLanguage = selectedLanguage,
            onLanguageSelected = { language ->
                selectedLanguage = language
                showLanguageSelector = false
            },
            onDismiss = { showLanguageSelector = false }
        )
    }

    // Plate Dialog with Animation
    if (showPlateDialog) {
        AnimatedPlateDialog(
            orderItems = orderItems,
            totalAmount = totalAmount,
            tableNumber = tableNumber,
            language = selectedLanguage,
            translationVersion = translationVersion,
            onDismiss = { showPlateDialog = false },
            onUpdateQuantity = { item, newQty ->
                if (newQty <= 0) {
                    orderItems = orderItems.filter { it.id != item.id }
                } else {
                    orderItems = orderItems.map {
                        if (it.id == item.id) it.copy(quantity = newQty)
                        else it
                    }
                }
            },
            onRemoveItem = { item ->
                orderItems = orderItems.filter { it.id != item.id }
            },
            onClearAll = {
                orderItems = emptyList()
            },
            onPlaceOrder = {
                if (tableNumber.isBlank()) {
                    showPlateDialog = false
                    showTableDialog = true
                } else {
                    scope.launch {
                        loading = true
                        showPlateDialog = false
                        val result = withContext(Dispatchers.Default) {
                            createKOT(token!!, orderItems, tableNumber, customerName)
                        }
                        loading = false

                        if (result != null && (result.startsWith("Failed") || result.startsWith("Exception"))) {
                            errorMessage = result
                            showErrorDialog = true
                        } else {
                            successMessage = "Order created successfully! KOT No: ${result ?: ""}"
                            showSuccessDialog = true
                            orderItems = emptyList()
                        }
                    }
                }
            }
        )
    }

    // Table Number Dialog
    if (showTableDialog) {
        TableNumberDialog(
            currentTableNumber = tableNumber,
            currentCustomerName = customerName,
            onDismiss = { showTableDialog = false },
            onSave = { table, name ->
                tableNumber = table
                customerName = name
                showTableDialog = false
            },
            hasOrderItems = orderItems.isNotEmpty(),
            onPlaceOrder = { table, name ->
                tableNumber = table
                customerName = name
                showTableDialog = false

                scope.launch {
                    loading = true
                    val result = withContext(Dispatchers.Default) {
                        createKOT(token!!, orderItems, tableNumber, customerName)
                    }
                    loading = false

                    if (result != null && (result.startsWith("Failed") || result.startsWith("Exception"))) {
                        errorMessage = result
                        showErrorDialog = true
                    } else {
                        successMessage = "Order created successfully! KOT No: ${result ?: ""}"
                        showSuccessDialog = true
                        orderItems = emptyList()
                    }
                }
            }
        )
    }

    // Error Dialog
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error", color = Color(0xFFD32F2F)) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Success", color = Color(0xFF4CAF50)) },
            text = { Text(successMessage) },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Full Screen Item Detail Dialog
    selectedItemForDetail?.let { item ->
        FullScreenItemDialog(
            item = item,
            language = selectedLanguage,
            translationVersion = translationVersion,
            onDismiss = { selectedItemForDetail = null },
            onAddToPlate = {
                val existingItem = orderItems.find { it.id == item.id }
                orderItems = if (existingItem != null) {
                    orderItems.map {
                        if (it.id == item.id) it.copy(quantity = it.quantity + 1)
                        else it
                    }
                } else {
                    orderItems + item.copy(quantity = 1)
                }
                selectedItemForDetail = null
            }
        )
    }
}

@Composable
fun FoodPlateButton(
    itemCount: Int,
    totalAmount: Double,
    hasItems: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "plate")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (hasItems) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .scale(pulseScale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (hasItems) Color(0xFF1A237E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Food Plate Emoji/Icon
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🍽️",
                    fontSize = 32.sp
                )
                if (itemCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .size(22.dp)
                            .background(Color(0xFFFF5722), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$itemCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Total Amount
            Column {
                Text(
                    text = "Total",
                    fontSize = 11.sp,
                    color = if (hasItems) Color.White.copy(alpha = 0.8f) else Color.Gray
                )
                Text(
                    text = "Rs. ${String.format("%.0f", totalAmount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasItems) Color.White else Color(0xFF1A237E)
                )
            }
        }
    }
}

@Composable
fun LanguageSelectorDialog(
    currentLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Select Language",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
                Spacer(modifier = Modifier.height(16.dp))

                availableLanguages.forEach { language ->
                    val isSelected = language.code == currentLanguage.code
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onLanguageSelected(language) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFE8EAF6) else Color(0xFFF5F5F5)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(2.dp, Color(0xFF1A237E)) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = language.flag,
                                fontSize = 32.sp
                            )
                            Text(
                                text = language.name,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color(0xFF212121)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedPlateDialog(
    orderItems: List<KOTMenuItem>,
    totalAmount: Double,
    tableNumber: String,
    language: Language,
    translationVersion: Int = 0,
    onDismiss: () -> Unit,
    onUpdateQuantity: (KOTMenuItem, Int) -> Unit,
    onRemoveItem: (KOTMenuItem) -> Unit,
    onClearAll: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 0f else -30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )

    Dialog(
        onDismissRequest = {
            isVisible = false
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                    this.alpha = alpha
                },
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Plate Animation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🍽️",
                            fontSize = 36.sp,
                            modifier = Modifier.graphicsLayer {
                                rotationY = if (isVisible) 0f else 180f
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            translateText("Your Plate", language.code),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }

                    if (orderItems.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text(translateText("Clear All", language.code), color = Color(0xFFD32F2F))
                        }
                    }
                }

                if (tableNumber.isNotBlank()) {
                    Text(
                        "Table: $tableNumber",
                        fontSize = 14.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0))
                Spacer(modifier = Modifier.height(12.dp))

                if (orderItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🍽️",
                                fontSize = 80.sp,
                                modifier = Modifier.graphicsLayer { this.alpha = 0.5f }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                translateText("Your plate is empty", language.code),
                                fontSize = 18.sp,
                                color = Color(0xFF9E9E9E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                translateText("Tap items to add them", language.code),
                                fontSize = 14.sp,
                                color = Color(0xFFBDBDBD)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(orderItems) { item ->
                            PlateItemCard(
                                item = item,
                                language = language,
                                translationVersion = translationVersion,
                                onUpdateQuantity = { newQty -> onUpdateQuantity(item, newQty) },
                                onRemove = { onRemoveItem(item) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            translateText("Total", language.code),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF424242)
                        )
                        Text(
                            "Rs. ${String.format("%.2f", totalAmount)}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A237E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isVisible = false
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(translateText("Continue", language.code), fontSize = 16.sp, color = Color(0xFF1A237E))
                    }

                    Button(
                        onClick = onPlaceOrder,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = orderItems.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(translateText("Place Order", language.code), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LargeMenuItemCard(
    item: KOTMenuItem,
    isInCart: Boolean,
    cartQuantity: Int,
    language: Language,
    translationVersion: Int = 0,
    onAddToCart: () -> Unit,
    onShowDetail: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .scale(scale)
            .clickable {
                isPressed = true
                onShowDetail()
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        border = if (isInCart) BorderStroke(3.dp, Color(0xFF4CAF50)) else null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AsyncImage(
                    model = com.pos.cosmos.local.LocalImageStore
                        .localOrRemote(androidx.compose.ui.platform.LocalContext.current, item.id.toLong(), item.image)
                        ?: "https://cosmospos.com/static/default-kot.jpeg",
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )

                if (isInCart && cartQuantity > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$cartQuantity",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Add Button
                IconButton(
                    onClick = {
                        isPressed = true
                        onAddToCart()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    getTranslatedItemName(item.id, item.name, language.code, translationVersion),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Rs. ${String.format("%.2f", item.sellingPrice)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

@Composable
fun FullScreenItemDialog(
    item: KOTMenuItem,
    language: Language,
    translationVersion: Int = 0,
    onDismiss: () -> Unit,
    onAddToPlate: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Large Image
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable(enabled = false) { },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AsyncImage(
                        model = com.pos.cosmos.local.LocalImageStore
                            .localOrRemote(androidx.compose.ui.platform.LocalContext.current, item.id.toLong(), item.image)
                            ?: "https://cosmospos.com/static/default-kot.jpeg",
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Item Name - translated if available
                val translatedName = getTranslatedItemName(item.id, item.name, language.code, translationVersion)
                Text(
                    text = translatedName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Price
                Text(
                    text = "Rs. ${String.format("%.2f", item.sellingPrice)}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Description - show translated if available, otherwise original, or nothing if empty
                val translatedDescription = getTranslatedItemDescription(item.id, item.description, language.code, translationVersion)
                if (translatedDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = translateText("Description", language.code),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = translatedDescription,
                                fontSize = 16.sp,
                                color = Color.White,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
                // If no description available (neither translated nor original), show nothing

                Spacer(modifier = Modifier.height(24.dp))

                // Add to Plate Button
                Button(
                    onClick = onAddToPlate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        translateText("Add to Plate", language.code),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PlateItemCard(
    item: KOTMenuItem,
    language: Language,
    translationVersion: Int = 0,
    onUpdateQuantity: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = com.pos.cosmos.local.LocalImageStore
                    .localOrRemote(androidx.compose.ui.platform.LocalContext.current, item.id.toLong(), item.image)
                    ?: "https://cosmospos.com/static/default-kot.jpeg",
                contentDescription = item.name,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    getTranslatedItemName(item.id, item.name, language.code, translationVersion),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Rs. ${String.format("%.2f", item.sellingPrice)} each",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
                Text(
                    "Subtotal: Rs. ${String.format("%.2f", item.amount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A237E)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onUpdateQuantity(item.quantity - 1) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE0E0E0), CircleShape)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Decrease",
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    "${item.quantity}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121),
                    modifier = Modifier.widthIn(min = 32.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { onUpdateQuantity(item.quantity + 1) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF1A237E), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    category: MenuCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .width(if (category.image.isNotBlank()) 120.dp else 100.dp)
            .scale(scale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A237E) else Color(0xFFF5F5F5)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val catImageModel = com.pos.cosmos.local.LocalImageStore
                .localCategoryOrRemote(androidx.compose.ui.platform.LocalContext.current, category.id.toLong(), category.image)
            if (catImageModel != null) {
                AsyncImage(
                    model = catImageModel,
                    contentDescription = category.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                category.name,
                color = if (isSelected) Color.White else Color(0xFF212121),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableNumberDialog(
    currentTableNumber: String,
    currentCustomerName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    hasOrderItems: Boolean = false,
    onPlaceOrder: ((String, String) -> Unit)? = null
) {
    var tableNumber by remember { mutableStateOf(currentTableNumber) }
    var customerName by remember { mutableStateOf(currentCustomerName) }

    AlertDialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Table Details",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = tableNumber,
                    onValueChange = { tableNumber = it },
                    label = { Text("Table Number", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1A237E),
                        focusedLabelColor = Color(0xFF1A237E),
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color(0xFF1A237E)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name (Optional)", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1A237E),
                        focusedLabelColor = Color(0xFF1A237E),
                        unfocusedBorderColor = Color.Gray,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        cursorColor = Color(0xFF1A237E)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (hasOrderItems && onPlaceOrder != null) {
                    Button(
                        onClick = { onPlaceOrder(tableNumber, customerName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        enabled = tableNumber.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Place Order", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color(0xFF1A237E))
                    }
                    Button(
                        onClick = { onSave(tableNumber, customerName) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E)),
                        enabled = tableNumber.isNotBlank()
                    ) {
                        Text(if (hasOrderItems) "Save & Continue" else "Save")
                    }
                }
            }
        }
    }
}

private suspend fun fetchCategories(token: String): List<MenuCategory> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/category/getAllCategories")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val categories = gson.fromJson(content, List::class.java) as? List<Map<String, Any>> ?: emptyList()

            categories.mapNotNull { catMap ->
                try {
                    val id = (catMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val name = catMap["name"] as? String ?: return@mapNotNull null
                    val image = catMap["image"] as? String ?: ""
                    MenuCategory(id = id, name = name, image = image)
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("KOTScreen", "Exception in fetchCategories: ${e.message}")
        emptyList()
    }
}

private suspend fun fetchAllMenuItems(token: String): List<KOTMenuItem> {
    return try {
        // Map to store item images and descriptions from /item/getAllItems
        val allItemsMap = mutableMapOf<Int, String>()
        val allItemsDescriptionMap = mutableMapOf<Int, String>()
        try {
            val itemsConnection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
                .openConnection() as HttpURLConnection
            itemsConnection.requestMethod = "GET"
            itemsConnection.setRequestProperty("Authorization", "Bearer $token")
            itemsConnection.connectTimeout = 30000
            itemsConnection.readTimeout = 30000

            if (itemsConnection.responseCode == 200) {
                val itemsContent = itemsConnection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                @Suppress("UNCHECKED_CAST")
                val allItems = gson.fromJson(itemsContent, List::class.java) as? List<Map<String, Any>> ?: emptyList()

                allItems.forEach { item ->
                    val id = (item["id"] as? Number)?.toInt()
                    val image = item["image"] as? String
                    val description = item["description"] as? String
                    if (id != null) {
                        if (!image.isNullOrBlank()) {
                            allItemsMap[id] = image
                        }
                        if (!description.isNullOrBlank()) {
                            allItemsDescriptionMap[id] = description
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KOTScreen", "Error loading item images: ${e.message}")
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/getAllBillItems?billType=CASH")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            val response = gson.fromJson(content, Map::class.java) as? Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val billItems = response?.get("billItems") as? List<Map<String, Any>> ?: emptyList()

            billItems.mapNotNull { itemMap ->
                try {
                    val itemId = (itemMap["id"] as? Number)?.toInt() ?: return@mapNotNull null
                    val itemCode = itemMap["code"] as? String ?: ""
                    val itemName = itemMap["name"] as? String ?: ""
                    val sellingPrice = (itemMap["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                    val categoryId = (itemMap["categoryId"] as? Number)?.toInt() ?: 0
                    val imageUrl = allItemsMap[itemId] ?: ""
                    val description = allItemsDescriptionMap[itemId] ?: ""

                    if (sellingPrice <= 0.0 || itemName.isBlank()) {
                        return@mapNotNull null
                    }

                    KOTMenuItem(
                        id = itemId,
                        code = itemCode,
                        name = itemName,
                        sellingPrice = sellingPrice,
                        invoiceId = 0,
                        supplierId = 0,
                        totalQty = 0,
                        image = imageUrl,
                        categoryId = categoryId,
                        description = description
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("KOTScreen", "Exception in fetchAllMenuItems: ${e.message}")
        emptyList()
    }
}

private suspend fun createKOT(
    token: String,
    items: List<KOTMenuItem>,
    tableNumber: String,
    customerName: String
): String? {
    return try {
        val total = items.sumOf { it.amount }

        val kotData = buildJsonObject(
            "orderItems" to items.map { item ->
                buildJsonObject(
                    "itemId" to item.id,
                    "name" to item.name,
                    "qty" to item.quantity,
                    "price" to item.sellingPrice
                )
            },
            "orderDateTime" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()),
            "orderType" to if (tableNumber.isNotBlank()) "dine-in" else "takeaway",
            "notes" to customerName.ifEmpty { "" },
            "tableNo" to if (tableNumber.isNotBlank()) tableNumber else null
        )

        val connection = URL("${ApiConfig.BASE_URL_8080}/kitchen-order")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        val jsonString = kotData.toString()
        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        if (connection.responseCode == 200 || connection.responseCode == 201) {
            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            try {
                val gson = Gson()
                val response = gson.fromJson(responseBody, JsonObject::class.java)
                response.get("kitchenOrderNo")?.asString ?: "New Order"
            } catch (e: Exception) {
                "New Order"
            }
        } else {
            val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            "Failed to create KOT. Response code: ${connection.responseCode}\nError: $errorResponse"
        }
    } catch (e: Exception) {
        "Exception while creating KOT: ${e.message}"
    }
}

private fun buildJsonObject(vararg pairs: Pair<String, Any?>): JsonObject {
    val obj = JsonObject()
    pairs.forEach { (key, value) ->
        when (value) {
            is String -> obj.addProperty(key, value)
            is Number -> obj.addProperty(key, value)
            is Boolean -> obj.addProperty(key, value)
            is JsonObject -> obj.add(key, value)
            is List<*> -> {
                val array = com.google.gson.JsonArray()
                value.forEach { item ->
                    if (item is JsonObject) array.add(item)
                    else if (item is String) array.add(item)
                    else if (item is Number) array.add(item)
                }
                obj.add(key, array)
            }
            null -> obj.addProperty(key, null as String?)
        }
    }
    return obj
}
