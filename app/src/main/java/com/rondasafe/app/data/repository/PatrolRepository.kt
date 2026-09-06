package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.time.Instant

object PatrolRepository {
    private val client get() = SupabaseProvider.client

    private fun currentAdminId(): String =
        client.auth.currentUserOrNull()?.id
            ?: error("Sessão administrativa não encontrada.")

    suspend fun listTemplates(includeArchived: Boolean = false): List<PatrolTemplateDto> =
        client.from("patrol_templates").select {
            if (!includeArchived) filter { eq("active", true) }
        }.decodeList()

    suspend fun listWindows(templateId: String, includeArchived: Boolean = false): List<PatrolScheduleWindowDto> =
        client.from("patrol_schedule_windows").select {
            filter {
                eq("patrol_template_id", templateId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList<PatrolScheduleWindowDto>().sortedBy { it.dayOfWeek }

    suspend fun listTemplateCheckpoints(templateId: String, includeArchived: Boolean = false): List<PatrolTemplateCheckpointDto> =
        client.from("patrol_template_checkpoints").select {
            filter {
                eq("patrol_template_id", templateId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList()

    suspend fun listCheckpointOptions(buildingId: String): List<PatrolCheckpointOption> {
        val blocks = AdminRepository.listBlocks(buildingId)
        val options = mutableListOf<PatrolCheckpointOption>()
        for (block in blocks) {
            val floors = AdminRepository.listFloors(block.id)
            for (floor in floors) {
                val checkpoints = AdminRepository.listCheckpoints(floor.id)
                checkpoints.forEach { checkpoint ->
                    options += PatrolCheckpointOption(
                        checkpoint = checkpoint,
                        label = "${block.name} • ${floor.name} • ${checkpoint.name}",
                    )
                }
            }
        }
        return options
    }

    suspend fun createTemplate(
        buildingId: String,
        name: String,
        description: String?,
        lateToleranceMinutes: Int,
        days: List<PatrolDayConfig>,
        checkpointIds: List<String>,
    ): PatrolTemplateDto {
        require(name.isNotBlank()) { "Informe um nome para a ronda." }
        require(days.any { it.enabled }) { "Selecione ao menos um dia da semana." }
        require(checkpointIds.isNotEmpty()) { "Selecione ao menos um ponto obrigatório." }
        require(lateToleranceMinutes in 0..1440) { "Tolerância inválida." }

        val adminId = currentAdminId()
        val template = client.from("patrol_templates")
            .insert(
                CreatePatrolTemplateDto(
                    buildingId = buildingId,
                    name = name.trim(),
                    description = description?.trim()?.takeIf { it.isNotEmpty() },
                    createdBy = adminId,
                )
            ) { select() }
            .decodeSingle<PatrolTemplateDto>()

        days.filter { it.enabled }.forEach { day ->
            require(day.startTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Horário inicial inválido." }
            require(day.endTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Horário final inválido." }
            client.from("patrol_schedule_windows").insert(
                CreatePatrolScheduleWindowDto(
                    patrolTemplateId = template.id,
                    dayOfWeek = day.dayOfWeek,
                    startTime = day.startTime,
                    endTime = day.endTime,
                    lateToleranceMinutes = lateToleranceMinutes,
                    createdBy = adminId,
                )
            )
        }

        checkpointIds.distinct().forEach { checkpointId ->
            client.from("patrol_template_checkpoints").insert(
                CreatePatrolTemplateCheckpointDto(
                    patrolTemplateId = template.id,
                    checkpointId = checkpointId,
                    createdBy = adminId,
                )
            )
        }

        return template
    }

    suspend fun archiveTemplate(templateId: String) {
        val adminId = currentAdminId()
        val now = Instant.now().toString()
        client.from("patrol_templates").update(
            ArchiveDto(archivedAt = now, archivedBy = adminId)
        ) { filter { eq("id", templateId) } }
    }

    suspend fun restoreTemplate(templateId: String) {
        client.from("patrol_templates").update(RestoreDto()) {
            filter { eq("id", templateId) }
        }
    }
}
