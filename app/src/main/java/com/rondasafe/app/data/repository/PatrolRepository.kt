package com.rondasafe.app.data.repository

import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

object PatrolRepository {
    private val client get() = SupabaseProvider.client

    data class PatrolEditData(
        val template: PatrolTemplateDto,
        val windows: List<PatrolScheduleWindowDto>,
        val checkpointIds: Set<String>,
    )

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

    suspend fun loadForEdit(template: PatrolTemplateDto): PatrolEditData = PatrolEditData(
        template = template,
        windows = listWindows(template.id),
        checkpointIds = listTemplateCheckpoints(template.id).filter { it.required }.map { it.checkpointId }.toSet(),
    )

    suspend fun updateFixedPatrolHours(templateId: String, startTime: String, endTime: String) {
        val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        require(startTime.matches(timePattern)) { "Horário inicial inválido." }
        require(endTime.matches(timePattern)) { "Horário final inválido." }
        client.postgrest.rpc(
            "update_fixed_patrol_hours",
            buildJsonObject {
                put("p_template_id", templateId)
                put("p_start_time", "$startTime:00")
                put("p_end_time", "$endTime:00")
                put("p_late_tolerance_minutes", 15)
            },
        )
    }

    suspend fun listAssignments(windowId: String, includeArchived: Boolean = true): List<PatrolScheduleAssignmentDto> =
        client.from("patrol_schedule_assignments").select {
            filter {
                eq("schedule_window_id", windowId)
                if (!includeArchived) eq("active", true)
            }
        }.decodeList()

    suspend fun setAssignments(windowId: String, guardIds: Set<String>) {
        val adminId = currentAdminId()
        val now = Instant.now().toString()
        val existing = listAssignments(windowId, includeArchived = true)
        val byGuard = existing.associateBy { it.guardId }

        existing.filter { it.active && it.guardId !in guardIds }.forEach { assignment ->
            client.from("patrol_schedule_assignments").update(
                ArchiveDto(active = false, archivedAt = now, archivedBy = adminId)
            ) { filter { eq("id", assignment.id) } }
        }

        guardIds.forEach { guardId ->
            val assignment = byGuard[guardId]
            if (assignment == null) {
                client.from("patrol_schedule_assignments").insert(
                    CreatePatrolScheduleAssignmentDto(
                        scheduleWindowId = windowId,
                        guardId = guardId,
                        createdBy = adminId,
                    )
                )
            } else if (!assignment.active) {
                client.from("patrol_schedule_assignments").update(RestoreDto(active = true)) {
                    filter { eq("id", assignment.id) }
                }
            }
        }
    }

    suspend fun setAssignmentsForWindows(windowIds: Collection<String>, guardIds: Set<String>) {
        windowIds.distinct().forEach { windowId -> setAssignments(windowId, guardIds) }
    }

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
                        label = "${floor.name} • ${checkpoint.name}",
                    )
                }
            }
        }
        return options
    }

    suspend fun allActiveCheckpointIds(buildingId: String): List<String> =
        listCheckpointOptions(buildingId).map { it.checkpoint.id }.distinct()

    suspend fun saveTemplate(
        templateId: String? = null,
        buildingId: String,
        name: String,
        description: String?,
        lateToleranceMinutes: Int,
        days: List<PatrolDayConfig>,
        checkpointIds: List<String> = emptyList(),
    ): String {
        require(name.isNotBlank()) { "Informe um nome para a ronda." }
        require(days.any { it.enabled }) { "Selecione ao menos um dia da semana." }
        require(lateToleranceMinutes in 0..1440) { "Tolerância inválida." }

        val effectiveCheckpointIds = if (checkpointIds.isEmpty()) allActiveCheckpointIds(buildingId) else checkpointIds.distinct()
        require(effectiveCheckpointIds.isNotEmpty()) { "Cadastre pelo menos um ponto de ronda antes de criar a programação." }

        val enabledDays = days.filter { it.enabled }
        enabledDays.forEach { day ->
            require(day.startTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Horário inicial inválido." }
            require(day.endTime.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))) { "Horário final inválido." }
        }

        val params = buildJsonObject {
            if (templateId == null) put("p_template_id", JsonNull) else put("p_template_id", templateId)
            put("p_building_id", buildingId)
            put("p_name", name.trim())
            if (description.isNullOrBlank()) put("p_description", JsonNull) else put("p_description", description.trim())
            put("p_late_tolerance_minutes", lateToleranceMinutes)
            put("p_days", buildJsonArray {
                enabledDays.forEach { day ->
                    add(buildJsonObject {
                        put("day_of_week", day.dayOfWeek)
                        put("start_time", "${day.startTime}:00")
                        put("end_time", "${day.endTime}:00")
                    })
                }
            })
            put("p_checkpoint_ids", buildJsonArray { effectiveCheckpointIds.forEach { add(JsonPrimitive(it)) } })
        }
        return client.postgrest.rpc("save_patrol_template", params).decodeAs<String>()
    }

    suspend fun createTemplate(
        buildingId: String,
        name: String,
        description: String?,
        lateToleranceMinutes: Int,
        days: List<PatrolDayConfig>,
        checkpointIds: List<String> = emptyList(),
    ): PatrolTemplateDto {
        val id = saveTemplate(buildingId = buildingId, name = name, description = description, lateToleranceMinutes = lateToleranceMinutes, days = days, checkpointIds = checkpointIds)
        return client.from("patrol_templates").select { filter { eq("id", id) } }.decodeSingle()
    }

    suspend fun archiveTemplate(templateId: String) {
        val adminId = currentAdminId()
        client.from("patrol_templates").update(
            ArchiveDto(active = false, archivedAt = Instant.now().toString(), archivedBy = adminId)
        ) { filter { eq("id", templateId) } }
    }

    suspend fun restoreTemplate(templateId: String) {
        client.from("patrol_templates").update(RestoreDto(active = true)) {
            filter { eq("id", templateId) }
        }
    }
}
