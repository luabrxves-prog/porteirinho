package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.data.local.*
import com.rondasafe.app.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

interface PatrolOperations {
    suspend fun available(): List<AvailablePatrolDto>
    suspend fun start(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto
    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto
    suspend fun finish(runId: String): FinishPatrolDto
    suspend fun report(runId: String, description: String): Boolean
    suspend fun endShift(shiftId: String)
    fun observeRun(runId: String): Flow<LocalPatrolRunEntity?>
}

class RepositoryPatrolOperations(context: Context) : PatrolOperations {
    private val app = context.applicationContext
    private val dao get() = OfflineDatabase.get(app).offlineDao()
    override suspend fun available() = PortariaRepository.availablePatrols()
    override suspend fun start(shiftId: String, patrol: AvailablePatrolDto) = PortariaRepository.startPatrol(shiftId, patrol)
    override suspend fun scan(runId: String, qr: String, monotonicMs: Long) = PortariaRepository.scan(runId, qr, monotonicMs)
    override suspend fun report(runId: String, description: String) = OccurrenceRepository.report(app, runId, description)
    override fun observeRun(runId: String) = dao.localRunFlow(runId)
    override suspend fun finish(runId: String): FinishPatrolDto {
        try { return PortariaRepository.finishPatrol(runId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val local = dao.localRun(runId)
            if (local != null && !local.active) {
                PortariaRepository.clearGuardSession()
                return FinishPatrolDto("SYNC_FAILED", local.visitedPoints, local.requiredPoints, synced = false)
            }
            throw e
        }
    }
    override suspend fun endShift(shiftId: String) {
        try { PortariaRepository.endShift(shiftId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // The command may be durable but rejected/pending. The entry screen shows
            // the outbox state; do not trap the guard or call this server-confirmed.
            if (dao.localShift(shiftId)?.active != false) throw e
            PortariaRepository.clearGuardSession()
        }
    }
}
