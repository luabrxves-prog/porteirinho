package com.rondasafe.app.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rondasafe.app.RondaSafeApplication
import com.rondasafe.app.data.sync.OfflineSyncOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = syncPending(applicationContext)

    companion object {
        private const val UNIQUE_WORK = "rondasafe-offline-sync"
        private val foregroundSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun syncPending(context: Context): Result {
            val application = context.applicationContext as? RondaSafeApplication
                ?: return Result.retry()
            return when (application.container.offlineSyncService.syncPending()) {
                OfflineSyncOutcome.SUCCESS -> Result.success()
                OfflineSyncOutcome.RETRY -> Result.retry()
            }
        }

        fun schedule(context: Context, force: Boolean = false) {
            val appContext = context.applicationContext

            foregroundSyncScope.launch {
                runCatching { syncPending(appContext) }
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS,
                )
                .build()

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
