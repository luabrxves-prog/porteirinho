package com.rondasafe.app.data.sync

import androidx.room.withTransaction
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.PendingEventEntity
import kotlinx.serialization.json.*
import java.time.Instant

/** Resolve only rejected EMPTY starts after a different start was acknowledged.
 * Never invent an ACK, resend an obsolete request, or discard a scan/round.
 * Called under the outbox execution mutex; the check and update are atomic.
 */
object ObsoleteShiftConflictReconciler {
    suspend fun reconcile(db: OfflineDatabase) = db.withTransaction {
        val dao = db.offlineDao()
        val confirmed = dao.confirmedShifts().filter {
            dao.eventState(it.shiftClientEventId) == PendingEventEntity.STATE_SYNCED
        }
        if (confirmed.isEmpty()) return@withTransaction
        val unresolved = dao.unsettled()
        for (event in unresolved) {
            if (event.type != "SHIFT_STARTED" || event.state != PendingEventEntity.STATE_FAILED_PERMANENT ||
                event.lastError !in setOf("GUARD_ALREADY_HAS_ACTIVE_SHIFT", "DEVICE_ALREADY_HAS_ACTIVE_SHIFT")) continue
            val rejected = dao.localShift(event.clientEventId) ?: continue
            if (rejected.active || rejected.serverShiftId != null || dao.runsForShiftCount(event.clientEventId) > 0) continue
            val eventTime = runCatching { Instant.parse(event.createdAtLocal) }.getOrNull() ?: continue
            val accepted = confirmed.firstOrNull {
                it.guardId == rejected.guardId && it.shiftClientEventId != event.clientEventId &&
                    (it.active || runCatching { !Instant.parse(it.startedAtLocal).isBefore(eventTime) }.getOrDefault(false))
            } ?: continue
            val hasDependents = unresolved.any { other ->
                if (other.clientEventId == event.clientEventId) false
                else runCatching {
                    val payload = OutboxProcessor.json.parseToJsonElement(other.payloadJson).jsonObject
                    payload["shift_client_event_id"]?.jsonPrimitive?.contentOrNull == event.clientEventId
                }.getOrDefault(true) // Unknown/corrupt evidence is never silently retired.
            }
            if (hasDependents) continue
            val resolution = buildJsonObject {
                put("ack", false)
                put("resolution", "EMPTY_CONFLICT_SUPERSEDED_BY_CONFIRMED_SHIFT")
                put("replacement_client_event_id", accepted.shiftClientEventId)
                put("replacement_server_shift_id", accepted.serverShiftId)
                put("resolved_at", Instant.now().toString())
            }.toString()
            dao.markShiftConflictSuperseded(event.clientEventId, resolution)
            dao.saveLocalShift(rejected.copy(syncState = PendingEventEntity.STATE_SUPERSEDED))
        }
    }
}
