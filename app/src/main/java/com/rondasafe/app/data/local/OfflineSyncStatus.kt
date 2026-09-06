package com.rondasafe.app.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class OfflineSyncStatus(
    val pending: Int = 0,
    val failedPermanent: Int = 0,
    val latestError: String? = null,
) {
    val isSynced: Boolean get() = pending == 0 && failedPermanent == 0
}

object OfflineSyncStatusRepository {
    fun observe(context: Context): Flow<OfflineSyncStatus> {
        val dao = OfflineDatabase.get(context.applicationContext).offlineDao()
        return combine(
            dao.pendingCountFlow(),
            dao.permanentFailureCountFlow(),
            dao.latestPermanentFailureFlow(),
        ) { pending, failedPermanent, latestError ->
            OfflineSyncStatus(
                pending = pending,
                failedPermanent = failedPermanent,
                latestError = latestError,
            )
        }
    }
}
