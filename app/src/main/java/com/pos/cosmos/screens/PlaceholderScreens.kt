package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.viewmodels.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(navController: NavHostController, appViewModel: AppViewModel) {
    // Redirect to the full StockScreen implementation
    StockScreenFull(navController, appViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFormScreen(navController: NavHostController, appViewModel: AppViewModel) {
    // Redirect to the full OrderFormScreen implementation
    OrderFormScreenFull(navController, appViewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasingScreen(navController: NavHostController, appViewModel: AppViewModel) {
    PlaceholderScreen("Purchasing", navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierRegistrationScreen(navController: NavHostController, appViewModel: AppViewModel) {
    PlaceholderScreen("Supplier Registration", navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String, navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A2E))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F3460)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    "Coming Soon",
                    fontSize = 16.sp,
                    color = Color(0xFFCCCCCC)
                )
            }
        }
    }
}
