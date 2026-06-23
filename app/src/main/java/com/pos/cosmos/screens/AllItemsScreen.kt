package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.pos.cosmos.api.ApiConfig

data class Item(
    val id: Int,
    val code: String,
    val name: String,
    val sellingPrice: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllItemsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var allItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        if (token != null) {
            scope.launch {
                loading = true
                val items = withContext(Dispatchers.Default) {
                    fetchItems(token!!)
                }
                allItems = items
                filteredItems = items
                loading = false
            }
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
                onValueChange = { query ->
                    searchQuery = query
                    filteredItems = if (query.isEmpty()) {
                        allItems
                    } else {
                        allItems.filter {
                            it.name.contains(query, ignoreCase = true) ||
                            it.code.contains(query, ignoreCase = true)
                        }
                    }
                },
                label = { Text("Search items...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true
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
                    items(filteredItems) { item ->
                        ItemCard(item)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemCard(item: Item) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Code: ${item.code}", fontSize = 12.sp, color = Color.Gray)
                Text("Price: ${String.format("%.2f", item.sellingPrice)}", fontSize = 12.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private suspend fun fetchItems(token: String): List<Item> {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/item/getAllItems")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = Gson()
            @Suppress("UNCHECKED_CAST")
            val list = gson.fromJson(content, List::class.java) as? List<Map<String, Any>>
            list?.mapNotNull { map ->
                try {
                    Item(
                        id = (map["id"] as? Number)?.toInt() ?: return@mapNotNull null,
                        code = map["code"] as? String ?: "",
                        name = map["name"] as? String ?: "",
                        sellingPrice = (map["sellingPrice"] as? Number)?.toDouble() ?: 0.0
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
