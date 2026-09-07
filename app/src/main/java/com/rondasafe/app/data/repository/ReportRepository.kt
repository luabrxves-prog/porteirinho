package com.rondasafe.app.data.repository

import com.rondasafe.app.data.remote.SupabaseProvider
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ReportExportRequest(
    val days: Int,
)

@Serializable
data class ReportTotalsDto(
    val runs: Int = 0,
    val scans: Int = 0,
    val alerts: Int = 0,
    val audit: Int = 0,
)

@Serializable
data class ReportExportResponse(
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("file_base64") val fileBase64: String? = null,
    @SerialName("period_from") val periodFrom: String? = null,
    @SerialName("period_to") val periodTo: String? = null,
    val totals: ReportTotalsDto? = null,
    val error: String? = null,
    val message: String? = null,
)

object ReportRepository {
    private val client get() = SupabaseProvider.client

    suspend fun export(days: Int): ReportExportResponse {
        require(days in 1..366) { "O período deve ter entre 1 e 366 dias." }
        val response = client.functions.invoke(
            function = "admin-reports",
            body = ReportExportRequest(days),
        )
        return response.body<ReportExportResponse>().also { result ->
            result.error?.let { error(result.message ?: it) }
            require(!result.fileBase64.isNullOrBlank()) { "O relatório não retornou o arquivo Excel." }
            require(!result.fileName.isNullOrBlank()) { "O relatório não retornou o nome do arquivo." }
        }
    }
}
