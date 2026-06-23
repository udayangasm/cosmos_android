package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import com.pos.cosmos.navigation.Screen
import com.pos.cosmos.viewmodels.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val mobileUiConfig by appViewModel.mobileUiConfig.collectAsState()

    val showConfig = mobileUiConfig.isEnabled("showConfigInMobile")
    val showSyncData = mobileUiConfig.isEnabled("showSyncDataInMobile")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showConfig) {
                SettingsCard(
                    title = "Configurations",
                    description = "Manage app configurations and feature settings",
                    icon = Icons.Default.Settings,
                    color = Color(0xFF607D8B),
                    onClick = { navController.navigate(Screen.Config.route) }
                )
            }

            if (showSyncData) {
                SettingsCard(
                    title = "Sync Data",
                    description = "Sync data between device and server",
                    icon = Icons.Default.Sync,
                    color = Color(0xFF455A64),
                    onClick = { navController.navigate(Screen.SyncData.route) }
                )
            }

            if (!showConfig && !showSyncData) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No settings available for your account",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                Text(description, fontSize = 13.sp, color = Color.Gray)
            }
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Go",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
