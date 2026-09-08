package com.rondasafe.app.data.sync

import android.content.Context
import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import java.util.UUID

class OfflineEventQueue(context: Context) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()

    suspend fun enqueue(
        type: OfflineEventType,
        payloadJson: String,
        createdAtLocal: String,
        monotonicMs: Long? = null,
        clientEventId: String = UUID.randomUUID().toString(),
    ): String {
        dao.enqueue(
            PendingEventEntity(
                clientEventId = clientEventId,
                type = type.name,
                payloadJson = payloadJson,
                createdAtLocal = createdAtLocal,
                monotonicMs = monotonicMs,
            )
        )
        requestSync()
        return clientEventId
    }

    suspend fun pendingCount(): Int = dao.pendingCount()

    fun requestSync(force: Boolean = false) {
        OfflineSyncWorker.schedule(appContext, force)
    }
}
