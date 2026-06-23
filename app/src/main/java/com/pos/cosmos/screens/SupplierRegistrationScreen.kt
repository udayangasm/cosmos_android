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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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

data class SupplierData(
    val id: Int = 0,
    val name: String? = null,
    val address: String? = null,
    val telephone: String? = null,
    val phone: String? = null,
    val details: String? = null,
    val contact: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierRegistrationScreenFull(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var nameInput by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(TextFieldValue("")) }
    var telephone by remember { mutableStateOf(TextFieldValue("")) }
    var details by remember { mutableStateOf(TextFieldValue("")) }
    var selectedSupplierId by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingSuppliers by remember { mutableStateOf(true) }
    var filteredSuppliers by remember { mutableStateOf<List<SupplierData>>(emptyList()) }
    var allSuppliers by remember { mutableStateOf<List<SupplierData>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    val token by appViewModel.token.collectAsState()
    val scope = rememberCoroutineScope()

    fun fetchAllSuppliers() {
        scope.launch {
            isLoadingSuppliers = true
            try {
                val suppliers = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/supplier/getAllSuppliers?companyCode=0")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<SupplierData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
                allSuppliers = suppliers
                filteredSuppliers = suppliers
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingSuppliers = false
            }
        }
    }

    fun filterSuppliers(query: String) {
        nameInput = query
        if (query.isEmpty()) {
            filteredSuppliers = allSuppliers
            expanded = false
        } else {
            val filtered = allSuppliers.filter { supplier ->
                (supplier.name?.contains(query, ignoreCase = true) ?: false)
            }
            filteredSuppliers = filtered
            expanded = filtered.isNotEmpty()
        }
    }

    fun onSelectSupplier(supplier: SupplierData) {
        nameInput = supplier.name ?: ""
        address = TextFieldValue(supplier.address ?: "")
        telephone = TextFieldValue(supplier.telephone ?: supplier.phone ?: "")
        details = TextFieldValue(supplier.details ?: supplier.contact ?: "")
        selectedSupplierId = supplier.id
        expanded = false
    }

    fun saveSupplier(isUpdate: Boolean = false) {
        if (nameInput.trim().isEmpty()) {
            return
        }

        if (isUpdate && selectedSupplierId <= 0) {
            return
        }

        scope.launch {
            isLoading = true
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val supplierData = mapOf(
                            "id" to if (isUpdate) selectedSupplierId else 0,
                            "name" to nameInput.trim(),
                            "address" to address.text.trim(),
                            "telephone" to telephone.text.trim(),
                            "details" to details.text.trim()
                        )

                        val connection = URL("${ApiConfig.BASE_URL_8090}/supplier/saveSupplier")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        val gson = Gson()
                        val json = gson.toJson(supplierData)
                        connection.outputStream.bufferedWriter().use { it.write(json) }

                        connection.responseCode == 200
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                if (success) {
                    nameInput = ""
                    address = TextFieldValue("")
                    telephone = TextFieldValue("")
                    details = TextFieldValue("")
                    selectedSupplierId = 0
                    expanded = false
                    fetchAllSuppliers()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(token) {
        if (token != null) {
            fetchAllSuppliers()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supplier Registration", color = Color.White) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Supplier Registration",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Supplier Information",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isLoadingSuppliers) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE94560))
                        }
                    } else {
                        // Supplier Name with Dropdown
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = nameInput,
                                onValueChange = { filterSuppliers(it) },
                                label = { Text("Supplier Name") },
                                placeholder = { Text("Type supplier name...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true
                            )

                            // Show dropdown list
                            if (expanded && filteredSuppliers.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .background(Color(0xFF133C55)),
                                    color = Color(0xFF133C55)
                                ) {
                                    LazyColumn {
                                        items(filteredSuppliers.size) { index ->
                                            val supplier = filteredSuppliers[index]
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
                                                if (!supplier.telephone.isNullOrEmpty() || !supplier.phone.isNullOrEmpty()) {
                                                    Text(
                                                        text = "Tel: ${supplier.telephone ?: supplier.phone}",
                                                        color = Color(0xFFB0B0B0),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            if (index < filteredSuppliers.size - 1) {
                                                Divider(
                                                    color = Color(0xFF0F3460),
                                                    thickness = 0.5.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Address
                        TextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // Telephone
                        TextField(
                            value = telephone,
                            onValueChange = { telephone = it },
                            label = { Text("Telephone") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // Details
                        TextField(
                            value = details,
                            onValueChange = { details = it },
                            label = { Text("Details") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { saveSupplier(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isLoading && nameInput.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                            ) {
                                Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { saveSupplier(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isLoading && selectedSupplierId > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                            ) {
                                Text("Update", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedSupplierId > 0) {
                            Text(
                                "Selected Supplier ID: $selectedSupplierId",
                                color = Color(0xFFB0B0B0),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
