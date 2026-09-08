package com.rondasafe.app.domain.service

import android.content.Context
import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.core.constants.PatrolExecutionStatus
import com.rondasafe.app.data.local.LocalPatrolRunEntity
import com.rondasafe.app.data.local.LocalShiftEntity
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.model.ShiftDto
import com.rondasafe.app.data.sync.OfflineEventQueue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class PatrolRunService(
    context: Context,
    private val sessionStore: GuardSessionStore,
    private val eventQueue: OfflineEventQueue,
) {
    private val appContext = context.applicationContext
    private val dao get() = OfflineDatabase.get(appContext).offlineDao()

    suspend fun startShift(): ShiftDto {
        val guard = requireGuard()
        dao.activeLocalShift()?.let { error("Já existe um turno ativo neste aparelho.") }

        val eventId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        dao.saveLocalShift(
            LocalShiftEntity(
                shiftClientEventId = eventId,
                guardId = guard.guardId,
                guardName = guard.guardName,
                startedAtLocal = startedAt,
                active = true,
            )
        )
        eventQueue.enqueue(
            type = OfflineEventType.SHIFT_STARTED,
            clientEventId = eventId,
            createdAtLocal = startedAt,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("started_at_local", startedAt)
            }.toString(),
        )
        return ShiftDto(shiftId = eventId, startedAtServer = startedAt)
    }

    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto {
        require(patrol.executionStatus == PatrolExecutionStatus.AVAILABLE.name) {
            "Esta ronda já foi iniciada ou finalizada neste horário."
        }
        val guard = requireGuard()
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) {
            error("Turno inválido para este porteiro.")
        }
        dao.activeLocalRun()?.let { error("Já existe uma ronda em andamento.") }
        dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor)?.let {
            error("Esta ronda já foi iniciada ou finalizada neste horário.")
        }

        val eventId = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        dao.saveLocalRun(
            LocalPatrolRunEntity(
                runClientEventId = eventId,
                shiftClientEventId = shiftId,
                guardId = guard.guardId,
                patrolTemplateId = patrol.patrolTemplateId,
                scheduleWindowId = patrol.scheduleWindowId,
                patrolName = patrol.patrolName,
                scheduledFor = patrol.scheduledFor,
                isLate = patrol.isLate,
                requiredPoints = patrol.requiredPoints,
                visitedPoints = 0,
                startedAtLocal = startedAt,
                active = true,
            )
        )
        eventQueue.enqueue(
            type = OfflineEventType.PATROL_STARTED,
            clientEventId = eventId,
            createdAtLocal = startedAt,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("shift_client_event_id", shiftId)
                put("patrol_template_id", patrol.patrolTemplateId)
                put("schedule_window_id", patrol.scheduleWindowId)
                put("scheduled_for", patrol.scheduledFor)
                put("started_at_local", startedAt)
                put("is_late", patrol.isLate)
            }.toString(),
        )
        return PatrolRunDto(
            runId = eventId,
            requiredPoints = patrol.requiredPoints,
            startedAtServer = startedAt,
        )
    }

    suspend fun finishPatrol(runId: String): FinishPatrolDto {
        val guard = requireGuard()
        val run = dao.localRun(runId) ?: error("Ronda local não encontrada.")
        if (!run.active || run.guardId != guard.guardId) error("Ronda não está em andamento.")

        val finishedAt = Instant.now().toString()
        val visitedIds = dao.localVisitedCheckpointIds(runId).toSet()
        val requiredIds = OfflineOperationalCache.requiredCheckpointIds(appContext, run.patrolTemplateId)
        val missing = (requiredIds - visitedIds).toList()
        val visited = visitedIds.count { it in requiredIds }
        val total = requiredIds.size
        val status = if (total > 0 && missing.isEmpty()) {
            PatrolExecutionStatus.COMPLETED
        } else {
            PatrolExecutionStatus.INCOMPLETE
        }

        eventQueue.enqueue(
            type = OfflineEventType.PATROL_FINISHED,
            createdAtLocal = finishedAt,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("run_client_event_id", runId)
                put("finished_at_local", finishedAt)
            }.toString(),
        )
        dao.finishLocalRun(runId)
        dao.finishLocalShift(run.shiftClientEventId)
        sessionStore.clear()

        return FinishPatrolDto(
            status = status.name,
            visitedPoints = visited,
            totalPoints = total,
            missingCheckpointIds = missing,
        )
    }

    suspend fun endShift(shiftId: String) {
        val guard = requireGuard()
        dao.activeLocalRun()?.let { error("Finalize a ronda em andamento antes de encerrar o turno.") }
        val shift = dao.activeLocalShift() ?: error("Turno local não encontrado.")
        if (shift.shiftClientEventId != shiftId || shift.guardId != guard.guardId) error("Turno inválido.")

        val endedAt = Instant.now().toString()
        eventQueue.enqueue(
            type = OfflineEventType.SHIFT_ENDED,
            createdAtLocal = endedAt,
            payloadJson = buildJsonObject {
                put("guard_id", guard.guardId)
                put("shift_client_event_id", shiftId)
                put("ended_at_local", endedAt)
            }.toString(),
        )
        dao.finishLocalShift(shiftId)
        sessionStore.clear()
    }

    private fun requireGuard(): GuardSessionStore.GuardSession =
        sessionStore.current ?: error("Porteiro não autenticado.")
}
