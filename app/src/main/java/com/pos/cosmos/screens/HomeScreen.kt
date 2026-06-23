package com.pos.cosmos.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.pos.cosmos.components.*
import com.pos.cosmos.utils.BillTemplateCache
import com.pos.cosmos.utils.SessionManager
import com.pos.cosmos.navigation.Screen
import com.pos.cosmos.viewmodels.AppViewModel
import com.pos.cosmos.viewmodels.DashboardViewModel
import com.pos.cosmos.utils.LocalResponsiveDimensions
import com.pos.cosmos.models.MobileUiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import com.google.gson.Gson
import android.util.Log
import java.text.NumberFormat
import java.util.Locale
import com.pos.cosmos.api.ApiConfig

data class MenuOption(
    val label: String,
    val route: String,
    val color: Color,
    val permission: String? = null,  // null means always visible
    val mobileConfigKey: String? = null  // Key to check in mobile UI config, null means always visible
)

data class SearchMenuItem(
    val name: String,
    val route: String,
    val permission: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    appViewModel: AppViewModel,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val verifiedUserName by appViewModel.verifiedUserName.collectAsState()
    val token by appViewModel.token.collectAsState()
    val userPermission by appViewModel.userPermission.collectAsState()
    val mobileUiConfig by appViewModel.mobileUiConfig.collectAsState()

    val salesOverview by dashboardViewModel.salesOverview.collectAsState()
    val purchaseOverview by dashboardViewModel.purchaseOverview.collectAsState()
    val stockInfo by dashboardViewModel.stockInfo.collectAsState()
    val salesStatistics by dashboardViewModel.salesStatistics.collectAsState()
    val topSellingItems by dashboardViewModel.topSellingItems.collectAsState()

    var showQuickMenu by remember { mutableStateOf(false) }

    // Load dashboard data and mobile UI config
    LaunchedEffect(token) {
        token?.let {
            dashboardViewModel.loadDashboardData(it)
            // Fetch mobile UI config
            try {
                val config = fetchMobileUiConfig(it)
                Log.d("HomeScreen", "Fetched mobile UI config: showCashBillInMobile=${config.showCashBillInMobile}, showCreditBillInMobile=${config.showCreditBillInMobile}")
                appViewModel.setMobileUiConfig(config)
            } catch (e: Exception) {
                Log.e("HomeScreen", "Failed to fetch mobile UI config", e)
            }
        }
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.UK).apply {
            maximumFractionDigits = 0
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { showQuickMenu = !showQuickMenu }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Color(0xFF4a5568)
                        )
                    }
                },
                actions = {
                    // Search Icon
                    IconButton(onClick = { showSearch = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFFa0aec0)
                        )
                    }

                    // Notification Icon with Badge
                    Box {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color(0xFFa0aec0)
                            )
                        }
                        // Notification badge
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = 24.dp, y = 8.dp)
                                .background(Color(0xFFf56565), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "3",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // User Profile Icon
                    IconButton(onClick = { showProfile = true }) {
                        Box(
                            modifier = Modifier
                                .size(35.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Content
            if (showQuickMenu) {
            QuickMenuOverlay(
                navController = navController,
                onDismiss = { showQuickMenu = false },
                modifier = Modifier.padding(innerPadding),
                userPermission = userPermission,
                mobileUiConfig = mobileUiConfig
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Billing Overview Section
                Text(
                    text = "Billing Overview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4a5568)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconStatItem(
                                label = "CREDIT BILLS",
                                value = salesOverview.creditBillsCount.toString(),
                                iconBg = Color(0xFFe6f2ff),
                                iconColor = Color(0xFF4299e1),
                                icon = "credit_card",
                                modifier = Modifier.weight(1f)
                            )
                            IconStatItem(
                                label = "CASH BILLS",
                                value = salesOverview.cashBillsCount.toString(),
                                iconBg = Color(0xFFfff5e6),
                                iconColor = Color(0xFFed8936),
                                icon = "money",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconStatItem(
                                label = "CUSTOMERS",
                                value = salesOverview.customerCount.toString(),
                                iconBg = Color(0xFFffe6f0),
                                iconColor = Color(0xFFd53f8c),
                                icon = "people",
                                modifier = Modifier.weight(1f)
                            )
                            IconStatItem(
                                label = "TODAY BILLS",
                                value = salesOverview.todayInvoiceCount.toString(),
                                iconBg = Color(0xFFffe6e6),
                                iconColor = Color(0xFFe53e3e),
                                icon = "receipt",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Purchase Overview Section
                Text(
                    text = "Purchase Overview",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF4a5568)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconStatItem(
                                label = "TOTAL PURCHASE",
                                value = purchaseOverview.totalPurchases.toInt().toString(),
                                iconBg = Color(0xFFe6f9f0),
                                iconColor = Color(0xFF38b2ac),
                                icon = "cart",
                                modifier = Modifier.weight(1f)
                            )
                            IconStatItem(
                                label = "TODAY PURCHASE",
                                value = purchaseOverview.todayPurchases.toString(),
                                iconBg = Color(0xFFe6ffe6),
                                iconColor = Color(0xFF48bb78),
                                icon = "calendar",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconStatItem(
                                label = "SUPPLIERS",
                                value = purchaseOverview.suppliersCount.toString(),
                                iconBg = Color(0xFFfff0e6),
                                iconColor = Color(0xFFdd6b20),
                                icon = "truck",
                                modifier = Modifier.weight(1f)
                            )
                            IconStatItem(
                                label = "OWN PRODUCTS",
                                value = purchaseOverview.ownProductsCount.toString(),
                                iconBg = Color(0xFFffe6f5),
                                iconColor = Color(0xFFd53f8c),
                                icon = "box",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Stock and Return Items Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stock Section
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Stock",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4a5568),
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            StockGauge(
                                currentStock = stockInfo.currentStock,
                                maxStock = stockInfo.maxStock,
                                lowStockCount = stockInfo.lowStockCount
                            )
                        }
                    }

                    // Return Items Section
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            Text(
                                text = "Return Items",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4a5568)
                            )
                            IconStatItem(
                                label = "CUSTOMER",
                                value = purchaseOverview.customerReturnsCount.toString(),
                                iconBg = Color(0xFFe6f2ff),
                                iconColor = Color(0xFF4299e1),
                                icon = "person",
                                modifier = Modifier.fillMaxWidth()
                            )
                            IconStatItem(
                                label = "SUPPLIER",
                                value = purchaseOverview.supplierReturnsCount.toString(),
                                iconBg = Color(0xFFfff5e6),
                                iconColor = Color(0xFFed8936),
                                icon = "truck",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Sales Statistics Chart
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Sales Statistics",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4a5568)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SalesLineChart(data = salesStatistics)
                    }
                }

                // Top Selling Items
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Top Selling Items",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4a5568)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        DonutChart(items = topSellingItems)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

            // Sliding Sidebars
            // Search Sidebar
            AnimatedVisibility(
                visible = showSearch,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ),
                modifier = Modifier.zIndex(10f)
            ) {
                SearchSidebar(
                    onDismiss = { showSearch = false },
                    navController = navController,
                    userPermission = userPermission
                )
            }

            // Notifications Sidebar
            AnimatedVisibility(
                visible = showNotifications,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ),
                modifier = Modifier.zIndex(10f)
            ) {
                NotificationsSidebar(onDismiss = { showNotifications = false })
            }

            // Profile Sidebar
            AnimatedVisibility(
                visible = showProfile,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                ),
                modifier = Modifier.zIndex(10f)
            ) {
                ProfileSidebar(
                    userName = verifiedUserName ?: "User",
                    onDismiss = { showProfile = false },
                    onLogout = {
                        // Clear cached bill template so new account gets fresh template
                        BillTemplateCache.clearTemplate(context)
                        // Clear saved credentials so user must login again
                        SessionManager.getInstance(context).clearCredentials()
                        appViewModel.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun IconStatItem(
    label: String,
    value: String,
    iconBg: Color,
    iconColor: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    val dimens = LocalResponsiveDimensions.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(dimens.scaledDp(45f))
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dimens.iconNormal)
                    .background(iconColor, RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.width(dimens.spacingMedium))
        Column {
            Text(
                text = label,
                fontSize = dimens.fontSmall,
                color = Color(0xFFa0aec0),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(dimens.spacingTiny))
            Text(
                text = value,
                fontSize = dimens.fontLargeTitle,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2d3748)
            )
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dimens = LocalResponsiveDimensions.current
    Button(
        onClick = onClick,
        modifier = modifier.height(dimens.scaledDp(50f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = color
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = dimens.fontTitle,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun QuickMenuOverlay(
    navController: NavHostController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    userPermission: com.pos.cosmos.models.UserPermission? = null,
    mobileUiConfig: MobileUiConfig = MobileUiConfig()
) {
    val dimens = LocalResponsiveDimensions.current
    val allMenuItems = listOf(
        MenuOption("Image Bill", Screen.ImageBill.route, Color(0xFF7C4DFF), "viewCashBill", "showImageBillInMobile"),
        MenuOption("Cash Bill", Screen.CashBill.route, Color(0xFF00D4FF), "viewCashBill", "showCashBillInMobile"),
        MenuOption("Credit Bill", Screen.CreditBill.route, Color(0xFF00FF88), "viewCreditBill", "showCreditBillInMobile"),
        MenuOption("KOT", Screen.KOT.route, Color(0xFFFF5733), "viewKOT", "showKOTInMobile"),
        MenuOption("All KOTs", Screen.AllKOTs.route, Color(0xFFE91E63), "viewKOT", "showAllKOTsInMobile"),
        MenuOption("Stock", Screen.Stock.route, Color(0xFFFF6B6B), "viewStock", "showStockInMobile"),
        MenuOption("All Items", Screen.AllItems.route, Color(0xFFFFD93D), "viewAllItems", "showAllItemsInMobile"),
        MenuOption("Orders", Screen.OrderForm.route, Color(0xFF9D84B7), "viewOrderForm", "showOrderFormInMobile"),
        MenuOption("All Orders", Screen.AllOrders.route, Color(0xFFBA68C8), "viewAllOrders", "showAllOrdersInMobile"),
        MenuOption("Purchasing", Screen.Purchasing.route, Color(0xFF6BCB77), "viewPurchasing", "showPurchasingInMobile"),
        MenuOption("Stock Adjust", Screen.StockAdjustment.route, Color(0xFF4D96FF), "updateStock", "showStockAdjustInMobile"),
        MenuOption("Profit", Screen.Profit.route, Color(0xFFFF6B9D), "profit", "showProfitInMobile"),
        MenuOption("Outstanding", Screen.CustomerOutstanding.route, Color(0xFFC780FA), "viewTotalOutStanding", "showCustomerOutstandingInMobile"),
        MenuOption("Customer Payments", Screen.CustomerPayments.route, Color(0xFF50C878), "customerPayments", "showCustomerPaymentsInMobile"),
        MenuOption("Customers", Screen.CustomerRegistration.route, Color(0xFFFFA500), "addCustomers", "showCustomersInMobile"),
        MenuOption("Suppliers", Screen.SupplierRegistration.route, Color(0xFF1E90FF), "viewSupplierRegistration", "showSuppliersInMobile"),
        MenuOption("Supplier Payments", Screen.SupplierPayments.route, Color(0xFF00897B), "supplierPayments", "showSupplierPaymentsInMobile"),
        MenuOption("Expenses", Screen.Expenses.route, Color(0xFFAD1457), "viewExpenses", "showExpensesInMobile"),
        MenuOption("Cheques", Screen.Cheques.route, Color(0xFF8E44AD), "viewCheque", "showChequesInMobile"),
        MenuOption("Printer", Screen.Printer.route, Color(0xFF00CEC9), null, "showPrinterInMobile"),
        MenuOption("All Bills", Screen.AllBills.route, Color(0xFF42A5F5), "viewAllBills", "showAllBillsInMobile"),
        MenuOption("Returns", Screen.CustomerReturns.route, Color(0xFFFF7043), "viewCreditBill", "showReturnsInMobile"),
        MenuOption("Item Returns", Screen.ItemReturns.route, Color(0xFFFF8A65), "viewCreditBill", "showItemReturnsInMobile"),
        MenuOption("Settings", Screen.Settings.route, Color(0xFF607D8B), null, "showSettingsInMobile"),
    )

    // Filter menu items based on permissions and mobile UI config
    val menuItems = allMenuItems.filter { item ->
        val hasPermission = item.permission == null || userPermission?.hasPermission(item.permission) == true
        val isEnabledInMobile = item.mobileConfigKey == null || mobileUiConfig.isEnabled(item.mobileConfigKey)
        Log.d("HomeScreen", "Menu item '${item.label}': permission=$hasPermission, mobileEnabled=$isEnabledInMobile (key=${item.mobileConfigKey})")
        hasPermission && isEnabledInMobile
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacingNormal)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Menu",
                    fontSize = dimens.fontDisplay,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A2E)
                )
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color(0xFF1A1A2E), fontSize = dimens.fontSubtitle)
                }
            }

            Spacer(modifier = Modifier.height(dimens.spacingNormal))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingMedium),
                verticalArrangement = Arrangement.spacedBy(dimens.spacingMedium)
            ) {
                items(menuItems) { item ->
                    MenuCard(
                        label = item.label,
                        color = item.color,
                        onClick = {
                            onDismiss()
                            navController.navigate(item.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuCard(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val dimens = LocalResponsiveDimensions.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.scaledDp(100f))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacingNormal),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = dimens.fontSubtitle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(dimens.spacingSmall)
            )
        }
    }
}

@Composable
fun SearchSidebar(
    onDismiss: () -> Unit,
    navController: NavHostController,
    userPermission: com.pos.cosmos.models.UserPermission? = null
) {
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    val allMenuItems = remember {
        listOf(
            SearchMenuItem("Home", Screen.Home.route, "viewHomePage"),
            SearchMenuItem("Cash Bill", Screen.CashBill.route, "viewCashBill"),
            SearchMenuItem("Credit Bill", Screen.CreditBill.route, "viewCreditBill"),
            SearchMenuItem("KOT", Screen.KOT.route, "viewKOT"),
            SearchMenuItem("All KOTs", Screen.AllKOTs.route, "viewKOT"),
            SearchMenuItem("Stock", Screen.Stock.route, "viewStock"),
            SearchMenuItem("All Items", Screen.AllItems.route, "viewAllItems"),
            SearchMenuItem("Order Form", Screen.OrderForm.route, "viewOrderForm"),
            SearchMenuItem("All Orders", Screen.AllOrders.route, "viewAllOrders"),
            SearchMenuItem("Purchasing", Screen.Purchasing.route, "viewPurchasing"),
            SearchMenuItem("Stock Adjustment", Screen.StockAdjustment.route, "updateStock"),
            SearchMenuItem("Profit", Screen.Profit.route, "profit"),
            SearchMenuItem("Customer Outstanding", Screen.CustomerOutstanding.route, "viewTotalOutStanding"),
            SearchMenuItem("Customer Payments", Screen.CustomerPayments.route, "customerPayments"),
            SearchMenuItem("Customer Registration", Screen.CustomerRegistration.route, "addCustomers"),
            SearchMenuItem("Supplier Registration", Screen.SupplierRegistration.route, "viewSupplierRegistration"),
            SearchMenuItem("Supplier Payments", Screen.SupplierPayments.route, "supplierPayments"),
            SearchMenuItem("Expenses", Screen.Expenses.route, "viewExpenses"),
            SearchMenuItem("Cheques", Screen.Cheques.route, "viewCheque"),
            SearchMenuItem("Printer", Screen.Printer.route, null),
            SearchMenuItem("All Bills", Screen.AllBills.route, "viewAllBills"),
            SearchMenuItem("Settings", Screen.Settings.route, null)
        )
    }

    // Filter menu items based on permissions first
    val permittedItems = allMenuItems.filter { item ->
        item.permission == null || userPermission?.hasPermission(item.permission) == true
    }

    val filteredItems = remember(searchQuery.text, permittedItems) {
        if (searchQuery.text.isEmpty()) {
            emptyList()
        } else {
            permittedItems.filter {
                it.name.contains(searchQuery.text, ignoreCase = true)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent overlay on the left
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )

        // Sidebar content on the right
        Surface(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .shadow(8.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2d3748)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF4a5568)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search pages...", color = Color(0xFFa0aec0)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4299e1),
                        unfocusedBorderColor = Color(0xFFe2e8f0),
                        cursorColor = Color(0xFF4299e1),
                        focusedTextColor = Color(0xFF2d3748),
                        unfocusedTextColor = Color(0xFF2d3748)
                    ),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFFa0aec0)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Search results or recent searches
                if (searchQuery.text.isEmpty()) {
                    Text(
                        text = "Recent Searches",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF718096)
                    )
                } else {
                    Text(
                        text = "${filteredItems.size} result${if (filteredItems.size != 1) "s" else ""}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF718096)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Display filtered results
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredItems) { item ->
                            SearchResultItem(
                                item = item,
                                onClick = {
                                    navController.navigate(item.route)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    item: SearchMenuItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF4299e1),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = item.name,
                fontSize = 15.sp,
                color = Color(0xFF2d3748),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NotificationsSidebar(onDismiss: () -> Unit) {
    val notifications = remember {
        listOf(
            NotificationItem("New order received", "#12345", "2 hours ago", false),
            NotificationItem("Low stock alert", "Item ABC is running low", "5 hours ago", false),
            NotificationItem("Payment received", "Customer XYZ paid $500", "1 day ago", true)
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )

        // Sidebar content
        Surface(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .shadow(8.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Notifications",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2d3748)
                        )
                        Text(
                            text = "You have ${notifications.filter { !it.isRead }.size} unread notification${if (notifications.filter { !it.isRead }.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = Color(0xFFa0aec0),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row {
                        TextButton(onClick = { /* Mark all as read */ }) {
                            Text(
                                "Mark all read",
                                fontSize = 13.sp,
                                color = Color(0xFF4299e1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF4a5568)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Notifications list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(notifications) { notification ->
                        NotificationCard(notification)
                    }
                }
            }
        }
    }
}

data class NotificationItem(
    val title: String,
    val message: String,
    val time: String,
    val isRead: Boolean
)

@Composable
fun NotificationCard(notification: NotificationItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Mark as read */ }
            .background(if (!notification.isRead) Color(0xFFf7fafc) else Color.Transparent)
            .padding(vertical = 15.dp, horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Unread indicator
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(Color(0xFF4299e1), CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontSize = 14.sp,
                    fontWeight = if (!notification.isRead) FontWeight.SemiBold else FontWeight.Normal,
                    color = Color(0xFF2d3748)
                )
                Text(
                    text = notification.message,
                    fontSize = 13.sp,
                    color = Color(0xFF4a5568),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFFa0aec0),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = notification.time,
                        fontSize = 11.sp,
                        color = Color(0xFFa0aec0)
                    )
                    if (notification.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF48bb78),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Read",
                            fontSize = 11.sp,
                            color = Color(0xFF48bb78)
                        )
                    }
                }
            }
        }
    }
    Divider(color = Color(0xFFe2e8f0), thickness = 1.dp)
}

