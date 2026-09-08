package com.rondasafe.app.data.sync

import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.PendingEventEntity
import java.util.UUID

object PendingEventFactory {
    fun create(
        type: OfflineEventType,
        payloadJson: String,
        createdAtLocal: String,
        monotonicMs: Long? = null,
        clientEventId: String = UUID.randomUUID().toString(),
    ): PendingEventEntity = PendingEventEntity(
        clientEventId = clientEventId,
        type = type.name,
        payloadJson = payloadJson,
        createdAtLocal = createdAtLocal,
        monotonicMs = monotonicMs,
    )
}
