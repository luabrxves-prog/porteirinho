package com.rondasafe.app.domain.service

import android.content.Context
import com.rondasafe.app.core.constants.PatrolExecutionStatus
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.remote.datasource.PortariaRemoteDataSource
import java.io.IOException

class PatrolAvailabilityService(
    context: Context,
    private val remote: PortariaRemoteDataSource,
    private val sessionStore: GuardSessionStore,
) {
    private val appContext = context.applicationContext

    suspend fun availablePatrols(): List<AvailablePatrolDto> {
        val guard = sessionStore.current ?: error("Porteiro não autenticado.")

        if (!guard.offline) {
            try {
                return remote.availablePatrols().patrols
            } catch (error: IOException) {
                if (!OfflineOperationalCache.hasCache(appContext)) throw error
            }
        }

        if (!OfflineOperationalCache.hasCache(appContext)) return emptyList()
        val dao = OfflineDatabase.get(appContext).offlineDao()

        return OfflineOperationalCache.availablePatrols(appContext, guard.guardId).map { patrol ->
            val local = dao.localOccurrence(patrol.scheduleWindowId, patrol.scheduledFor)
            when {
                local == null -> patrol
                local.active -> patrol.copy(
                    executionStatus = PatrolExecutionStatus.IN_PROGRESS.name,
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                local.requiredPoints > 0 && local.visitedPoints >= local.requiredPoints -> patrol.copy(
                    executionStatus = PatrolExecutionStatus.COMPLETED.name,
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
                else -> patrol.copy(
                    executionStatus = PatrolExecutionStatus.INCOMPLETE.name,
                    executedByGuardName = local.guardId.takeIf { it == guard.guardId }?.let { guard.guardName },
                )
            }
        }
    }
}