@Composable
fun ProfileSidebar(
    userName: String,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    var showProfileDetails by remember { mutableStateOf(false) }

    if (showProfileDetails) {
        ProfileDetailsDialog(
            userName = userName,
            onDismiss = { showProfileDetails = false }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )

        // Sidebar content
        Surface(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .shadow(8.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(15.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = userName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2d3748)
                            )
                            Text(
                                text = "Admin",
                                fontSize = 13.sp,
                                color = Color(0xFFa0aec0)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF4a5568)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Menu items
                ProfileMenuItem(
                    icon = Icons.Default.AccountBox,
                    label = "My Profile",
                    onClick = { showProfileDetails = true }
                )
                ProfileMenuItem(
                    icon = Icons.Default.AccountCircle,
                    label = "Company",
                    onClick = { }
                )
                ProfileMenuItem(
                    icon = Icons.Default.ShoppingCart,
                    label = "My Invoices",
                    onClick = { }
                )
                ProfileMenuItem(
                    icon = Icons.Default.Person,
                    label = "Accept User",
                    onClick = { }
                )

                Spacer(modifier = Modifier.weight(1f))

                val context = LocalContext.current
                val versionName = remember(context) {
                    try {
                        val pkg = context.packageName
                        context.packageManager.getPackageInfo(pkg, 0).versionName ?: "—"
                    } catch (_: Exception) {
                        "—"
                    }
                }
                Divider(color = Color(0xFFe2e8f0), modifier = Modifier.padding(top = 15.dp))
                Text(
                    text = "App Version $versionName",
                    fontSize = 12.sp,
                    color = Color(0xFFa0aec0),
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                )
                Divider(color = Color(0xFFe2e8f0), modifier = Modifier.padding(bottom = 5.dp))

                // Logout button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogout() }
                        .padding(vertical = 15.dp, horizontal = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            tint = Color(0xFFf56565),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Log Out",
                            fontSize = 16.sp,
                            color = Color(0xFFe53e3e),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog showing the logged-in user's basic profile details.
 */
@Composable
fun ProfileDetailsDialog(
    userName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = "My Profile",
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2d3748)
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                ProfileDetailRow(label = "User Name", value = userName)
                Divider(
                    color = Color(0xFFe2e8f0),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                ProfileDetailRow(label = "Role", value = "Admin")
            }
        }
    )
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFFa0aec0)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2d3748)
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 15.dp, horizontal = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4299e1),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color(0xFF2d3748)
            )
        }
    }
}

