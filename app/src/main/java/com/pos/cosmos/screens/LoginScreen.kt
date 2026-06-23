package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.api.RetrofitClient
import com.pos.cosmos.models.UserPermission
import com.pos.cosmos.navigation.Screen
import com.pos.cosmos.utils.SessionManager
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rememberMe by remember { mutableStateOf(sessionManager.isRememberMeEnabled()) }
    var autoLoginAttempted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-login if credentials are saved
    LaunchedEffect(Unit) {
        if (!autoLoginAttempted && sessionManager.hasSavedCredentials()) {
            autoLoginAttempted = true
            val savedUsername = sessionManager.getSavedUsername()
            val savedPassword = sessionManager.getSavedPassword()
            if (!savedUsername.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                username = savedUsername
                password = savedPassword
                // Trigger auto-login
                performLogin(
                    username = savedUsername,
                    password = savedPassword,
                    rememberMe = true,
                    sessionManager = sessionManager,
                    appViewModel = appViewModel,
                    navController = navController,
                    onLoadingChange = { loading = it },
                    onError = { errorMessage = it }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome Back!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username", color = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16213E), shape = RoundedCornerShape(8.dp)),
                textStyle = TextStyle(color = Color.White),
                singleLine = true,
                enabled = !loading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.White) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF16213E), shape = RoundedCornerShape(8.dp)),
                textStyle = TextStyle(color = Color.White),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !loading
            )

            // Remember Me checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF00D4FF),
                        uncheckedColor = Color.White,
                        checkmarkColor = Color.Black
                    ),
                    enabled = !loading
                )
                Text(
                    text = "Remember Me",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (username.trim().isEmpty() || password.trim().isEmpty()) {
                        errorMessage = "Username and password cannot be empty."
                        return@Button
                    }

                    coroutineScope.launch {
                        performLogin(
                            username = username,
                            password = password,
                            rememberMe = rememberMe,
                            sessionManager = sessionManager,
                            appViewModel = appViewModel,
                            navController = navController,
                            onLoadingChange = { loading = it },
                            onError = { errorMessage = it }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4FF),
                    disabledContainerColor = Color.Gray
                ),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(6.dp))
                } else {
                    Text("Login", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private suspend fun performLogin(
    username: String,
    password: String,
    rememberMe: Boolean,
    sessionManager: SessionManager,
    appViewModel: AppViewModel,
    navController: NavHostController,
    onLoadingChange: (Boolean) -> Unit,
    onError: (String?) -> Unit
) {
    onLoadingChange(true)
    onError(null)
    try {
        val credentials = mapOf(
            "username" to username,
            "password" to password
        )

        try {
            val response = RetrofitClient.apiService.login(credentials)

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val token = body.get("token")?.asString
                    if (token != null && token.isNotEmpty()) {
                        appViewModel.setToken(token)
                        appViewModel.setVerifiedUserName(username)
                        // Persist token so background SyncWorker can reach the API.
                        sessionManager.saveToken(token)

                        // Save credentials if Remember Me is checked
                        if (rememberMe) {
                            sessionManager.saveCredentials(username, password)
                        } else {
                            sessionManager.clearCredentials()
                        }

                        // Fetch user permissions
                        try {
                            val permResponse = RetrofitClient.apiService.getUserPermissions("Bearer $token")
                            if (permResponse.isSuccessful && permResponse.body() != null) {
                                val permBody = permResponse.body()!!

                                fun getLong(key: String): Long {
                                    val element = permBody.get(key)
                                    return if (element == null || element.isJsonNull) 0 else element.asLong
                                }

                                fun getString(key: String, default: String = "N"): String {
                                    val element = permBody.get(key)
                                    return if (element == null || element.isJsonNull) default else element.asString
                                }

                                val permission = UserPermission(
                                    id = getLong("id"),
                                    userId = getLong("userId"),
                                    profit = getString("profit"),
                                    addCustomers = getString("addCustomers"),
                                    addItems = getString("addItems"),
                                    addSuppliers = getString("addSuppliers"),
                                    billSettings = getString("billSettings"),
                                    customerPayments = getString("customerPayments"),
                                    deleteCustomers = getString("deleteCustomers"),
                                    deleteItems = getString("deleteItems"),
                                    deleteSuppliers = getString("deleteSuppliers"),
                                    supplierPayments = getString("supplierPayments"),
                                    updateUserPermissions = getString("updateUserPermissions"),
                                    userEvents = getString("userEvents"),
                                    viewUserPermissions = getString("viewUserPermissions"),
                                    viewSmallBillSettings = getString("viewSmallBillSettings"),
                                    viewLargeBillSettings = getString("viewLargeBillSettings"),
                                    viewAllItems = getString("viewAllItems"),
                                    viewDashboard = getString("viewDashboard"),
                                    viewTotalOutStanding = getString("viewTotalOutStanding"),
                                    viewIndividualCustomerOutStanding = getString("viewIndividualCustomerOutStanding"),
                                    viewSupplierRegistration = getString("viewSupplierRegistration"),
                                    viewCreditNote = getString("viewCreditNote"),
                                    viewCashBill = getString("viewCashBill"),
                                    viewSalesRepRegistration = getString("viewSalesRepRegistration"),
                                    viewCreditBill = getString("viewCreditBill"),
                                    viewImageBill = getString("viewImageBill"),
                                    viewOrderForm = getString("viewOrderForm"),
                                    viewAllBills = getString("viewAllBills"),
                                    viewCustomerItemReturns = getString("viewCustomerItemReturns"),
                                    viewAllOrders = getString("viewAllOrders"),
                                    viewExpenses = getString("viewExpenses"),
                                    viewSupplierItemReturns = getString("viewSupplierItemReturns"),
                                    viewStock = getString("viewStock"),
                                    viewCheque = getString("viewCheque"),
                                    viewPurchasing = getString("viewPurchasing"),
                                    viewSalesRepCollection = getString("viewSalesRepCollection"),
                                    viewAcceptPayments = getString("viewAcceptPayments"),
                                    acceptPayments = getString("acceptPayments"),
                                    updateCreditCustomerInvoice = getString("updateCreditCustomerInvoice"),
                                    updatePurchaseInvoice = getString("updatePurchaseInvoice"),
                                    deletePurchaseInvoice = getString("deletePurchaseInvoice"),
                                    viewConfigs = getString("viewConfigs"),
                                    updateItems = getString("updateItems"),
                                    updateStock = getString("updateStock"),
                                    updateOrders = getString("updateOrders"),
                                    viewCashBook = getString("viewCashBook"),
                                    updateReOrderQty = getString("updateReOrderQty"),
                                    allowBlockCustomerBilling = getString("allowBlockCustomerBilling"),
                                    allowReadyStateKotCancel = getString("allowReadyStateKotCancel"),
                                    initialPage = getString("initialPage", "home"),
                                    viewHomePage = getString("viewHomePage"),
                                    viewItemMovement = getString("viewItemMovement"),
                                    viewStockMovement = getString("viewStockMovement"),
                                    showCashBookSummary = getString("showCashBookSummary"),
                                    showDailySalesSettlements = getString("showDailySalesSettlements"),
                                    viewAllServices = getString("viewAllServices"),
                                    editServices = getString("editServices"),
                                    deleteServices = getString("deleteServices"),
                                    viewKOT = getString("viewKOT")
                                )
                                appViewModel.setUserPermission(permission)
                                android.util.Log.d("LoginScreen", "Permissions loaded successfully")
                            }
                        } catch (permError: Exception) {
                            android.util.Log.e("LoginScreen", "Failed to load permissions: ${permError.message}")
                        }

                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        onError("Invalid token from server")
                    }
                } else {
                    onError("No response body")
                }
            } else {
                // Clear saved credentials on failed login (invalid credentials)
                if (response.code() == 401) {
                    sessionManager.clearCredentials()
                }
                val errorBodyText = try {
                    response.errorBody()?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }
                onError(when (response.code()) {
                    401 -> "Invalid username or password"
                    400 -> "Bad request: $errorBodyText"
                    404 -> "Server endpoint not found"
                    500 -> "Server error"
                    else -> "Login failed with code ${response.code()}: $errorBodyText"
                })
            }
        } catch (apiError: Exception) {
            onError("Network error: ${apiError.message ?: "Unknown error"}")
            android.util.Log.e("LoginScreen", "API Error", apiError)
        }
    } catch (e: Exception) {
        onError("Error: ${e.message}")
        android.util.Log.e("LoginScreen", "Login Error", e)
    } finally {
        onLoadingChange(false)
    }
}
