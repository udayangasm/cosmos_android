package com.pos.cosmos.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pos.cosmos.utils.AppSettings
import com.pos.cosmos.utils.SessionManager
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync — mirrors the desktop SyncDataService auto-export
 * timer. WorkManager enforces a 15-minute minimum interval for periodic work,
 * so the user-chosen interval is clamped accordingly. For sub-15-minute
 * cadence, run a foreground sync from the SyncDataScreen instead.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = AppSettings.get(ctx)
        if (!settings.useLocalDatabase.value) {
            return Result.success()
        }
        val token = SessionManager(ctx).getToken() ?: return Result.retry()
        val repo = SyncRepository.get(ctx)
        return try {
            repo.exportPendingToServer(token)
            if (settings.autoSyncEnabled.value) {
                repo.pullAllFromServer(token)
            }
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.e("SyncWorker", "sync failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "cosmos_periodic_sync"
        private const val MIN_INTERVAL_MINUTES = 15L

        fun schedule(context: Context, intervalMinutes: Int) {
            val effective = intervalMinutes.toLong().coerceAtLeast(MIN_INTERVAL_MINUTES)
            val request = PeriodicWorkRequestBuilder<SyncWorker>(effective, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Enqueue a one-time sync immediately — mirrors desktop
         * SyncDataService.EnsureAutoExportRunning(). Called right after a
         * bill is queued locally so it gets pushed without waiting for the
         * 15-minute periodic tick.
         */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "cosmos_sync_once",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
