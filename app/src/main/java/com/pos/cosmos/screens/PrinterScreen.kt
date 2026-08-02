package com.pos.cosmos.screens

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.pos.cosmos.api.RetrofitClient
import com.pos.cosmos.models.PrinterInfo
import com.pos.cosmos.services.BluetoothDeviceInfo
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.services.BluetoothService
import com.pos.cosmos.services.PrinterService
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import com.pos.cosmos.utils.ReceiptItem
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.pos.cosmos.api.ApiConfig

/** Safe asString that handles JsonNull */
private val JsonElement?.safeString: String?
    get() = if (this == null || this is JsonNull) null else this.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(
    navController: NavHostController,
    appViewModel: AppViewModel,
    context: Context
) {
    var devices by remember { mutableStateOf<List<BluetoothDeviceInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var printingButton by remember { mutableStateOf<String?>(null) }
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var permissionGranted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Template editor states
    var expanded80mm by remember { mutableStateOf(false) }
    var expanded58mm by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf<String?>(null) }

    // 80mm template fields
    var t80CompanyName by remember { mutableStateOf("") }
    var t80CompanyFont by remember { mutableStateOf("17") }
    var t80CompanyGap by remember { mutableStateOf("6") }
    var t80Address by remember { mutableStateOf("") }
    var t80AddressFont by remember { mutableStateOf("12") }
    var t80AddressGap by remember { mutableStateOf("6") }
    var t80Contact by remember { mutableStateOf("") }
    var t80ContactFont by remember { mutableStateOf("12") }
    var t80ContactGap by remember { mutableStateOf("6") }
    var t80Email by remember { mutableStateOf("") }
    var t80EmailFont by remember { mutableStateOf("12") }
    var t80EmailGap by remember { mutableStateOf("10") }
    var t80LastLine by remember { mutableStateOf("") }
    var t80LastLineFont by remember { mutableStateOf("10") }
    var t80LastLineGap by remember { mutableStateOf("10") }
    var t80UpperGap by remember { mutableStateOf("5") }
    var t80LeftGap by remember { mutableStateOf("1") }
    var t80LogoLeft by remember { mutableStateOf("53") }
    var t80LogoUpper by remember { mutableStateOf("10") }
    var t80LogoWidth by remember { mutableStateOf("20") }
    var t80LogoHeight by remember { mutableStateOf("20") }
    var t80BillNoLabel by remember { mutableStateOf("Bill No :") }
    var t80BillNoFont by remember { mutableStateOf("11") }
    var t80BillNoGap by remember { mutableStateOf("5") }
    var t80DateLabel by remember { mutableStateOf("Date:") }
    var t80DateFont by remember { mutableStateOf("11") }
    var t80DateGap by remember { mutableStateOf("5") }
    var t80SeparatorGap by remember { mutableStateOf("5") }
    var t80ItemHeader by remember { mutableStateOf("Item") }
    var t80PriceHeader by remember { mutableStateOf("Price") }
    var t80QtyHeader by remember { mutableStateOf("Qty") }
    var t80AmountHeader by remember { mutableStateOf("Amount") }
    var t80HeaderFont by remember { mutableStateOf("11") }
    var t80HeaderGap by remember { mutableStateOf("5") }
    var t80ItemFont by remember { mutableStateOf("10") }
    var t80ItemGap by remember { mutableStateOf("5") }
    var t80ItemNameGap by remember { mutableStateOf("5") }
    var t80GrossTotalLabel by remember { mutableStateOf("Gross Total:") }
    var t80GrossTotalFont by remember { mutableStateOf("11") }
    var t80GrossTotalGap by remember { mutableStateOf("5") }
    var t80DiscountLabel by remember { mutableStateOf("Discount:") }
    var t80DiscountFont by remember { mutableStateOf("11") }
    var t80DiscountGap by remember { mutableStateOf("6") }
    var t80NetTotalLabel by remember { mutableStateOf("Net Total:") }
    var t80NetTotalFont by remember { mutableStateOf("13") }
    var t80NetTotalGap by remember { mutableStateOf("5") }
    var t80CashLabel by remember { mutableStateOf("Cash:") }
    var t80CashFont by remember { mutableStateOf("11") }
    var t80CashGap by remember { mutableStateOf("5") }
    var t80BalanceLabel by remember { mutableStateOf("Balance:") }
    var t80BalanceFont by remember { mutableStateOf("11") }
    var t80BalanceGap by remember { mutableStateOf("5") }
    var t80ColPrice by remember { mutableStateOf("30") }
    var t80ColQty by remember { mutableStateOf("50") }
    var t80ColAmount by remember { mutableStateOf("70") }
    var t80ColHeaderPrice by remember { mutableStateOf("30") }
    var t80ColHeaderQty by remember { mutableStateOf("50") }
    var t80ColHeaderAmount by remember { mutableStateOf("70") }

    // 58mm template fields
    var t58CompanyName by remember { mutableStateOf("") }
    var t58CompanyFont by remember { mutableStateOf("14") }
    var t58CompanyGap by remember { mutableStateOf("4") }
    var t58Address by remember { mutableStateOf("") }
    var t58AddressFont by remember { mutableStateOf("10") }
    var t58AddressGap by remember { mutableStateOf("4") }
    var t58Contact by remember { mutableStateOf("") }
    var t58ContactFont by remember { mutableStateOf("10") }
    var t58ContactGap by remember { mutableStateOf("4") }
    var t58Email by remember { mutableStateOf("") }
    var t58EmailFont by remember { mutableStateOf("10") }
    var t58EmailGap by remember { mutableStateOf("8") }
    var t58LastLine by remember { mutableStateOf("") }
    var t58LastLineFont by remember { mutableStateOf("8") }
    var t58LastLineGap by remember { mutableStateOf("8") }
    var t58UpperGap by remember { mutableStateOf("3") }
    var t58LeftGap by remember { mutableStateOf("1") }
    var t58LogoLeft by remember { mutableStateOf("35") }
    var t58LogoUpper by remember { mutableStateOf("8") }
    var t58LogoWidth by remember { mutableStateOf("16") }
    var t58LogoHeight by remember { mutableStateOf("16") }
    var t58BillNoLabel by remember { mutableStateOf("Bill No :") }
    var t58BillNoFont by remember { mutableStateOf("9") }
    var t58BillNoGap by remember { mutableStateOf("3") }
    var t58DateLabel by remember { mutableStateOf("Date:") }
    var t58DateFont by remember { mutableStateOf("9") }
    var t58DateGap by remember { mutableStateOf("3") }
    var t58SeparatorGap by remember { mutableStateOf("3") }
    var t58ItemHeader by remember { mutableStateOf("Item") }
    var t58PriceHeader by remember { mutableStateOf("Price") }
    var t58QtyHeader by remember { mutableStateOf("Qty") }
    var t58AmountHeader by remember { mutableStateOf("Amount") }
    var t58HeaderFont by remember { mutableStateOf("9") }
    var t58HeaderGap by remember { mutableStateOf("3") }
    var t58ItemFont by remember { mutableStateOf("8") }
    var t58ItemGap by remember { mutableStateOf("3") }
    var t58ItemNameGap by remember { mutableStateOf("3") }
    var t58GrossTotalLabel by remember { mutableStateOf("Gross Total:") }
    var t58GrossTotalFont by remember { mutableStateOf("9") }
    var t58GrossTotalGap by remember { mutableStateOf("3") }
    var t58DiscountLabel by remember { mutableStateOf("Discount:") }
    var t58DiscountFont by remember { mutableStateOf("9") }
    var t58DiscountGap by remember { mutableStateOf("4") }
    var t58NetTotalLabel by remember { mutableStateOf("Net Total:") }
    var t58NetTotalFont by remember { mutableStateOf("11") }
    var t58NetTotalGap by remember { mutableStateOf("3") }
    var t58CashLabel by remember { mutableStateOf("Cash:") }
    var t58CashFont by remember { mutableStateOf("9") }
    var t58CashGap by remember { mutableStateOf("3") }
    var t58BalanceLabel by remember { mutableStateOf("Balance:") }
    var t58BalanceFont by remember { mutableStateOf("9") }
    var t58BalanceGap by remember { mutableStateOf("3") }
    var t58ColPrice by remember { mutableStateOf("20") }
    var t58ColQty by remember { mutableStateOf("35") }
    var t58ColAmount by remember { mutableStateOf("48") }
    var t58ColHeaderPrice by remember { mutableStateOf("20") }
    var t58ColHeaderQty by remember { mutableStateOf("35") }
    var t58ColHeaderAmount by remember { mutableStateOf("48") }

    // Get token from AppViewModel
    val token by appViewModel.token.collectAsState()

    val bluetoothService = remember { BluetoothService(context) }
    val printerService = remember { PrinterService.getInstance() }
    val apiService = remember { RetrofitClient.apiService }

    // Check if already connected on screen load
    LaunchedEffect(Unit) {
        if (printerService.isConnected()) {
            connectionStatus = "Connected to ${printerService.getConnectedDeviceName() ?: "Printer"}"
        }
    }

    // Fetch and cache bill template on screen load (only if not already cached)
    LaunchedEffect(token) {
        if (!token.isNullOrEmpty()) {
            // Check if template is already cached
            val cachedTemplate = BillTemplateCache.getTemplate(context)
            if (cachedTemplate == null) {
                // Fetch from API and cache it
                Log.d("PrinterScreen", "No cached template found, fetching from API...")
                val template = withContext(Dispatchers.IO) {
                    fetchSmallBillTemplate(token!!)
                }
                if (template != null) {
                    BillTemplateCache.saveTemplate(context, template)
                    Log.d("PrinterScreen", "Bill template fetched and cached successfully")
                } else {
                    Log.e("PrinterScreen", "Failed to fetch bill template from API")
                }
            } else {
                Log.d("PrinterScreen", "Bill template already cached")
            }
        }
    }

    // Load template data into editor fields
    LaunchedEffect(token) {
        if (!token.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                val template80 = fetchSmallBillTemplate(token!!, "android")
                val template58 = fetchSmallBillTemplate(token!!, "android58")

                template80?.let { t ->
                    t80CompanyName = t.get("companyName").safeString ?: ""
                    t80CompanyFont = (t.get("companyFont")?.asInt ?: 17).toString()
                    t80CompanyGap = (t.get("companyGap")?.asInt ?: 6).toString()
                    t80Address = t.get("address").safeString ?: ""
                    t80AddressFont = (t.get("addressFont")?.asInt ?: 12).toString()
                    t80AddressGap = (t.get("addressGap")?.asInt ?: 6).toString()
                    t80Contact = t.get("contact").safeString ?: ""
                    t80ContactFont = (t.get("contactFont")?.asInt ?: 12).toString()
                    t80ContactGap = (t.get("contactGap")?.asInt ?: 6).toString()
                    t80Email = t.get("email").safeString ?: ""
                    t80EmailFont = (t.get("emailFont")?.asInt ?: 12).toString()
                    t80EmailGap = (t.get("emailGap")?.asInt ?: 10).toString()
                    t80LastLine = t.get("lastLine").safeString ?: ""
                    t80LastLineFont = (t.get("lastLineFont")?.asInt ?: 10).toString()
                    t80LastLineGap = (t.get("lastLineGap")?.asInt ?: 10).toString()
                    t80UpperGap = (t.get("upperGap")?.asInt ?: 5).toString()
                    t80LeftGap = (t.get("leftGap")?.asInt ?: 1).toString()
                    t80LogoLeft = (t.get("logoLeft")?.asInt ?: 53).toString()
                    t80LogoUpper = (t.get("logoUpper")?.asInt ?: 10).toString()
                    t80LogoWidth = (t.get("logoWidth")?.asInt ?: 20).toString()
                    t80LogoHeight = (t.get("logoHeight")?.asInt ?: 20).toString()
                    t80BillNoLabel = t.get("billNoLabel").safeString ?: "Bill No :"
                    t80BillNoFont = (t.get("billNoFontSize")?.asInt ?: 11).toString()
                    t80BillNoGap = (t.get("billNoGap2")?.asInt ?: 5).toString()
                    t80DateLabel = t.get("dateLabel").safeString ?: "Date:"
                    t80DateFont = (t.get("dateFontSize")?.asInt ?: 11).toString()
                    t80DateGap = (t.get("dateGap2")?.asInt ?: 5).toString()
                    t80SeparatorGap = (t.get("separatorGap")?.asInt ?: 5).toString()
                    t80ItemHeader = t.get("itemHeader").safeString ?: "Item"
                    t80PriceHeader = t.get("priceHeader").safeString ?: "Price"
                    t80QtyHeader = t.get("qtyHeader").safeString ?: "Qty"
                    t80AmountHeader = t.get("totalHeader").safeString ?: "Amount"
                    t80HeaderFont = (t.get("itemHeaderFontSize")?.asInt ?: 11).toString()
                    t80HeaderGap = (t.get("headerGap2")?.asInt ?: 5).toString()
                    t80ItemFont = (t.get("itemFontSize")?.asInt ?: 10).toString()
                    t80ItemGap = (t.get("itemGap")?.asInt ?: 5).toString()
                    t80ItemNameGap = (t.get("itemNameGap")?.asInt ?: 5).toString()
                    t80GrossTotalLabel = t.get("grossTotalLabel").safeString ?: "Gross Total:"
                    t80GrossTotalFont = (t.get("grossTotalFontSize")?.asInt ?: 11).toString()
                    t80GrossTotalGap = (t.get("grossTotalGap")?.asInt ?: 5).toString()
                    t80DiscountLabel = t.get("billDiscountLabel").safeString ?: "Discount:"
                    t80DiscountFont = (t.get("billDiscountFontSize")?.asInt ?: 11).toString()
                    t80DiscountGap = (t.get("discountGap")?.asInt ?: 6).toString()
                    t80NetTotalLabel = t.get("netTotalLabel").safeString ?: "Net Total:"
                    t80NetTotalFont = (t.get("netTotalFontSize")?.asInt ?: 13).toString()
                    t80NetTotalGap = (t.get("netTotalGap")?.asInt ?: 5).toString()
                    t80CashLabel = t.get("cashPaidLabel").safeString ?: "Cash:"
                    t80CashFont = (t.get("cashPaidFontSize")?.asInt ?: 11).toString()
                    t80CashGap = (t.get("cashGap")?.asInt ?: 5).toString()
                    t80BalanceLabel = t.get("balanceLabel").safeString ?: "Balance:"
                    t80BalanceFont = (t.get("balanceFontSize")?.asInt ?: 11).toString()
                    t80BalanceGap = (t.get("balanceGap")?.asInt ?: 5).toString()
                    t80ColPrice = (t.get("colPriceMm")?.asInt ?: 30).toString()
                    t80ColQty = (t.get("colQtyMm")?.asInt ?: 50).toString()
                    t80ColAmount = (t.get("colTotalMm")?.asInt ?: 70).toString()
                    t80ColHeaderPrice = (t.get("colHeaderPriceMm")?.asInt ?: (t.get("colPriceMm")?.asInt ?: 30)).toString()
                    t80ColHeaderQty = (t.get("colHeaderQtyMm")?.asInt ?: (t.get("colQtyMm")?.asInt ?: 50)).toString()
                    t80ColHeaderAmount = (t.get("colHeaderAmountMm")?.asInt ?: (t.get("colTotalMm")?.asInt ?: 70)).toString()
                }

                template58?.let { t ->
                    t58CompanyName = t.get("companyName").safeString ?: ""
                    t58CompanyFont = (t.get("companyFont")?.asInt ?: 14).toString()
                    t58CompanyGap = (t.get("companyGap")?.asInt ?: 4).toString()
                    t58Address = t.get("address").safeString ?: ""
                    t58AddressFont = (t.get("addressFont")?.asInt ?: 10).toString()
                    t58AddressGap = (t.get("addressGap")?.asInt ?: 4).toString()
                    t58Contact = t.get("contact").safeString ?: ""
                    t58ContactFont = (t.get("contactFont")?.asInt ?: 10).toString()
                    t58ContactGap = (t.get("contactGap")?.asInt ?: 4).toString()
                    t58Email = t.get("email").safeString ?: ""
                    t58EmailFont = (t.get("emailFont")?.asInt ?: 10).toString()
                    t58EmailGap = (t.get("emailGap")?.asInt ?: 8).toString()
                    t58LastLine = t.get("lastLine").safeString ?: ""
                    t58LastLineFont = (t.get("lastLineFont")?.asInt ?: 8).toString()
                    t58LastLineGap = (t.get("lastLineGap")?.asInt ?: 8).toString()
                    t58UpperGap = (t.get("upperGap")?.asInt ?: 3).toString()
                    t58LeftGap = (t.get("leftGap")?.asInt ?: 1).toString()
                    t58LogoLeft = (t.get("logoLeft")?.asInt ?: 35).toString()
                    t58LogoUpper = (t.get("logoUpper")?.asInt ?: 8).toString()
                    t58LogoWidth = (t.get("logoWidth")?.asInt ?: 16).toString()
                    t58LogoHeight = (t.get("logoHeight")?.asInt ?: 16).toString()
                    t58BillNoLabel = t.get("billNoLabel").safeString ?: "Bill No :"
                    t58BillNoFont = (t.get("billNoFontSize")?.asInt ?: 9).toString()
                    t58BillNoGap = (t.get("billNoGap2")?.asInt ?: 3).toString()
                    t58DateLabel = t.get("dateLabel").safeString ?: "Date:"
                    t58DateFont = (t.get("dateFontSize")?.asInt ?: 9).toString()
                    t58DateGap = (t.get("dateGap2")?.asInt ?: 3).toString()
                    t58SeparatorGap = (t.get("separatorGap")?.asInt ?: 3).toString()
                    t58ItemHeader = t.get("itemHeader").safeString ?: "Item"
                    t58PriceHeader = t.get("priceHeader").safeString ?: "Price"
                    t58QtyHeader = t.get("qtyHeader").safeString ?: "Qty"
                    t58AmountHeader = t.get("totalHeader").safeString ?: "Amount"
                    t58HeaderFont = (t.get("itemHeaderFontSize")?.asInt ?: 9).toString()
                    t58HeaderGap = (t.get("headerGap2")?.asInt ?: 3).toString()
                    t58ItemFont = (t.get("itemFontSize")?.asInt ?: 8).toString()
                    t58ItemGap = (t.get("itemGap")?.asInt ?: 3).toString()
                    t58ItemNameGap = (t.get("itemNameGap")?.asInt ?: 3).toString()
                    t58GrossTotalLabel = t.get("grossTotalLabel").safeString ?: "Gross Total:"
                    t58GrossTotalFont = (t.get("grossTotalFontSize")?.asInt ?: 9).toString()
                    t58GrossTotalGap = (t.get("grossTotalGap")?.asInt ?: 3).toString()
                    t58DiscountLabel = t.get("billDiscountLabel").safeString ?: "Discount:"
                    t58DiscountFont = (t.get("billDiscountFontSize")?.asInt ?: 9).toString()
                    t58DiscountGap = (t.get("discountGap")?.asInt ?: 4).toString()
                    t58NetTotalLabel = t.get("netTotalLabel").safeString ?: "Net Total:"
                    t58NetTotalFont = (t.get("netTotalFontSize")?.asInt ?: 11).toString()
                    t58NetTotalGap = (t.get("netTotalGap")?.asInt ?: 3).toString()
                    t58CashLabel = t.get("cashPaidLabel").safeString ?: "Cash:"
                    t58CashFont = (t.get("cashPaidFontSize")?.asInt ?: 9).toString()
                    t58CashGap = (t.get("cashGap")?.asInt ?: 3).toString()
                    t58BalanceLabel = t.get("balanceLabel").safeString ?: "Balance:"
                    t58BalanceFont = (t.get("balanceFontSize")?.asInt ?: 9).toString()
                    t58BalanceGap = (t.get("balanceGap")?.asInt ?: 3).toString()
                    t58ColPrice = (t.get("colPriceMm")?.asInt ?: 20).toString()
                    t58ColQty = (t.get("colQtyMm")?.asInt ?: 35).toString()
                    t58ColAmount = (t.get("colTotalMm")?.asInt ?: 48).toString()
                    t58ColHeaderPrice = (t.get("colHeaderPriceMm")?.asInt ?: (t.get("colPriceMm")?.asInt ?: 20)).toString()
                    t58ColHeaderQty = (t.get("colHeaderQtyMm")?.asInt ?: (t.get("colQtyMm")?.asInt ?: 35)).toString()
                    t58ColHeaderAmount = (t.get("colHeaderAmountMm")?.asInt ?: (t.get("colTotalMm")?.asInt ?: 48)).toString()
                }
            }
        }
    }

    // Permission launcher for Bluetooth permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        permissionGranted = allGranted
        if (allGranted) {
            scope.launch {
                isScanning = true
                withContext(Dispatchers.Default) {
                    bluetoothService.scanForDevices()
                    devices = bluetoothService.availableDevices.value
                }
                isScanning = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Check if permissions are needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionGranted = bluetoothService.needsRuntimePermissions().not()
        } else {
            permissionGranted = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MTP-3 Printer", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F3460))
        ) {
            // Connection Status Card
            item {
                ConnectionStatusCard(
                    connectionStatus = connectionStatus,
                    selectedDevice = selectedDeviceAddress?.let { address ->
                        devices.find { it.address == address }?.name
                    },
                    onDisconnect = {
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                printerService.disconnectDevice()
                                selectedDeviceAddress = null
                                connectionStatus = "Disconnected"
                            }
                        }
                    }
                )
            }

            // Scan Section
            item {
                ScanSection(
                    isScanning = isScanning,
                    onScan = {
                        // Request permissions if needed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !permissionGranted) {
                            val requiredPermissions = bluetoothService.getRequiredPermissions()
                            permissionLauncher.launch(requiredPermissions)
                        } else {
                            scope.launch {
                                isScanning = true
                                withContext(Dispatchers.Default) {
                                    bluetoothService.scanForDevices()
                                    devices = bluetoothService.availableDevices.value
                                }
                                isScanning = false
                            }
                        }
                    }
                )
            }

            // Devices List
            if (devices.isNotEmpty()) {
                items(devices.size) { index ->
                    val device = devices[index]
                    DeviceItem(
                        device = device,
                        isSelected = device.address == selectedDeviceAddress,
                        onSelect = { selectedDevice ->
                            scope.launch {
                                connectionStatus = "Connecting..."
                                val bluetoothDevice = bluetoothService.getDeviceByAddress(selectedDevice.address)
                                if (bluetoothDevice != null) {
                                    withContext(Dispatchers.Default) {
                                        val connected = printerService.connectDevice(bluetoothDevice)
                                        if (connected) {
                                            selectedDeviceAddress = selectedDevice.address
                                            connectionStatus = "Connected to ${selectedDevice.name}"
                                        } else {
                                            connectionStatus = "Connection Failed"
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Print Section (only show when connected)
            if (selectedDeviceAddress != null && printerService.isConnected()) {
                item {
                    PrintSection(
                        printingButton = printingButton,
                        onPrintTest = {
                            scope.launch {
                                printingButton = "test"
                                withContext(Dispatchers.Default) {
                                    printerService.printTest()
                                }
                                printingButton = null
                            }
                        },
                        onPrintReceipt = {
                            scope.launch {
                                printingButton = "receipt"
                                withContext(Dispatchers.Default) {
                                    printerService.printReceipt()
                                }
                                printingButton = null
                            }
                        },
                        onPrint80mm = {
                            scope.launch {
                                printingButton = "80mm"
                                try {
                                    val template = getOrFetchTemplate(context, token, "android")
                                    printWithTemplate(context, printerService, template, 80f)
                                } catch (e: Exception) {
                                    Log.e("PrinterScreen", "Error printing 80mm: ${e.message}")
                                } finally {
                                    printingButton = null
                                }
                            }
                        },
                        onPrint58mm = {
                            scope.launch {
                                printingButton = "58mm"
                                try {
                                    val template = getOrFetchTemplate(context, token, "android58")
                                    printWithTemplate(context, printerService, template, 58f)
                                } catch (e: Exception) {
                                    Log.e("PrinterScreen", "Error printing 58mm: ${e.message}")
                                } finally {
                                    printingButton = null
                                }
                            }
                        }
                    )
                }
            }

            // 80mm Template Editor Accordion
            item {
                BillTemplateAccordion(
                    title = "Android App Bill Template (80 mm)",
                    expanded = expanded80mm,
                    onToggle = { expanded80mm = !expanded80mm },
                    isSaving = savingTemplate == "android",
                    onSave = {
                        scope.launch {
                            savingTemplate = "android"
                            val data = buildTemplateMap(
                                "android",
                                t80CompanyName, t80CompanyFont, t80CompanyGap,
                                t80Address, t80AddressFont, t80AddressGap,
                                t80Contact, t80ContactFont, t80ContactGap,
                                t80Email, t80EmailFont, t80EmailGap,
                                t80LastLine, t80LastLineFont, t80LastLineGap,
                                t80UpperGap, t80LeftGap,
                                t80LogoLeft, t80LogoUpper, t80LogoWidth, t80LogoHeight,
                                t80BillNoLabel, t80BillNoFont, t80BillNoGap,
                                t80DateLabel, t80DateFont, t80DateGap,
                                t80SeparatorGap,
                                t80ItemHeader, t80PriceHeader, t80QtyHeader, t80AmountHeader,
                                t80HeaderFont, t80HeaderGap,
                                t80ItemFont, t80ItemGap, t80ItemNameGap,
                                t80GrossTotalLabel, t80GrossTotalFont, t80GrossTotalGap,
                                t80DiscountLabel, t80DiscountFont, t80DiscountGap,
                                t80NetTotalLabel, t80NetTotalFont, t80NetTotalGap,
                                t80CashLabel, t80CashFont, t80CashGap,
                                t80BalanceLabel, t80BalanceFont, t80BalanceGap,
                                t80ColPrice, t80ColQty, t80ColAmount,
                                t80ColHeaderPrice, t80ColHeaderQty, t80ColHeaderAmount
                            )
                            val success = withContext(Dispatchers.IO) {
                                saveBillTemplate(token ?: "", data)
                            }
                            if (success) {
                                BillTemplateCache.clearTemplate(context)
                                // Print test with updated template
                                if (printerService.isConnected()) {
                                    val freshTemplate = withContext(Dispatchers.IO) {
                                        fetchSmallBillTemplate(token ?: "", "android")
                                    }
                                    if (freshTemplate != null) {
                                        BillTemplateCache.saveTemplate(context, freshTemplate, "android")
                                        printWithTemplate(context, printerService, freshTemplate, 80f)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "80mm template saved & printed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to save template", Toast.LENGTH_SHORT).show()
                                }
                            }
                            savingTemplate = null
                        }
                    },
                    companyName = t80CompanyName, onCompanyNameChange = { t80CompanyName = it },
                    companyFont = t80CompanyFont, onCompanyFontChange = { t80CompanyFont = it },
                    companyGap = t80CompanyGap, onCompanyGapChange = { t80CompanyGap = it },
                    address = t80Address, onAddressChange = { t80Address = it },
                    addressFont = t80AddressFont, onAddressFontChange = { t80AddressFont = it },
                    addressGap = t80AddressGap, onAddressGapChange = { t80AddressGap = it },
                    contact = t80Contact, onContactChange = { t80Contact = it },
                    contactFont = t80ContactFont, onContactFontChange = { t80ContactFont = it },
                    contactGap = t80ContactGap, onContactGapChange = { t80ContactGap = it },
                    email = t80Email, onEmailChange = { t80Email = it },
                    emailFont = t80EmailFont, onEmailFontChange = { t80EmailFont = it },
                    emailGap = t80EmailGap, onEmailGapChange = { t80EmailGap = it },
                    lastLine = t80LastLine, onLastLineChange = { t80LastLine = it },
                    lastLineFont = t80LastLineFont, onLastLineFontChange = { t80LastLineFont = it },
                    lastLineGap = t80LastLineGap, onLastLineGapChange = { t80LastLineGap = it },
                    upperGap = t80UpperGap, onUpperGapChange = { t80UpperGap = it },
                    leftGap = t80LeftGap, onLeftGapChange = { t80LeftGap = it },
                    logoLeft = t80LogoLeft, onLogoLeftChange = { t80LogoLeft = it },
                    logoUpper = t80LogoUpper, onLogoUpperChange = { t80LogoUpper = it },
                    logoWidth = t80LogoWidth, onLogoWidthChange = { t80LogoWidth = it },
                    logoHeight = t80LogoHeight, onLogoHeightChange = { t80LogoHeight = it },
                    billNoLabel = t80BillNoLabel, onBillNoLabelChange = { t80BillNoLabel = it },
                    billNoFont = t80BillNoFont, onBillNoFontChange = { t80BillNoFont = it },
                    billNoGap = t80BillNoGap, onBillNoGapChange = { t80BillNoGap = it },
                    dateLabel = t80DateLabel, onDateLabelChange = { t80DateLabel = it },
                    dateFont = t80DateFont, onDateFontChange = { t80DateFont = it },
                    dateGap = t80DateGap, onDateGapChange = { t80DateGap = it },
                    separatorGap = t80SeparatorGap, onSeparatorGapChange = { t80SeparatorGap = it },
                    itemHeader = t80ItemHeader, onItemHeaderChange = { t80ItemHeader = it },
                    priceHeader = t80PriceHeader, onPriceHeaderChange = { t80PriceHeader = it },
                    qtyHeader = t80QtyHeader, onQtyHeaderChange = { t80QtyHeader = it },
                    amountHeader = t80AmountHeader, onAmountHeaderChange = { t80AmountHeader = it },
                    headerFont = t80HeaderFont, onHeaderFontChange = { t80HeaderFont = it },
                    headerGap = t80HeaderGap, onHeaderGapChange = { t80HeaderGap = it },
                    itemFont = t80ItemFont, onItemFontChange = { t80ItemFont = it },
                    itemGap = t80ItemGap, onItemGapChange = { t80ItemGap = it },
                    itemNameGap = t80ItemNameGap, onItemNameGapChange = { t80ItemNameGap = it },
                    grossTotalLabel = t80GrossTotalLabel, onGrossTotalLabelChange = { t80GrossTotalLabel = it },
                    grossTotalFont = t80GrossTotalFont, onGrossTotalFontChange = { t80GrossTotalFont = it },
                    grossTotalGap = t80GrossTotalGap, onGrossTotalGapChange = { t80GrossTotalGap = it },
                    discountLabel = t80DiscountLabel, onDiscountLabelChange = { t80DiscountLabel = it },
                    discountFont = t80DiscountFont, onDiscountFontChange = { t80DiscountFont = it },
                    discountGap = t80DiscountGap, onDiscountGapChange = { t80DiscountGap = it },
                    netTotalLabel = t80NetTotalLabel, onNetTotalLabelChange = { t80NetTotalLabel = it },
                    netTotalFont = t80NetTotalFont, onNetTotalFontChange = { t80NetTotalFont = it },
                    netTotalGap = t80NetTotalGap, onNetTotalGapChange = { t80NetTotalGap = it },
                    cashLabel = t80CashLabel, onCashLabelChange = { t80CashLabel = it },
                    cashFont = t80CashFont, onCashFontChange = { t80CashFont = it },
                    cashGap = t80CashGap, onCashGapChange = { t80CashGap = it },
                    balanceLabel = t80BalanceLabel, onBalanceLabelChange = { t80BalanceLabel = it },
                    balanceFont = t80BalanceFont, onBalanceFontChange = { t80BalanceFont = it },
                    balanceGap = t80BalanceGap, onBalanceGapChange = { t80BalanceGap = it },
                    colPrice = t80ColPrice, onColPriceChange = { t80ColPrice = it },
                    colQty = t80ColQty, onColQtyChange = { t80ColQty = it },
                    colAmount = t80ColAmount, onColAmountChange = { t80ColAmount = it },
                    colHeaderPrice = t80ColHeaderPrice, onColHeaderPriceChange = { t80ColHeaderPrice = it },
                    colHeaderQty = t80ColHeaderQty, onColHeaderQtyChange = { t80ColHeaderQty = it },
                    colHeaderAmount = t80ColHeaderAmount, onColHeaderAmountChange = { t80ColHeaderAmount = it }
                )
            }

            // 58mm Template Editor Accordion
            item {
                BillTemplateAccordion(
                    title = "Android App Bill Template (58 mm)",
                    expanded = expanded58mm,
                    onToggle = { expanded58mm = !expanded58mm },
                    isSaving = savingTemplate == "android58",
                    onSave = {
                        scope.launch {
                            savingTemplate = "android58"
                            val data = buildTemplateMap(
                                "android58",
                                t58CompanyName, t58CompanyFont, t58CompanyGap,
                                t58Address, t58AddressFont, t58AddressGap,
                                t58Contact, t58ContactFont, t58ContactGap,
                                t58Email, t58EmailFont, t58EmailGap,
                                t58LastLine, t58LastLineFont, t58LastLineGap,
                                t58UpperGap, t58LeftGap,
                                t58LogoLeft, t58LogoUpper, t58LogoWidth, t58LogoHeight,
                                t58BillNoLabel, t58BillNoFont, t58BillNoGap,
                                t58DateLabel, t58DateFont, t58DateGap,
                                t58SeparatorGap,
                                t58ItemHeader, t58PriceHeader, t58QtyHeader, t58AmountHeader,
                                t58HeaderFont, t58HeaderGap,
                                t58ItemFont, t58ItemGap, t58ItemNameGap,
                                t58GrossTotalLabel, t58GrossTotalFont, t58GrossTotalGap,
                                t58DiscountLabel, t58DiscountFont, t58DiscountGap,
                                t58NetTotalLabel, t58NetTotalFont, t58NetTotalGap,
                                t58CashLabel, t58CashFont, t58CashGap,
                                t58BalanceLabel, t58BalanceFont, t58BalanceGap,
                                t58ColPrice, t58ColQty, t58ColAmount,
                                t58ColHeaderPrice, t58ColHeaderQty, t58ColHeaderAmount
                            )
                            val success = withContext(Dispatchers.IO) {
                                saveBillTemplate(token ?: "", data)
                            }
                            if (success) {
                                BillTemplateCache.clearTemplate(context)
                                // Print test with updated template
                                if (printerService.isConnected()) {
                                    val freshTemplate = withContext(Dispatchers.IO) {
                                        fetchSmallBillTemplate(token ?: "", "android58")
                                    }
                                    if (freshTemplate != null) {
                                        BillTemplateCache.saveTemplate(context, freshTemplate, "android58")
                                        printWithTemplate(context, printerService, freshTemplate, 58f)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "58mm template saved & printed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Failed to save template", Toast.LENGTH_SHORT).show()
                                }
                            }
                            savingTemplate = null
                        }
                    },
                    companyName = t58CompanyName, onCompanyNameChange = { t58CompanyName = it },
                    companyFont = t58CompanyFont, onCompanyFontChange = { t58CompanyFont = it },
                    companyGap = t58CompanyGap, onCompanyGapChange = { t58CompanyGap = it },
                    address = t58Address, onAddressChange = { t58Address = it },
                    addressFont = t58AddressFont, onAddressFontChange = { t58AddressFont = it },
                    addressGap = t58AddressGap, onAddressGapChange = { t58AddressGap = it },
                    contact = t58Contact, onContactChange = { t58Contact = it },
                    contactFont = t58ContactFont, onContactFontChange = { t58ContactFont = it },
                    contactGap = t58ContactGap, onContactGapChange = { t58ContactGap = it },
                    email = t58Email, onEmailChange = { t58Email = it },
                    emailFont = t58EmailFont, onEmailFontChange = { t58EmailFont = it },
                    emailGap = t58EmailGap, onEmailGapChange = { t58EmailGap = it },
                    lastLine = t58LastLine, onLastLineChange = { t58LastLine = it },
                    lastLineFont = t58LastLineFont, onLastLineFontChange = { t58LastLineFont = it },
                    lastLineGap = t58LastLineGap, onLastLineGapChange = { t58LastLineGap = it },
                    upperGap = t58UpperGap, onUpperGapChange = { t58UpperGap = it },
                    leftGap = t58LeftGap, onLeftGapChange = { t58LeftGap = it },
                    logoLeft = t58LogoLeft, onLogoLeftChange = { t58LogoLeft = it },
                    logoUpper = t58LogoUpper, onLogoUpperChange = { t58LogoUpper = it },
                    logoWidth = t58LogoWidth, onLogoWidthChange = { t58LogoWidth = it },
                    logoHeight = t58LogoHeight, onLogoHeightChange = { t58LogoHeight = it },
                    billNoLabel = t58BillNoLabel, onBillNoLabelChange = { t58BillNoLabel = it },
                    billNoFont = t58BillNoFont, onBillNoFontChange = { t58BillNoFont = it },
                    billNoGap = t58BillNoGap, onBillNoGapChange = { t58BillNoGap = it },
                    dateLabel = t58DateLabel, onDateLabelChange = { t58DateLabel = it },
                    dateFont = t58DateFont, onDateFontChange = { t58DateFont = it },
                    dateGap = t58DateGap, onDateGapChange = { t58DateGap = it },
                    separatorGap = t58SeparatorGap, onSeparatorGapChange = { t58SeparatorGap = it },
                    itemHeader = t58ItemHeader, onItemHeaderChange = { t58ItemHeader = it },
                    priceHeader = t58PriceHeader, onPriceHeaderChange = { t58PriceHeader = it },
                    qtyHeader = t58QtyHeader, onQtyHeaderChange = { t58QtyHeader = it },
                    amountHeader = t58AmountHeader, onAmountHeaderChange = { t58AmountHeader = it },
                    headerFont = t58HeaderFont, onHeaderFontChange = { t58HeaderFont = it },
                    headerGap = t58HeaderGap, onHeaderGapChange = { t58HeaderGap = it },
                    itemFont = t58ItemFont, onItemFontChange = { t58ItemFont = it },
                    itemGap = t58ItemGap, onItemGapChange = { t58ItemGap = it },
                    itemNameGap = t58ItemNameGap, onItemNameGapChange = { t58ItemNameGap = it },
                    grossTotalLabel = t58GrossTotalLabel, onGrossTotalLabelChange = { t58GrossTotalLabel = it },
                    grossTotalFont = t58GrossTotalFont, onGrossTotalFontChange = { t58GrossTotalFont = it },
                    grossTotalGap = t58GrossTotalGap, onGrossTotalGapChange = { t58GrossTotalGap = it },
                    discountLabel = t58DiscountLabel, onDiscountLabelChange = { t58DiscountLabel = it },
                    discountFont = t58DiscountFont, onDiscountFontChange = { t58DiscountFont = it },
                    discountGap = t58DiscountGap, onDiscountGapChange = { t58DiscountGap = it },
                    netTotalLabel = t58NetTotalLabel, onNetTotalLabelChange = { t58NetTotalLabel = it },
                    netTotalFont = t58NetTotalFont, onNetTotalFontChange = { t58NetTotalFont = it },
                    netTotalGap = t58NetTotalGap, onNetTotalGapChange = { t58NetTotalGap = it },
                    cashLabel = t58CashLabel, onCashLabelChange = { t58CashLabel = it },
                    cashFont = t58CashFont, onCashFontChange = { t58CashFont = it },
                    cashGap = t58CashGap, onCashGapChange = { t58CashGap = it },
                    balanceLabel = t58BalanceLabel, onBalanceLabelChange = { t58BalanceLabel = it },
                    balanceFont = t58BalanceFont, onBalanceFontChange = { t58BalanceFont = it },
                    balanceGap = t58BalanceGap, onBalanceGapChange = { t58BalanceGap = it },
                    colPrice = t58ColPrice, onColPriceChange = { t58ColPrice = it },
                    colQty = t58ColQty, onColQtyChange = { t58ColQty = it },
                    colAmount = t58ColAmount, onColAmountChange = { t58ColAmount = it },
                    colHeaderPrice = t58ColHeaderPrice, onColHeaderPriceChange = { t58ColHeaderPrice = it },
                    colHeaderQty = t58ColHeaderQty, onColHeaderQtyChange = { t58ColHeaderQty = it },
                    colHeaderAmount = t58ColHeaderAmount, onColHeaderAmountChange = { t58ColHeaderAmount = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    connectionStatus: String,
    selectedDevice: String?,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionStatus.contains("Connected", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0x1A4CAF50) else Color(0x1AFF9800)
        ),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.foundation.BorderStroke(
                2.dp,
                if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800)
            ).brush
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) "Connected" else "Disconnected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (selectedDevice != null) {
                    Text(
                        text = selectedDevice,
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = connectionStatus,
                        fontSize = 14.sp,
                        color = Color(0xFFCCCCCC),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (isConnected) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
fun ScanSection(
    isScanning: Boolean,
    onScan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1F2196F3)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔍 Scan for Printers",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = onScan,
                enabled = !isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scanning...", color = Color.White)
                } else {
                    Text("Scan for Printers", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DevicesList(
    devices: List<BluetoothDeviceInfo>,
    selectedDeviceAddress: String?,
    onDeviceSelected: (BluetoothDeviceInfo) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1FFFFFFF).copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Found Devices (${devices.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        isSelected = selectedDeviceAddress == device.address,
                        onSelect = { onDeviceSelected(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDeviceInfo,
    isSelected: Boolean,
    onSelect: (BluetoothDeviceInfo) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp, 16.dp, 8.dp)
            .clickable { onSelect(device) }
            .background(
                if (isSelected) Color(0x1A4CAF50) else Color(0x1AFFFFFF).copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.material3.CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isSelected) Color(0xFF4CAF50) else Color(0x1AFFFFFF)
            ).brush
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = Color(0xFFCCCCCC),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isSelected) {
                Text(
                    text = "✓ Connected",
                    fontSize = 12.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PrintSection(
    printingButton: String?,
    onPrintTest: () -> Unit,
    onPrintReceipt: () -> Unit,
    onPrint80mm: () -> Unit = {},
    onPrint58mm: () -> Unit = {}
) {
    val isPrinting = printingButton != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1F4CAF50)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📄 Printing",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = onPrintTest,
                enabled = !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                )
            ) {
                if (printingButton == "test") {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Print Test Page", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onPrintReceipt,
                enabled = !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                if (printingButton == "receipt") {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Print Receipt", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onPrint80mm,
                enabled = !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                if (printingButton == "80mm") {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Print 80 mm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = onPrint58mm,
                enabled = !isPrinting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                )
            ) {
                if (printingButton == "58mm") {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Print 58 mm", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Fetch or retrieve cached template for a given bill type
 */
private suspend fun getOrFetchTemplate(
    context: Context,
    token: String?,
    billType: String
): com.google.gson.JsonObject? {
    var template = BillTemplateCache.getTemplate(context, billType)

    if (template == null && !token.isNullOrEmpty()) {
        Log.d("PrinterScreen", "No cached '$billType' template, fetching from API...")
        template = withContext(Dispatchers.IO) {
            fetchSmallBillTemplate(token!!, billType)
        }
        if (template != null) {
            BillTemplateCache.saveTemplate(context, template, billType)
            Log.d("PrinterScreen", "'$billType' bill template fetched and cached")
        }
    }
    return template
}

/**
 * Print using a bill template with the specified paper width
 */
private suspend fun printWithTemplate(
    context: Context,
    printerService: PrinterService,
    template: com.google.gson.JsonObject?,
    paperWidthMm: Float
) {
    if (template != null) {
        val companyName = template.get("companyName").safeString ?: "COSMOS POS"
        val companyNameFont = template.get("companyFont")?.asInt ?: 14
        val companyGap = template.get("companyGap")?.asInt ?: 0
        val address = template.get("address").safeString ?: ""
        val addressFont = template.get("addressFont")?.asInt ?: 12
        val addressGap = template.get("addressGap")?.asInt ?: 0
        val contact = template.get("contact").safeString ?: ""
        val contactFont = template.get("contactFont")?.asInt ?: 12
        val contactGap = template.get("contactGap")?.asInt ?: 0
        val email = template.get("email").safeString ?: ""
        val emailFont = template.get("emailFont")?.asInt ?: 12
        val emailGap = template.get("emailGap")?.asInt ?: 0
        val lastLine = template.get("lastLine").safeString ?: ""
        val lastLineFont = template.get("lastLineFont")?.asInt ?: 10
        val lastLineGap = template.get("lastLineGap")?.asInt ?: 0
        val upperGap = template.get("upperGap")?.asInt ?: 0
        val leftGap = template.get("leftGap")?.asInt ?: 0
        val logoDataUrl = template.get("logoDataURL").safeString ?: ""
        val logoLeft = template.get("logoLeft")?.asInt ?: 0
        val logoUpper = template.get("logoUpper")?.asInt ?: 0
        val logoWidth = template.get("logoWidth")?.asInt ?: 0
        val logoHeight = template.get("logoHeight")?.asInt ?: 0

        // Bill detail fields
        val billNoLabel = template.get("billNoLabel").safeString ?: "Bill No :"
        val dateLabel = template.get("dateLabel").safeString ?: "Date:"
        val itemHeaderLabel = template.get("itemHeader").safeString ?: "Item"
        val priceHeaderLabel = template.get("priceHeader").safeString ?: "Price"
        val qtyHeaderLabel = template.get("qtyHeader").safeString ?: "Qty"
        val amountHeaderLabel = template.get("totalHeader").safeString ?: "Amount"
        val grossTotalLabel = template.get("grossTotalLabel").safeString ?: "Gross Total:"
        val discountLabel = template.get("billDiscountLabel").safeString ?: "Discount:"
        val netTotalLabel = template.get("netTotalLabel").safeString ?: "Net Total:"
        val cashLabel = template.get("cashPaidLabel").safeString ?: "Cash:"
        val balanceLabel = template.get("balanceLabel").safeString ?: "Balance:"
        val billNoFontSize = template.get("billNoFontSize")?.asFloat ?: 11f
        val dateFontSize = template.get("dateFontSize")?.asFloat ?: 11f
        val headerFontSize = template.get("itemHeaderFontSize")?.asFloat ?: 11f
        val itemFontSizeVal = template.get("itemFontSize")?.asFloat ?: 10f
        val grossTotalFontSize = template.get("grossTotalFontSize")?.asFloat ?: 11f
        val discountFontSize = template.get("billDiscountFontSize")?.asFloat ?: 11f
        val netTotalFontSize = template.get("netTotalFontSize")?.asFloat ?: 13f
        val cashFontSize = template.get("cashPaidFontSize")?.asFloat ?: 11f
        val balanceFontSize = template.get("balanceFontSize")?.asFloat ?: 11f
        val billNoGapVal = template.get("billNoGap2")?.asFloat ?: 5f
        val dateGapVal = template.get("dateGap2")?.asFloat ?: 5f
        val separatorGapVal = template.get("separatorGap")?.asFloat ?: 5f
        val headerGapVal = template.get("headerGap2")?.asFloat ?: 5f
        val itemNameGapVal = template.get("itemNameGap")?.asFloat ?: 5f
        val itemGapVal = template.get("itemGap")?.asFloat ?: 5f
        val grossTotalGapVal = template.get("grossTotalGap")?.asFloat ?: 5f
        val discountGapVal = template.get("discountGap")?.asFloat ?: 6f
        val netTotalGapVal = template.get("netTotalGap")?.asFloat ?: 5f
        val cashGapVal = template.get("cashGap")?.asFloat ?: 5f
        val balanceGapVal = template.get("balanceGap")?.asFloat ?: 5f
        val colPriceMmVal = template.get("colPriceMm")?.asFloat ?: 30f
        val colQtyMmVal = template.get("colQtyMm")?.asFloat ?: 50f
        val colAmountMmVal = template.get("colTotalMm")?.asFloat ?: 70f
        // Header column positions (separate from item detail positions)
        val colHeaderPriceMmVal = template.get("colHeaderPriceMm")?.asFloat ?: colPriceMmVal
        val colHeaderQtyMmVal = template.get("colHeaderQtyMm")?.asFloat ?: colQtyMmVal
        val colHeaderAmountMmVal = template.get("colHeaderAmountMm")?.asFloat ?: colAmountMmVal

        Log.d("PrinterScreen", "Using ${paperWidthMm.toInt()}mm template - Company: $companyName")

        val sampleItems = listOf(
            ReceiptItem("Panadol Extra 500mg", 2.0, 45.00, 90.00),
            ReceiptItem("Vitamin C 1000mg", 1.0, 120.00, 120.00),
            ReceiptItem("Cough Syrup 100ml", 1.0, 350.00, 350.00),
            ReceiptItem("Band Aid Pack", 3.0, 85.00, 255.00)
        )
        val sampleGrossTotal = sampleItems.sumOf { it.amount }
        val sampleDiscount = 50.0
        val sampleNetTotal = sampleGrossTotal - sampleDiscount
        val sampleCash = 1000.0

        val receiptData = ReceiptData(
            items = sampleItems,
            customer = "Sample Customer",
            salesRep = "John",
            grossTotal = sampleGrossTotal,
            discount = sampleDiscount,
            netTotal = sampleNetTotal,
            cashPaid = sampleCash,
            billNo = "1025",
            companyName = companyName,
            address = address,
            contact = contact,
            email = email,
            companyNameFont = companyNameFont,
            addressFont = addressFont,
            contactFont = contactFont,
            emailFont = emailFont,
            lastLineFont = lastLineFont,
            companyGap = companyGap,
            addressGap = addressGap,
            contactGap = contactGap,
            emailGap = emailGap,
            lastLineGap = lastLineGap,
            upperGap = upperGap,
            leftGap = leftGap,
            logoDataUrl = logoDataUrl,
            logoLeft = logoLeft,
            logoUpper = logoUpper,
            logoWidth = logoWidth,
            logoHeight = logoHeight,
            lastLine = lastLine,
            billNoLabel = billNoLabel,
            dateLabel = dateLabel,
            itemHeader = itemHeaderLabel,
            priceHeader = priceHeaderLabel,
            qtyHeader = qtyHeaderLabel,
            amountHeader = amountHeaderLabel,
            grossTotalLabel = grossTotalLabel,
            discountLabel = discountLabel,
            netTotalLabel = netTotalLabel,
            cashLabel = cashLabel,
            balanceLabel = balanceLabel,
            billNoFontSize = billNoFontSize,
            dateFontSize = dateFontSize,
            headerFontSize = headerFontSize,
            itemFontSize = itemFontSizeVal,
            grossTotalFontSize = grossTotalFontSize,
            discountFontSize = discountFontSize,
            netTotalFontSize = netTotalFontSize,
            cashFontSize = cashFontSize,
            balanceFontSize = balanceFontSize,
            billNoGap = billNoGapVal,
            dateGap = dateGapVal,
            separatorGap = separatorGapVal,
            headerGap = headerGapVal,
            itemNameGap = itemNameGapVal,
            itemGap = itemGapVal,
            grossTotalGap = grossTotalGapVal,
            discountGap = discountGapVal,
            netTotalGap = netTotalGapVal,
            cashGapMm = cashGapVal,
            balanceGap = balanceGapVal,
            colPriceMm = colPriceMmVal,
            colQtyMm = colQtyMmVal,
            colAmountMm = colAmountMmVal,
            colHeaderPriceMm = colHeaderPriceMmVal,
            colHeaderQtyMm = colHeaderQtyMmVal,
            colHeaderAmountMm = colHeaderAmountMmVal
        )

        val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
        val success = withContext(Dispatchers.Default) {
            printerService.printPdfReceipt(pdfGenerator, receiptData)
        }
        if (success) {
            Log.d("PrinterScreen", "${paperWidthMm.toInt()}mm PDF receipt printed successfully")
        } else {
            Log.e("PrinterScreen", "Failed to print ${paperWidthMm.toInt()}mm PDF receipt")
        }
    } else {
        Log.e("PrinterScreen", "No bill template available - using sample data")
        val pdfGenerator = PdfReceiptGenerator(context, paperWidthMm)
        val receiptData = buildSampleReceiptData()
        withContext(Dispatchers.Default) {
            printerService.printPdfReceipt(pdfGenerator, receiptData)
        }
    }
}

/**
 * Build receipt data from sample/test data (for PrinterScreen testing)
 */
private fun buildSampleReceiptData(): ReceiptData {
    val items = listOf(
        ReceiptItem("Atlas 80 Pages", 2.0, 50.00, 100.00),
        ReceiptItem("Pen Blue", 5.0, 15.00, 75.00),
        ReceiptItem("Notebook A4", 1.0, 120.00, 120.00)
    )

    return ReceiptData(
        items = items,
        customer = "Sample Customer",
        salesRep = "John Doe",
        grossTotal = 295.00,
        discount = 29.50,
        netTotal = 265.50,
        cashPaid = 300.00,
        companyName = "COSMOS POS",
        address = "123 Main Street, City",
        contact = "(555) 123-4567",
        email = "info@cosmos.com"
    )
}

/**
 * Build receipt data from actual bill details (use in CashBillScreen and CreditBillScreen)
 * @param billItems List of items in the bill
 * @param customer Customer name
 * @param salesRep Sales representative name
 * @param discount Discount amount
 * @param netTotal Net total after discount
 * @param cashPaid Cash paid (0 for credit bills)
 * @param companyName Company/store name
 * @param address Store address
 * @param contact Contact number
 * @param email Email address
 */
private fun buildReceiptDataFromBill(
    billItems: List<BillItem>,
    customer: String,
    salesRep: String,
    discount: Double,
    netTotal: Double,
    cashPaid: Double,
    billNo: String = "",
    companyName: String = "COSMOS POS",
    address: String = "",
    contact: String = "",
    email: String = "",
    companyNameFont: Int = 14,
    addressFont: Int = 8,
    contactFont: Int = 8,
    emailFont: Int = 8,
    lastLineFont: Int = 10,
    companyGap: Int = 0,
    addressGap: Int = 0,
    contactGap: Int = 0,
    emailGap: Int = 0,
    lastLineGap: Int = 0,
    upperGap: Int = 0,
    leftGap: Int = 0,
    logoDataUrl: String = "",
    logoLeft: Int = 0,
    logoUpper: Int = 0,
    logoWidth: Int = 0,
    logoHeight: Int = 0,
    lastLine: String = ""
): ReceiptData {
    val grossTotal = billItems.sumOf { it.amount }

    return ReceiptData(
        items = billItems.map { item ->
            ReceiptItem(
                name = item.name,
                quantity = item.quantity.toDouble(),
                price = item.sellingPrice,
                amount = item.amount
            )
        },
        customer = customer.ifEmpty { "Walk-in" },
        salesRep = salesRep.ifEmpty { "N/A" },
        grossTotal = grossTotal,
        discount = discount,
        netTotal = netTotal,
        cashPaid = cashPaid,
        billNo = billNo,
        companyName = companyName,
        address = address,
        contact = contact,
        email = email,
        companyNameFont = companyNameFont,
        addressFont = addressFont,
        contactFont = contactFont,
        emailFont = emailFont,
        lastLineFont = lastLineFont,
        companyGap = companyGap,
        addressGap = addressGap,
        contactGap = contactGap,
        emailGap = emailGap,
        lastLineGap = lastLineGap,
        upperGap = upperGap,
        leftGap = leftGap,
        logoDataUrl = logoDataUrl,
        logoLeft = logoLeft,
        logoUpper = logoUpper,
        logoWidth = logoWidth,
        logoHeight = logoHeight,
        lastLine = lastLine
    )
}

/**
 * Fetch bill template from API and extract the "android" bill type
 *
 * ENDPOINT:
 * - GET ${ApiConfig.BASE_URL_8080}/bill/bill-templates
 *   Headers: Authorization (Bearer token), Celonis-Auth (totally_secret)
 *
 * RESPONSE FORMAT (array):
 * [
 *   {
 *     "id": 8,
 *     "billType": "android",
 *     "companyName": "JAYANTHA HARDWARE",
 *     "address": "Gohagoda Road, Kandy",
 *     "contact": "0714069926",
 *     "email": "contact@cosmos.com",
 *     ...
 *   },
 *   { "billType": "large", ... },
 *   ...
 * ]
 */
private suspend fun fetchSmallBillTemplate(
    token: String,
    targetBillType: String = "android"
): com.google.gson.JsonObject? {
    return try {
        val connection = java.net.URL("${ApiConfig.BASE_URL_8080}/bill/bill-templates")
            .openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val gson = com.google.gson.Gson()
            val allTemplates = gson.fromJson(content, com.google.gson.JsonArray::class.java)

            var smallTemplate: com.google.gson.JsonObject? = null
            if (allTemplates != null && allTemplates.size() > 0) {
                // First try to find the template with the target billType
                for (i in 0 until allTemplates.size()) {
                    val template = allTemplates.get(i).asJsonObject
                    val billType = template.get("billType").safeString
                    if (billType == targetBillType) {
                        Log.d("BillTemplate", "Fetched '$targetBillType' bill template successfully")
                        Log.d("BillTemplate", "Company: ${template.get("companyName").safeString}")
                        smallTemplate = template
                        break
                    }
                }
                // Fallback to "android" then "small" template if target not found
                if (smallTemplate == null && targetBillType != "android") {
                    Log.d("BillTemplate", "'$targetBillType' template not found, falling back to 'android' template")
                    for (i in 0 until allTemplates.size()) {
                        val template = allTemplates.get(i).asJsonObject
                        val billType = template.get("billType").safeString
                        if (billType == "android") {
                            smallTemplate = template
                            break
                        }
                    }
                }
                if (smallTemplate == null) {
                    Log.d("BillTemplate", "Android template not found, falling back to 'small' template")
                    for (i in 0 until allTemplates.size()) {
                        val template = allTemplates.get(i).asJsonObject
                        val billType = template.get("billType").safeString
                        if (billType == "small") {
                            Log.d("BillTemplate", "Fetched 'small' bill template as fallback")
                            Log.d("BillTemplate", "Company: ${template.get("companyName").safeString}")
                            smallTemplate = template
                            break
                        }
                    }
                }
                if (smallTemplate == null) {
                    Log.e("BillTemplate", "No bill template found")
                }
            } else {
                Log.e("BillTemplate", "Empty response from bill templates API")
            }
            smallTemplate
        } else {
            Log.e("BillTemplate", "bill/bill-templates request failed with response code: ${connection.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.e("BillTemplate", "Exception in fetchSmallBillTemplate: ${e.message}")
        e.printStackTrace()
        null
    }
}

// ---- Template Editor Composables ----

@Composable
fun TemplateTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFB39DDB),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color.White
            ),
            singleLine = true
        )
    }
}

@Composable
fun RowScope.TemplateNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onValueChange(it) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFB39DDB),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun TemplateSectionHeader(text: String) {
    Text(
        text = text,
        color = Color(0xFFB39DDB),
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}

@Composable
fun TemplateLabelFontGapRow(
    label: String, labelValue: String, onLabelChange: (String) -> Unit,
    fontValue: String, onFontChange: (String) -> Unit,
    gapValue: String, onGapChange: (String) -> Unit
) {
    TemplateTextField(label, labelValue, onLabelChange)
    Row(modifier = Modifier.fillMaxWidth()) {
        TemplateNumberField("Font Size", fontValue, onFontChange)
        TemplateNumberField("Gap (mm)", gapValue, onGapChange)
    }
}

@Composable
fun BillTemplateAccordion(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit,
    companyName: String, onCompanyNameChange: (String) -> Unit,
    companyFont: String, onCompanyFontChange: (String) -> Unit,
    companyGap: String, onCompanyGapChange: (String) -> Unit,
    address: String, onAddressChange: (String) -> Unit,
    addressFont: String, onAddressFontChange: (String) -> Unit,
    addressGap: String, onAddressGapChange: (String) -> Unit,
    contact: String, onContactChange: (String) -> Unit,
    contactFont: String, onContactFontChange: (String) -> Unit,
    contactGap: String, onContactGapChange: (String) -> Unit,
    email: String, onEmailChange: (String) -> Unit,
    emailFont: String, onEmailFontChange: (String) -> Unit,
    emailGap: String, onEmailGapChange: (String) -> Unit,
    lastLine: String, onLastLineChange: (String) -> Unit,
    lastLineFont: String, onLastLineFontChange: (String) -> Unit,
    lastLineGap: String, onLastLineGapChange: (String) -> Unit,
    upperGap: String, onUpperGapChange: (String) -> Unit,
    leftGap: String, onLeftGapChange: (String) -> Unit,
    logoLeft: String, onLogoLeftChange: (String) -> Unit,
    logoUpper: String, onLogoUpperChange: (String) -> Unit,
    logoWidth: String, onLogoWidthChange: (String) -> Unit,
    logoHeight: String, onLogoHeightChange: (String) -> Unit,
    billNoLabel: String, onBillNoLabelChange: (String) -> Unit,
    billNoFont: String, onBillNoFontChange: (String) -> Unit,
    billNoGap: String, onBillNoGapChange: (String) -> Unit,
    dateLabel: String, onDateLabelChange: (String) -> Unit,
    dateFont: String, onDateFontChange: (String) -> Unit,
    dateGap: String, onDateGapChange: (String) -> Unit,
    separatorGap: String, onSeparatorGapChange: (String) -> Unit,
    itemHeader: String, onItemHeaderChange: (String) -> Unit,
    priceHeader: String, onPriceHeaderChange: (String) -> Unit,
    qtyHeader: String, onQtyHeaderChange: (String) -> Unit,
    amountHeader: String, onAmountHeaderChange: (String) -> Unit,
    headerFont: String, onHeaderFontChange: (String) -> Unit,
    headerGap: String, onHeaderGapChange: (String) -> Unit,
    itemFont: String, onItemFontChange: (String) -> Unit,
    itemGap: String, onItemGapChange: (String) -> Unit,
    itemNameGap: String, onItemNameGapChange: (String) -> Unit,
    grossTotalLabel: String, onGrossTotalLabelChange: (String) -> Unit,
    grossTotalFont: String, onGrossTotalFontChange: (String) -> Unit,
    grossTotalGap: String, onGrossTotalGapChange: (String) -> Unit,
    discountLabel: String, onDiscountLabelChange: (String) -> Unit,
    discountFont: String, onDiscountFontChange: (String) -> Unit,
    discountGap: String, onDiscountGapChange: (String) -> Unit,
    netTotalLabel: String, onNetTotalLabelChange: (String) -> Unit,
    netTotalFont: String, onNetTotalFontChange: (String) -> Unit,
    netTotalGap: String, onNetTotalGapChange: (String) -> Unit,
    cashLabel: String, onCashLabelChange: (String) -> Unit,
    cashFont: String, onCashFontChange: (String) -> Unit,
    cashGap: String, onCashGapChange: (String) -> Unit,
    balanceLabel: String, onBalanceLabelChange: (String) -> Unit,
    balanceFont: String, onBalanceFontChange: (String) -> Unit,
    balanceGap: String, onBalanceGapChange: (String) -> Unit,
    colPrice: String, onColPriceChange: (String) -> Unit,
    colQty: String, onColQtyChange: (String) -> Unit,
    colAmount: String, onColAmountChange: (String) -> Unit,
    colHeaderPrice: String, onColHeaderPriceChange: (String) -> Unit,
    colHeaderQty: String, onColHeaderQtyChange: (String) -> Unit,
    colHeaderAmount: String, onColHeaderAmountChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1F9C27B0))
    ) {
        Column {
            // Clickable header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (expanded) "▲" else "▼", color = Color.White, fontSize = 16.sp)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {

                    // --- Company Information ---
                    TemplateSectionHeader("Company Information")
                    TemplateLabelFontGapRow("Company Name", companyName, onCompanyNameChange, companyFont, onCompanyFontChange, companyGap, onCompanyGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Address", address, onAddressChange, addressFont, onAddressFontChange, addressGap, onAddressGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Contact", contact, onContactChange, contactFont, onContactFontChange, contactGap, onContactGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Email", email, onEmailChange, emailFont, onEmailFontChange, emailGap, onEmailGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Last Line", lastLine, onLastLineChange, lastLineFont, onLastLineFontChange, lastLineGap, onLastLineGapChange)

                    // --- Document Settings ---
                    TemplateSectionHeader("Document Settings")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Upper Gap", upperGap, onUpperGapChange)
                        TemplateNumberField("Left Gap", leftGap, onLeftGapChange)
                    }

                    // --- Logo Positioning ---
                    TemplateSectionHeader("Logo Positioning")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Left", logoLeft, onLogoLeftChange)
                        TemplateNumberField("Upper", logoUpper, onLogoUpperChange)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Width", logoWidth, onLogoWidthChange)
                        TemplateNumberField("Height", logoHeight, onLogoHeightChange)
                    }

                    // --- Bill Details ---
                    TemplateSectionHeader("Bill Details")
                    TemplateLabelFontGapRow("Bill No Label", billNoLabel, onBillNoLabelChange, billNoFont, onBillNoFontChange, billNoGap, onBillNoGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Date Label", dateLabel, onDateLabelChange, dateFont, onDateFontChange, dateGap, onDateGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Separator Gap", separatorGap, onSeparatorGapChange)
                    }

                    // --- Item Table Headers ---
                    TemplateSectionHeader("Item Table Headers")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("Item Header", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            OutlinedTextField(
                                value = itemHeader, onValueChange = onItemHeaderChange,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB39DDB), unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = Color.White),
                                singleLine = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("Price Header", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            OutlinedTextField(
                                value = priceHeader, onValueChange = onPriceHeaderChange,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB39DDB), unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = Color.White),
                                singleLine = true
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("Qty Header", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            OutlinedTextField(
                                value = qtyHeader, onValueChange = onQtyHeaderChange,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB39DDB), unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = Color.White),
                                singleLine = true
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text("Amount Header", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                            OutlinedTextField(
                                value = amountHeader, onValueChange = onAmountHeaderChange,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFB39DDB), unfocusedBorderColor = Color.White.copy(alpha = 0.3f), cursorColor = Color.White),
                                singleLine = true
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Header Font", headerFont, onHeaderFontChange)
                        TemplateNumberField("Header Gap", headerGap, onHeaderGapChange)
                    }

                    // --- Item Settings ---
                    TemplateSectionHeader("Item Settings")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Item Font", itemFont, onItemFontChange)
                        TemplateNumberField("Item Gap", itemGap, onItemGapChange)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Name Gap (mm)", itemNameGap, onItemNameGapChange)
                    }

                    // --- Item Column Positions (mm) ---
                    TemplateSectionHeader("Item Column Positions (mm)")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Price Col", colPrice, onColPriceChange)
                        TemplateNumberField("Qty Col", colQty, onColQtyChange)
                        TemplateNumberField("Amount Col", colAmount, onColAmountChange)
                    }

                    // --- Header Column Positions (mm) ---
                    TemplateSectionHeader("Header Column Positions (mm)")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TemplateNumberField("Price Col", colHeaderPrice, onColHeaderPriceChange)
                        TemplateNumberField("Qty Col", colHeaderQty, onColHeaderQtyChange)
                        TemplateNumberField("Amount Col", colHeaderAmount, onColHeaderAmountChange)
                    }

                    // --- Totals ---
                    TemplateSectionHeader("Totals")
                    TemplateLabelFontGapRow("Gross Total Label", grossTotalLabel, onGrossTotalLabelChange, grossTotalFont, onGrossTotalFontChange, grossTotalGap, onGrossTotalGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Discount Label", discountLabel, onDiscountLabelChange, discountFont, onDiscountFontChange, discountGap, onDiscountGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Net Total Label", netTotalLabel, onNetTotalLabelChange, netTotalFont, onNetTotalFontChange, netTotalGap, onNetTotalGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Cash Label", cashLabel, onCashLabelChange, cashFont, onCashFontChange, cashGap, onCashGapChange)
                    Spacer(modifier = Modifier.height(4.dp))
                    TemplateLabelFontGapRow("Balance Label", balanceLabel, onBalanceLabelChange, balanceFont, onBalanceFontChange, balanceGap, onBalanceGapChange)

                    // --- Save Button ---
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667EEA))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", color = Color.White)
                        } else {
                            Text("Save & Print", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---- Template save helpers ----

private fun buildTemplateMap(
    billType: String,
    companyName: String, companyFont: String, companyGap: String,
    address: String, addressFont: String, addressGap: String,
    contact: String, contactFont: String, contactGap: String,
    email: String, emailFont: String, emailGap: String,
    lastLine: String, lastLineFont: String, lastLineGap: String,
    upperGap: String, leftGap: String,
    logoLeft: String, logoUpper: String, logoWidth: String, logoHeight: String,
    billNoLabel: String, billNoFont: String, billNoGap: String,
    dateLabel: String, dateFont: String, dateGap: String,
    separatorGap: String,
    itemHeader: String, priceHeader: String, qtyHeader: String, amountHeader: String,
    headerFont: String, headerGap: String,
    itemFont: String, itemGap: String, itemNameGap: String,
    grossTotalLabel: String, grossTotalFont: String, grossTotalGap: String,
    discountLabel: String, discountFont: String, discountGap: String,
    netTotalLabel: String, netTotalFont: String, netTotalGap: String,
    cashLabel: String, cashFont: String, cashGap: String,
    balanceLabel: String, balanceFont: String, balanceGap: String,
    colPrice: String, colQty: String, colAmount: String,
    colHeaderPrice: String = colPrice, colHeaderQty: String = colQty, colHeaderAmount: String = colAmount
): Map<String, Any> {
    return mapOf(
        "billType" to billType,
        "companyName" to companyName,
        "companyFont" to (companyFont.toIntOrNull() ?: 17),
        "companyGap" to (companyGap.toIntOrNull() ?: 6),
        "address" to address,
        "addressFont" to (addressFont.toIntOrNull() ?: 12),
        "addressGap" to (addressGap.toIntOrNull() ?: 6),
        "contact" to contact,
        "contactFont" to (contactFont.toIntOrNull() ?: 12),
        "contactGap" to (contactGap.toIntOrNull() ?: 6),
        "email" to email,
        "emailFont" to (emailFont.toIntOrNull() ?: 12),
        "emailGap" to (emailGap.toIntOrNull() ?: 10),
        "lastLine" to lastLine,
        "lastLineFont" to (lastLineFont.toIntOrNull() ?: 10),
        "lastLineGap" to (lastLineGap.toIntOrNull() ?: 10),
        "upperGap" to (upperGap.toIntOrNull() ?: 5),
        "leftGap" to (leftGap.toIntOrNull() ?: 1),
        "logoLeft" to (logoLeft.toIntOrNull() ?: 53),
        "logoUpper" to (logoUpper.toIntOrNull() ?: 10),
        "logoWidth" to (logoWidth.toIntOrNull() ?: 20),
        "logoHeight" to (logoHeight.toIntOrNull() ?: 20),
        "billNoLabel" to billNoLabel,
        "billNoFontSize" to (billNoFont.toIntOrNull() ?: 11),
        "billNoGap2" to (billNoGap.toIntOrNull() ?: 5),
        "dateLabel" to dateLabel,
        "dateFontSize" to (dateFont.toIntOrNull() ?: 11),
        "dateGap2" to (dateGap.toIntOrNull() ?: 5),
        "separatorGap" to (separatorGap.toIntOrNull() ?: 5),
        "itemHeader" to itemHeader,
        "priceHeader" to priceHeader,
        "qtyHeader" to qtyHeader,
        "totalHeader" to amountHeader,
        "itemHeaderFontSize" to (headerFont.toIntOrNull() ?: 11),
        "headerGap2" to (headerGap.toIntOrNull() ?: 5),
        "itemFontSize" to (itemFont.toIntOrNull() ?: 10),
        "itemGap" to (itemGap.toIntOrNull() ?: 5),
        "itemNameGap" to (itemNameGap.toIntOrNull() ?: 5),
        "grossTotalLabel" to grossTotalLabel,
        "grossTotalFontSize" to (grossTotalFont.toIntOrNull() ?: 11),
        "grossTotalGap" to (grossTotalGap.toIntOrNull() ?: 5),
        "billDiscountLabel" to discountLabel,
        "billDiscountFontSize" to (discountFont.toIntOrNull() ?: 11),
        "discountGap" to (discountGap.toIntOrNull() ?: 6),
        "netTotalLabel" to netTotalLabel,
        "netTotalFontSize" to (netTotalFont.toIntOrNull() ?: 13),
        "netTotalGap" to (netTotalGap.toIntOrNull() ?: 5),
        "cashPaidLabel" to cashLabel,
        "cashPaidFontSize" to (cashFont.toIntOrNull() ?: 11),
        "cashGap" to (cashGap.toIntOrNull() ?: 5),
        "balanceLabel" to balanceLabel,
        "balanceFontSize" to (balanceFont.toIntOrNull() ?: 11),
        "balanceGap" to (balanceGap.toIntOrNull() ?: 5),
        "colPriceMm" to (colPrice.toIntOrNull() ?: 30),
        "colQtyMm" to (colQty.toIntOrNull() ?: 50),
        "colTotalMm" to (colAmount.toIntOrNull() ?: 70),
        "colHeaderPriceMm" to (colHeaderPrice.toIntOrNull() ?: 30),
        "colHeaderQtyMm" to (colHeaderQty.toIntOrNull() ?: 50),
        "colHeaderAmountMm" to (colHeaderAmount.toIntOrNull() ?: 70)
    )
}

private suspend fun saveBillTemplate(token: String, templateData: Map<String, Any>): Boolean {
    return try {
        val gson = com.google.gson.Gson()
        val json = gson.toJson(templateData)
        val connection = java.net.URL("${ApiConfig.BASE_URL_8080}/bill/saveBillTemplate")
            .openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.outputStream.write(json.toByteArray())
        val responseCode = connection.responseCode
        Log.d("PrinterScreen", "saveBillTemplate response code: $responseCode")
        responseCode == 200
    } catch (e: Exception) {
        Log.e("PrinterScreen", "Error saving template: ${e.message}")
        false
    }
}
