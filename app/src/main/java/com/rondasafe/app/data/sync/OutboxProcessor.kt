package com.rondasafe.app.data.sync

import androidx.room.withTransaction
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant

@Serializable
data class SyncAck(
    val ack: Boolean = false,
    val retryable: Boolean? = null,
    val error: String? = null,
    @SerialName("client_event_id") val clientEventId: String? = null,
    @SerialName("shift_id") val shiftId: String? = null,
    @SerialName("run_id") val runId: String? = null,
    val shift: ShiftDto? = null,
    val run: PatrolRunDto? = null,
    val scan: ScanDto? = null,
    val result: JsonElement? = null,
)

/** The UI and WorkManager use the same ordered pump, never competing write paths. */
class OutboxProcessor(
    private val db: OfflineDatabase,
    private val transport: suspend (PendingEventEntity) -> SyncAck,
) {
    companion object {
        private val execution = Mutex()
        val json = Json { ignoreUnknownKeys = true }
    }
    private val dao get() = db.offlineDao()

    suspend fun drain(maxEvents: Int = 250): Boolean = execution.withLock {
        var delivered = 0
        while (delivered < maxEvents) {
            val batch = dao.pending()
            if (batch.isEmpty()) return@withLock true
            var madeProgress = false
            for (event in batch) {
                val payload = try { json.parseToJsonElement(event.payloadJson).jsonObject } catch (e: Exception) {
                    reject(event, "INVALID_LOCAL_PAYLOAD")
                    madeProgress = true
                    continue
                }
                if (blocked(event, payload)) continue
                SyncLogger.event("SYNC_PENDING", "type=${event.type} id=${event.clientEventId.take(8)}")
                val response = try { transport(event) } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dao.markFailed(event.clientEventId, "CONNECTION_OR_PROTOCOL_ERROR")
                    SyncLogger.error("SYNC_ERROR", e)
                    return@withLock false
                }
                if (!response.ack) {
                    val code = response.error?.takeIf { it.matches(Regex("[A-Z0-9_]{1,100}")) } ?: "SERVER_REJECTED_EVENT"
                    if (response.retryable == false) {
                        reject(event, code)
                        madeProgress = true
                        continue
                    }
                    dao.markFailed(event.clientEventId, code)
                    return@withLock false
                }
                try {
                    check(response.clientEventId == null || response.clientEventId == event.clientEventId) { "ACK_ID_MISMATCH" }
                    db.withTransaction {
                        applyReceipt(event, payload, response)
                        dao.markSynced(event.clientEventId, Instant.now().toString(), json.encodeToString(response))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dao.markFailed(event.clientEventId, "INVALID_SERVER_RECEIPT")
                    SyncLogger.error("SYNC_ERROR", e)
                    return@withLock false
                }
                SyncLogger.event("SYNC_SUCCESS", "type=${event.type} id=${event.clientEventId.take(8)}")
                madeProgress = true
                delivered++
            }
            if (!madeProgress) return@withLock false
        }
        dao.pendingCount() == 0
    }

    /** Reconcile even when no UI is open. Keep the event and dependent evidence for review. */
    private suspend fun reject(event: PendingEventEntity, reason: String) = db.withTransaction {
        dao.markPermanentFailure(event.clientEventId, reason)
        when (event.type) {
            "SHIFT_STARTED" -> {
                dao.localShift(event.clientEventId)?.let {
                    dao.saveLocalShift(it.copy(active = false, syncState = PendingEventEntity.STATE_FAILED_PERMANENT))
                }
                dao.activeLocalRun()?.takeIf { it.shiftClientEventId == event.clientEventId }?.let {
                    dao.saveLocalRun(it.copy(active = false, syncState = PendingEventEntity.STATE_FAILED_PERMANENT))
                }
            }
            "PATROL_STARTED" -> dao.localRun(event.clientEventId)?.let {
                dao.saveLocalRun(it.copy(active = false, syncState = PendingEventEntity.STATE_FAILED_PERMANENT))
            }
            else -> Unit
        }
    }

    private suspend fun blocked(event: PendingEventEntity, payload: JsonObject): Boolean {
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        val runId = payload.text("run_client_event_id")
        val shiftId = payload.text("shift_client_event_id")
        val parent = runId ?: shiftId
        if (parent != null) {
            val state = dao.eventState(parent)
            if (state != null && state != PendingEventEntity.STATE_SYNCED) return true
        }
        if (event.type == "PATROL_FINISHED") {
            return dao.unsettled().any { other ->
                other.clientEventId != event.clientEventId && other.type in setOf("QR_SCANNED", "GUARD_OCCURRENCE") &&
                    runCatching { json.parseToJsonElement(other.payloadJson).jsonObject.text("run_client_event_id") == runId }.getOrDefault(false)
            }
        }
        if (event.type == "SHIFT_ENDED" && shiftId != null) {
            for (other in dao.unsettled()) {
                if (other.clientEventId == event.clientEventId) continue
                val otherPayload = runCatching { json.parseToJsonElement(other.payloadJson).jsonObject }.getOrNull() ?: continue
                if (other.type == "PATROL_STARTED" && otherPayload.text("shift_client_event_id") == shiftId) return true
                val otherRunId = otherPayload.text("run_client_event_id") ?: continue
                if (dao.localRun(otherRunId)?.shiftClientEventId == shiftId) return true
            }
        }
        return false
    }

    private suspend fun applyReceipt(event: PendingEventEntity, payload: JsonObject, ack: SyncAck) {
        val runClientId = payload["run_client_event_id"]?.jsonPrimitive?.contentOrNull
        when (event.type) {
            "SHIFT_STARTED" -> {
                val id = ack.shift?.shiftId ?: ack.shiftId ?: error("SHIFT_ACK_MISSING_ID")
                dao.markShiftSynced(event.clientEventId, id)
            }
            "PATROL_STARTED" -> {
                val run = ack.run ?: error("PATROL_ACK_MISSING_RESULT")
                dao.markRunStartedSynced(event.clientEventId, run.runId)
                dao.updateLocalRequired(event.clientEventId, run.requiredPoints)
            }
            "QR_SCANNED" -> {
                val runId = runClientId ?: error("SCAN_PARENT_MISSING")
                val scan = ack.scan ?: error("SCAN_ACK_MISSING_RESULT")
                val point = scan.checkpointId ?: payload["checkpoint_id"]?.jsonPrimitive?.contentOrNull
                if (point != null) {
                    if (scan.scanResult in setOf("ACCEPTED", "DUPLICATE")) {
                        dao.addLocalVisit(LocalVisitedCheckpointEntity(runId, point, scan.checkpointName ?: "Ponto", event.createdAtLocal, confirmed = true))
                        dao.confirmLocalVisit(runId, point)
                    } else dao.removeProvisionalVisit(runId, point)
                }
                dao.updateLocalVisited(runId, dao.localVisitCount(runId))
            }
            "PATROL_FINISHED" -> {
                val runId = runClientId ?: error("FINISH_PARENT_MISSING")
                val result = ack.result?.let { json.decodeFromJsonElement<FinishPatrolDto>(it) } ?: error("FINISH_ACK_MISSING_RESULT")
                check(result.status in setOf("COMPLETED", "INCOMPLETE")) { "INVALID_FINAL_STATUS" }
                dao.removeMissingVisits(runId, result.missingCheckpointIds)
                dao.updateLocalVisited(runId, result.visitedPoints)
                dao.updateLocalRequired(runId, result.totalPoints)
                dao.markRunFinishedSynced(runId, result.status)
                dao.localRun(runId)?.let { dao.markShiftEndedSynced(it.shiftClientEventId) }
            }
            "SHIFT_ENDED" -> payload["shift_client_event_id"]?.jsonPrimitive?.contentOrNull?.let { dao.markShiftEndedSynced(it) }
            "GUARD_OCCURRENCE" -> Unit
            else -> error("UNSUPPORTED_EVENT_TYPE")
        }
    }
}