/**
 * Fetch mobile UI config from the dealer-service backend
 */
suspend fun fetchMobileUiConfig(token: String): MobileUiConfig = withContext(Dispatchers.IO) {
    try {
        val url = URL("${ApiConfig.BASE_URL_8090}/company/ui-config-by-token")
        Log.d("HomeScreen", "Fetching mobile UI config from: $url")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        // Handle SSL for development (same approach as RetrofitClient)
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

        val responseCode = connection.responseCode
        Log.d("HomeScreen", "Mobile UI config response code: $responseCode")

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("HomeScreen", "Mobile UI config response: $response")
            val gson = Gson()
            val jsonObject = gson.fromJson(response, com.google.gson.JsonObject::class.java)

            // Helper function to safely get string value
            fun getStringValue(key: String, default: String = "Y"): String {
                val element = jsonObject.get(key)
                return if (element != null && !element.isJsonNull) {
                    element.asString
                } else {
                    default // Default when key is missing
                }
            }

            // Parse the mobile-specific config fields
            val config = MobileUiConfig(
                id = jsonObject.get("id")?.asInt ?: 0,
                companyCode = jsonObject.get("companyCode")?.asInt ?: 0,
                showCashBillInMobile = getStringValue("showCashBillInMobile"),
                showCreditBillInMobile = getStringValue("showCreditBillInMobile"),
                showKOTInMobile = getStringValue("showKOTInMobile"),
                showAllKOTsInMobile = getStringValue("showAllKOTsInMobile"),
                showAllItemsInMobile = getStringValue("showAllItemsInMobile"),
                showStockInMobile = getStringValue("showStockInMobile"),
                showOrderFormInMobile = getStringValue("showOrderFormInMobile"),
                showAllOrdersInMobile = getStringValue("showAllOrdersInMobile"),
                showPurchasingInMobile = getStringValue("showPurchasingInMobile"),
                showProfitInMobile = getStringValue("showProfitInMobile"),
                showCustomerOutstandingInMobile = getStringValue("showCustomerOutstandingInMobile"),
                showCustomerPaymentsInMobile = getStringValue("showCustomerPaymentsInMobile"),
                showAllBillsInMobile = getStringValue("showAllBillsInMobile"),
                showPrinterInMobile = getStringValue("showPrinterInMobile"),
                showSuppliersInMobile = getStringValue("showSuppliersInMobile"),
                showCustomersInMobile = getStringValue("showCustomersInMobile"),
                showStockAdjustInMobile = getStringValue("showStockAdjustInMobile"),
                showImageBillInMobile = getStringValue("showImageBillInMobile"),
                showReturnsInMobile = getStringValue("showReturnsInMobile"),
                showItemReturnsInMobile = getStringValue("showItemReturnsInMobile", "N"),
                showSettingsInMobile = getStringValue("showSettingsInMobile"),
                showConfigInMobile = getStringValue("showConfigInMobile"),
                showSyncDataInMobile = getStringValue("showSyncDataInMobile"),
                showSupplierPaymentsInMobile = getStringValue("showSupplierPaymentsInMobile", "N"),
                showExpensesInMobile = getStringValue("showExpensesInMobile", "N"),
                showChequesInMobile = getStringValue("showChequesInMobile", "N")
            )
            Log.d("HomeScreen", "Parsed config: cashBill=${config.showCashBillInMobile}, creditBill=${config.showCreditBillInMobile}, printer=${config.showPrinterInMobile}")
            config
        } else {
            Log.e("HomeScreen", "Failed to fetch mobile UI config: $responseCode")
            MobileUiConfig() // Return default config with all enabled
        }
    } catch (e: Exception) {
        Log.e("HomeScreen", "Error fetching mobile UI config: ${e.message}", e)
        MobileUiConfig() // Return default config with all enabled
    }
}
