package com.rondasafe.app.data.local.cache

import com.rondasafe.app.data.model.CachedAssignmentDto
import com.rondasafe.app.data.model.CachedCheckpointDto
import com.rondasafe.app.data.model.CachedGuardDto
import com.rondasafe.app.data.model.CachedPatrolDto
import com.rondasafe.app.data.model.CachedQrDto
import com.rondasafe.app.data.model.PortariaCacheResponse

data class OperationalCacheIndex(
    val guardById: Map<String, CachedGuardDto>,
    val patrolById: Map<String, CachedPatrolDto>,
    val assignmentsByWindow: Map<String, List<CachedAssignmentDto>>,
    val requiredCheckpointsByPatrol: Map<String, Set<String>>,
    val qrByHash: Map<String, CachedQrDto>,
    val checkpointById: Map<String, CachedCheckpointDto>,
) {
    companion object {
        fun from(cache: PortariaCacheResponse): OperationalCacheIndex = OperationalCacheIndex(
            guardById = cache.guards.associateBy { it.id },
            patrolById = cache.patrols.associateBy { it.id },
            assignmentsByWindow = cache.assignments.groupBy { it.scheduleWindowId },
            requiredCheckpointsByPatrol = cache.patrolCheckpoints
                .asSequence()
                .filter { it.required }
                .groupBy({ it.patrolTemplateId }, { it.checkpointId })
                .mapValues { (_, ids) -> ids.toSet() },
            qrByHash = cache.qrTokens.associateBy { it.tokenHash.lowercase() },
            checkpointById = cache.checkpoints.associateBy { it.id },
        )
    }
}
