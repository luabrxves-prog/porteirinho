package com.rondasafe.app.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dao = OfflineDatabase.get(applicationContext).offlineDao()
        val pending = dao.pending()
        if (pending.isEmpty()) return Result.success()

        // O envio idempotente de cada tipo de evento será ligado ao endpoint
        // de ingestão offline. Até lá, nunca removemos eventos sem ACK do servidor.
        return Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK = "rondasafe-offline-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
