package com.pos.cosmos.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pos.cosmos.viewmodels.AppViewModel
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import com.pos.cosmos.api.ApiConfig

data class CustomerData(
    val id: Long = 0,
    val name: String? = null,
    val address: String? = null,
    val telephone: String? = null,
    val whatsappNo: String? = null,
    val email: String? = null,
    val status: String? = null,
    val details: String? = null,
    val companyCode: Int = 4
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerRegistrationScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    var nameInput by remember { mutableStateOf("") }
    var address by remember { mutableStateOf(TextFieldValue("")) }
    var telephone by remember { mutableStateOf(TextFieldValue("")) }
    var whatsappNo by remember { mutableStateOf(TextFieldValue("")) }
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var status by remember { mutableStateOf(TextFieldValue("ACTIVE")) }
    var details by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCustomerId by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingCustomers by remember { mutableStateOf(true) }
    var filteredCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var allCustomers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var messageType by remember { mutableStateOf("") } // "success" or "error"

    // Image state
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var customerImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingImage by remember { mutableStateOf(false) }
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var customerTelephoneAndWhatsappMandatory by remember { mutableStateOf(false) }

    val token by appViewModel.token.collectAsState()
    val config by appViewModel.config.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraPhotoUri != null) {
            selectedImageUri = cameraPhotoUri
            customerImageUrl = null
            // Decode bitmap for preview
            try {
                val inputStream = context.contentResolver.openInputStream(cameraPhotoUri!!)
                selectedImageBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            customerImageUrl = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                selectedImageBitmap = BitmapFactory.decodeStream(inputStream)
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
            val uri = createImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createImageUri(context)
            cameraPhotoUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun uploadCustomerImage(customerId: Long) {
        val uri = selectedImageUri ?: return
        scope.launch {
            isUploadingImage = true
            try {
                val success = withContext(Dispatchers.IO) {
                    uploadImageToServer(context, uri, customerId, token ?: "")
                }
                if (success) {
                    message = "Customer image uploaded successfully!"
                    messageType = "success"
                } else {
                    message = "Failed to upload customer image"
                    messageType = "error"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                message = "Image upload error: ${e.message}"
                messageType = "error"
            } finally {
                isUploadingImage = false
            }
        }
    }

    fun fetchAllCustomers() {
        scope.launch {
            isLoadingCustomers = true
            try {
                val customers = withContext(Dispatchers.IO) {
                    try {
                        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/getAllCustomers?companyCode=0")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000

                        if (connection.responseCode == 200) {
                            val content = connection.inputStream.bufferedReader().use { it.readText() }
                            val gson = Gson()
                            gson.fromJson(content, Array<CustomerData>::class.java).toList()
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
                allCustomers = customers
                filteredCustomers = customers
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingCustomers = false
            }
        }
    }

    fun filterCustomers(query: String) {
        nameInput = query
        if (query.isEmpty()) {
            filteredCustomers = allCustomers
            expanded = false
        } else {
            val filtered = allCustomers.filter { customer ->
                (customer.name?.contains(query, ignoreCase = true) ?: false)
            }
            filteredCustomers = filtered
            expanded = filtered.isNotEmpty()
        }
    }

    fun onSelectCustomer(customer: CustomerData) {
        nameInput = customer.name ?: ""
        address = TextFieldValue(customer.address ?: "")
        telephone = TextFieldValue(customer.telephone ?: "")
        whatsappNo = TextFieldValue(customer.whatsappNo ?: "")
        email = TextFieldValue(customer.email ?: "")
        status = TextFieldValue(customer.status ?: "ACTIVE")
        details = TextFieldValue(customer.details ?: "")
        selectedCustomerId = customer.id
        expanded = false

        // Load existing customer image
        selectedImageBitmap = null
        selectedImageUri = null
        customerImageUrl = "https://cosmospos.com/static/company_${customer.companyCode}/customer_${customer.id}.jpeg"
    }

    fun saveCustomer(isUpdate: Boolean = false) {
        if (nameInput.trim().isEmpty()) {
            message = "Customer name is required!"
            messageType = "error"
            return
        }

        if (customerTelephoneAndWhatsappMandatory) {
            if (telephone.text.trim().isEmpty()) {
                message = "Telephone is mandatory!"
                messageType = "error"
                return
            }
            if (whatsappNo.text.trim().isEmpty()) {
                message = "WhatsApp number is mandatory!"
                messageType = "error"
                return
            }
        }

        if (isUpdate && selectedCustomerId <= 0) {
            return
        }

        scope.launch {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val customerData = mutableMapOf<String, Any>(
                            "id" to if (isUpdate) selectedCustomerId else 0,
                            "name" to nameInput.trim(),
                            "address" to address.text.trim(),
                            "email" to email.text.trim(),
                            "status" to status.text.trim().ifEmpty { "ACTIVE" },
                            "details" to details.text.trim(),
                            "companyCode" to 4
                        )
                        if (telephone.text.trim().isNotEmpty()) {
                            customerData["telephone"] = telephone.text.trim()
                        }
                        if (whatsappNo.text.trim().isNotEmpty()) {
                            customerData["whatsappNo"] = whatsappNo.text.trim()
                        }

                        val connection = URL("${ApiConfig.BASE_URL_8090}/customer/saveCustomer")
                            .openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 30000
                        connection.readTimeout = 30000
                        connection.doOutput = true

                        val gson = Gson()
                        val json = gson.toJson(customerData)
                        connection.outputStream.bufferedWriter().use { it.write(json) }

                        val responseCode = connection.responseCode
                        if (responseCode == 200) {
                            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                            try {
                                val savedCustomer = gson.fromJson(responseBody, CustomerData::class.java)
                                savedCustomer.id
                            } catch (e: Exception) {
                                if (isUpdate) selectedCustomerId else 0L
                            }
                        } else {
                            -1L
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        -1L
                    }
                }

                if (result >= 0) {
                    // Upload image if one was selected
                    val customerIdForUpload = if (result > 0) result else selectedCustomerId
                    if (selectedImageUri != null && customerIdForUpload > 0) {
                        uploadCustomerImage(customerIdForUpload)
                    }

                    message = "Customer ${if (isUpdate) "updated" else "created"} successfully!"
                    messageType = "success"
                    nameInput = ""
                    address = TextFieldValue("")
                    telephone = TextFieldValue("")
                    whatsappNo = TextFieldValue("")
                    email = TextFieldValue("")
                    status = TextFieldValue("ACTIVE")
                    details = TextFieldValue("")
                    selectedCustomerId = 0
                    expanded = false
                    selectedImageBitmap = null
                    selectedImageUri = null
                    customerImageUrl = null
                    fetchAllCustomers()
                } else {
                    message = "Failed to ${if (isUpdate) "update" else "create"} customer"
                    messageType = "error"
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
            fetchAllCustomers()
            // Fetch config
            withContext(Dispatchers.IO) {
                val appConfig = fetchCustomerRegConfig(token!!)
                if (appConfig != null) {
                    appViewModel.setConfig(appConfig)
                    customerTelephoneAndWhatsappMandatory = appConfig.customerTelephoneAndWhatsappMandatory == "Y"
                }
            }
        }
    }

    LaunchedEffect(config) {
        config?.let {
            customerTelephoneAndWhatsappMandatory = it.customerTelephoneAndWhatsappMandatory == "Y"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Registration", color = Color.White) },
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
                "Customer Registration",
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
                        "Customer Information",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isLoadingCustomers) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE94560))
                        }
                    } else {
                        // Customer Photo Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Customer Photo",
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Image Preview
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFF2A2A4E), RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A1A2E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        selectedImageBitmap != null -> {
                                            Image(
                                                bitmap = selectedImageBitmap!!.asImageBitmap(),
                                                contentDescription = "Customer Photo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        customerImageUrl != null -> {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(customerImageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "Customer Photo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop,
                                                onError = {
                                                    // Image doesn't exist on server - show placeholder
                                                    customerImageUrl = null
                                                }
                                            )
                                        }
                                        else -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Person,
                                                    contentDescription = "No Photo",
                                                    tint = Color(0xFF555555),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Text(
                                                    "No Photo",
                                                    color = Color(0xFF555555),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }

                                    // Upload indicator overlay
                                    if (isUploadingImage) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Camera & Gallery Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { launchCamera() },
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

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Button(
                                        onClick = { galleryLauncher.launch("image/*") },
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

                                    // Remove button (only show when image is selected)
                                    if (selectedImageBitmap != null || customerImageUrl != null) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                            onClick = {
                                                selectedImageBitmap = null
                                                selectedImageUri = null
                                                customerImageUrl = null
                                            }
                                        ) {
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

                        // Customer Name with Dropdown
                        Column(modifier = Modifier.fillMaxWidth()) {
                            TextField(
                                value = nameInput,
                                onValueChange = { filterCustomers(it) },
                                label = { Text("Customer Name") },
                                placeholder = { Text("Type customer name...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                singleLine = true
                            )

                            // Show dropdown list
                            if (expanded && filteredCustomers.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .background(Color(0xFF133C55)),
                                    color = Color(0xFF133C55)
                                ) {
                                    LazyColumn {
                                        items(filteredCustomers.size) { index ->
                                            val customer = filteredCustomers[index]
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        onSelectCustomer(customer)
                                                    }
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    text = customer.name ?: "Unknown",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                if (!customer.telephone.isNullOrEmpty()) {
                                                    Text(
                                                        text = "Tel: ${customer.telephone}",
                                                        color = Color(0xFFB0B0B0),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                if (!customer.address.isNullOrEmpty()) {
                                                    Text(
                                                        text = customer.address,
                                                        color = Color(0xFFB0B0B0),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                            if (index < filteredCustomers.size - 1) {
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
                            label = { Text(if (customerTelephoneAndWhatsappMandatory) "Telephone *" else "Telephone") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // WhatsApp Number
                        TextField(
                            value = whatsappNo,
                            onValueChange = { whatsappNo = it },
                            label = { Text(if (customerTelephoneAndWhatsappMandatory) "WhatsApp No *" else "WhatsApp No") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // Email
                        TextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            singleLine = true
                        )

                        // Status
                        TextField(
                            value = status,
                            onValueChange = { status = it },
                            label = { Text("Status (ACTIVE/INACTIVE)") },
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

                        // Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { saveCustomer(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isLoading && nameInput.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                } else {
                                    Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { saveCustomer(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !isLoading && selectedCustomerId > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                } else {
                                    Text("Update", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Selected Customer ID display
                        if (selectedCustomerId > 0) {
                            Text(
                                "Selected Customer ID: $selectedCustomerId",
                                fontSize = 12.sp,
                                color = Color(0xFFB0B0B0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun fetchCustomerRegConfig(token: String): com.pos.cosmos.models.Config? {
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
            val gson = Gson()
            gson.fromJson(content, com.pos.cosmos.models.Config::class.java)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun createImageUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "customer_photos")
    if (!photoDir.exists()) photoDir.mkdirs()
    val photoFile = File(photoDir, "customer_photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        photoFile
    )
}

private fun uploadImageToServer(context: Context, imageUri: Uri, customerId: Long, token: String): Boolean {
    val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"
    val lineEnd = "\r\n"

    try {
        val url = URL("${ApiConfig.BASE_URL_8080}/upload-customer")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.doOutput = true
        connection.doInput = true

        // Disable SSL verification to match RetrofitClient behavior
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

        // Write customer ID field
        outputStream.writeBytes("--$boundary$lineEnd")
        outputStream.writeBytes("Content-Disposition: form-data; name=\"id\"$lineEnd")
        outputStream.writeBytes(lineEnd)
        outputStream.writeBytes("$customerId$lineEnd")

        // Write image file
        outputStream.writeBytes("--$boundary$lineEnd")
        outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"customer_$customerId.jpeg\"$lineEnd")
        outputStream.writeBytes("Content-Type: image/jpeg$lineEnd")
        outputStream.writeBytes(lineEnd)

        // Read and compress the image before uploading
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBitmap != null) {
            // Scale down if too large (max 1024px on longest side)
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
        if (responseCode != 200) {
            val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() }
            println("Upload error: Code=$responseCode, Response=$errorStream")
        }

        return responseCode == 200
    } catch (e: Exception) {
        e.printStackTrace()
        return false
    }
}
