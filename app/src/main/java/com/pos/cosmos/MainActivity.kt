package com.pos.cosmos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.pos.cosmos.navigation.NavGraph
import com.pos.cosmos.navigation.Screen
import com.pos.cosmos.sync.SyncRepository
import com.pos.cosmos.ui.theme.COSMOSTheme
import com.pos.cosmos.utils.AppSettings
import com.pos.cosmos.utils.ResponsiveProvider
import com.pos.cosmos.utils.SessionManager
import com.pos.cosmos.viewmodels.AppViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            COSMOSTheme {
                ResponsiveProvider {
                    Scaffold {
                        MainApp()
                    }
                }
            }
        }
    }
}

@Composable
fun MainApp(appViewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val token by appViewModel.token.collectAsState()
    val context = LocalContext.current

    // ── App-scope periodic sync (mirrors desktop SyncDataService timer) ──
    // WorkManager PeriodicWorkRequest has a 15-minute floor, so we run our
    // own loop here while the app is in the foreground. The SyncWorker
    // periodic request still runs as a safety net for background ticks.
    LaunchedEffect(Unit) {
        val settings = AppSettings.get(context)
        val repo = SyncRepository.get(context)
        val session = SessionManager.getInstance(context)
        while (true) {
            val mins = settings.autoSyncIntervalMinutes.value.coerceAtLeast(1)
            delay(mins * 60_000L)
            if (!settings.useLocalDatabase.value) continue
            val authToken = session.getToken() ?: continue
            runCatching { repo.exportPendingToServer(authToken) }
                .onFailure { android.util.Log.e("MainApp", "periodic export failed", it) }
        }
    }

    // Always start with login, navigation will handle transitions
    NavGraph(
        navController = navController,
        appViewModel = appViewModel,
        startDestination = Screen.Login.route
    )
}
