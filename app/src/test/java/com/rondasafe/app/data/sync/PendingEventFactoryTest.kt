package com.rondasafe.app.data.sync

import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.PendingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingEventFactoryTest {
    @Test
    fun `creates pending offline event preserving identifiers and payload`() {
        val event: PendingEventEntity = PendingEventFactory.create(
            type = OfflineEventType.PATROL_STARTED,
            payloadJson = "{\"run\":\"local-1\"}",
            createdAtLocal = "2026-09-08T14:00:00Z",
            monotonicMs = 123L,
            clientEventId = "event-1",
        )

        assertEquals("event-1", event.clientEventId)
        assertEquals("PATROL_STARTED", event.type)
        assertEquals("{\"run\":\"local-1\"}", event.payloadJson)
        assertEquals(123L, event.monotonicMs)
        assertEquals(PendingEventEntity.STATE_PENDING, event.state)
    }
}
