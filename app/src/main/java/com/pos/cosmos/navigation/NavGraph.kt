package com.pos.cosmos.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pos.cosmos.screens.*
import com.pos.cosmos.viewmodels.AppViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Profit : Screen("profit")
    object AllItems : Screen("allitems")
    object Stock : Screen("stock")
    object CustomerOutstanding : Screen("customer_outstanding")
    object CustomerPayments : Screen("customer_payments")
    object CreditBill : Screen("credit_bill")
    object CashBill : Screen("cash_bill")
    object OrderForm : Screen("order_form")
    object AllOrders : Screen("all_orders")
    object StockAdjustment : Screen("stock_adjustment")
    object Purchasing : Screen("purchasing")
    object SupplierRegistration : Screen("supplier_registration")
    object SupplierPayments : Screen("supplier_payments")
    object Expenses : Screen("expenses")
    object CustomerRegistration : Screen("customer_registration")
    object Printer : Screen("printer")
    object AllBills : Screen("all_bills")
    object KOT : Screen("kot")
    object AllKOTs : Screen("all_kots")
    object ImageBill : Screen("image_bill")
    object Config : Screen("config")
    object SyncData : Screen("sync_data")
    object Settings : Screen("settings")
    object Cheques : Screen("cheque")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    appViewModel: AppViewModel,
    startDestination: String
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Home.route) {
            HomeScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Profit.route) {
            ProfitScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.AllItems.route) {
            AllItemsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Stock.route) {
            StockScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.CustomerOutstanding.route) {
            CustomerOutstandingScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.CustomerPayments.route) {
            CustomerPaymentsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.CreditBill.route) {
            CreditBillScreen(navController = navController, appViewModel = appViewModel)
        }
        // CreditBill with order parameters
        composable(
            route = "credit_bill?orderNo={orderNo}&customerId={customerId}&salesRefId={salesRefId}",
            arguments = listOf(
                navArgument("orderNo") { type = NavType.IntType; defaultValue = 0 },
                navArgument("customerId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("salesRefId") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val orderNo = backStackEntry.arguments?.getInt("orderNo") ?: 0
            val customerId = backStackEntry.arguments?.getLong("customerId") ?: 0L
            val salesRefId = backStackEntry.arguments?.getLong("salesRefId") ?: 0L
            CreditBillScreen(
                navController = navController,
                appViewModel = appViewModel,
                orderNo = orderNo,
                customerId = customerId,
                salesRefId = salesRefId
            )
        }
        composable(Screen.CashBill.route) {
            CashBillScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.OrderForm.route) {
            OrderFormScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.AllOrders.route) {
            AllOrdersScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.StockAdjustment.route) {
            StockAdjustmentScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Purchasing.route) {
            PurchasingScreenFull(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.SupplierRegistration.route) {
            SupplierRegistrationScreenFull(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.SupplierPayments.route) {
            SupplierPaymentsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Expenses.route) {
            ExpensesScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.CustomerRegistration.route) {
            CustomerRegistrationScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Printer.route) {
            PrinterScreen(
                navController = navController,
                appViewModel = appViewModel,
                context = context
            )
        }
        composable(Screen.AllBills.route) {
            AllBillsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.KOT.route) {
            KOTScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.AllKOTs.route) {
            AllKOTsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.ImageBill.route) {
            ImageBillScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Config.route) {
            ConfigScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.SyncData.route) {
            SyncDataScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController, appViewModel = appViewModel)
        }
        composable(Screen.Cheques.route) {
            ChequeScreen(navController = navController, appViewModel = appViewModel)
        }
    }
}
