package com.pos.cosmos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.pos.cosmos.sync.SyncRepository
import com.pos.cosmos.sync.SyncTables
import com.pos.cosmos.sync.TableSyncStatus
import com.pos.cosmos.utils.AppSettings
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDataScreen(
    navController: NavHostController,
    appViewModel: AppViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { SyncRepository.get(context) }
    val settings = remember { AppSettings.get(context) }

    val token by appViewModel.token.collectAsState()
    val progress by repo.progress.collectAsState()
    val imageSync by repo.imageSync.collectAsState()
    val lastSyncAt by settings.lastSyncAt.collectAsState()
    val useLocalDb by settings.useLocalDatabase.collectAsState()

    var showClearConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { repo.refreshLocalCounts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Data") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            // ── Status panel
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Connection", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (useLocalDb) "Local database mode" else "Online mode",
                        color = if (useLocalDb) Color(0xFF2E7D32) else Color(0xFF1565C0)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Last Sync", fontWeight = FontWeight.SemiBold)
                    Text(formatTimestamp(lastSyncAt))
                    if (progress.isRunning) {
                        Spacer(Modifier.height(8.dp))
                        Text(progress.phase, fontSize = 12.sp, color = Color(0xFF666666))
                        LinearProgressIndicator(
                            progress = { progress.overallPercent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }

            // ── Action buttons (mirrors desktop SyncDataForm 6 buttons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    enabled = !progress.isRunning && token != null,
                    onClick = {
                        scope.launch {
                            token?.let {
                                repo.pullAllFromServer(it)
                                statusMessage = "Pull complete"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Sync")
                }
                Button(
                    enabled = !progress.isRunning && token != null,
                    onClick = {
                        scope.launch {
                            token?.let {
                                val n = repo.exportPendingToServer(it)
                                statusMessage = "Exported $n pending records"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    enabled = !progress.isRunning && !imageSync.isRunning && token != null,
                    onClick = {
                        scope.launch {
                            token?.let {
                                val n = repo.syncImages(it)
                                statusMessage = "Downloaded $n new images"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Sync Images")
                }
                OutlinedButton(
                    enabled = !progress.isRunning,
                    onClick = { scope.launch { repo.refreshLocalCounts() } },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    enabled = !progress.isRunning,
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear & Re-sync")
                }
            }

            // Image-sync progress card
            if (imageSync.isRunning || imageSync.message.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Item Images", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(imageSync.message, fontSize = 12.sp, color = Color(0xFF666666))
                        if (imageSync.total > 0) {
                            LinearProgressIndicator(
                                progress = { imageSync.processed.toFloat() / imageSync.total.toFloat() },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            statusMessage?.let {
                LaunchedEffect(it) {
                    kotlinx.coroutines.delay(2500)
                    statusMessage = null
                }
                Text(it, fontSize = 12.sp, color = Color(0xFF2E7D32))
            }

            // ── Per-table grid header
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF5F5F5))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Table", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Status", modifier = Modifier.weight(1.1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Local", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Synced", modifier = Modifier.weight(0.9f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Last Sync", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(SyncTables.ALL) { table ->
                            val status = progress.tables[table] ?: TableSyncStatus(table = table)
                            TableRow(status)
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear local data?") },
            text = { Text("All locally cached records will be deleted. Unsynced bills will be lost. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        repo.clearAllLocalData()
                        token?.let { repo.pullAllFromServer(it) }
                        statusMessage = "Local data cleared and re-synced"
                    }
                }) { Text("Clear", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TableRow(status: TableSyncStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status.table, modifier = Modifier.weight(1.8f), fontSize = 13.sp)
        StatusChip(status.status, modifier = Modifier.weight(1.1f))
        Text(status.localCount.toString(), modifier = Modifier.weight(0.9f), fontSize = 13.sp)
        Text(
            if (status.total > 0) "${status.processed}/${status.total}" else status.syncedRecords.toString(),
            modifier = Modifier.weight(0.9f),
            fontSize = 13.sp
        )
        Text(
            formatLastSync(status.lastSyncAt),
            modifier = Modifier.weight(1.6f),
            fontSize = 11.sp,
            color = Color(0xFF616161)
        )
    }
}

private fun formatLastSync(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

@Composable
private fun StatusChip(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        "Done" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        "Syncing" -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        "Error" -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        "Pending" -> Color(0xFFFFF8E1) to Color(0xFFF57F17)
        "Skipped" -> Color(0xFFEEEEEE) to Color(0xFF9E9E9E)
        else -> Color(0xFFF5F5F5) to Color(0xFF616161)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Text(
            status,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Never"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
