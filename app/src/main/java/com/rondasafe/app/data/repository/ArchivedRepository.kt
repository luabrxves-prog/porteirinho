package com.rondasafe.app.data.repository

import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ArchivedRecord(
    val id: String,
    @SerialName("entity_type") val type: String,
    val name: String,
    val category: String,
    val subtitle: String,
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("blocked_reason") val blockedReason: String? = null,
) {
    val key: String get() = "$type:$id"
}

interface ArchivedDataSource {
    suspend fun list(): List<ArchivedRecord>
    suspend fun restore(item: ArchivedRecord)
    suspend fun delete(item: ArchivedRecord)
}

/** One paginated read model; opening this screen must never create/restore locations. */
object ArchivedRepository : ArchivedDataSource {
    override suspend fun list(): List<ArchivedRecord> {
        val rows = mutableListOf<ArchivedRecord>()
        var offset = 0
        do {
            val page = SupabaseProvider.client.postgrest.rpc(
                "admin_list_archived_items",
                buildJsonObject { put("p_limit", 200); put("p_offset", offset) },
            ).decodeList<ArchivedRecord>()
            rows.addAll(page)
            offset += page.size
        } while (page.size == 200)
        return rows.distinctBy { it.key }
    }

    override suspend fun restore(item: ArchivedRecord) {
        when (item.type) {
            "guard" -> GuardRepository.restore(item.id)
            "floor" -> AdminRepository.restore("floors", item.id)
            "checkpoint" -> AdminRepository.restore("checkpoints", item.id)
            "patrol_template" -> PatrolRepository.restoreTemplate(item.id)
            else -> error("Tipo de cadastro não suportado.")
        }
    }

    override suspend fun delete(item: ArchivedRecord) {
        require(item.canDelete) { item.blockedReason ?: "Este item deve permanecer arquivado." }
        // The RPC independently locks the row and rechecks history and active/fixed flags.
        AdminRepository.deleteArchived(item.type, item.id)
    }
}
