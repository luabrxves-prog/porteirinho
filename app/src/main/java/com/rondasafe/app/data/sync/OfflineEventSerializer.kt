package com.rondasafe.app.data.sync

import com.rondasafe.app.core.constants.OfflineEventType
import com.rondasafe.app.data.local.PendingEventEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class PreparedOfflineRequest(
    val functionName: String,
    val body: JsonObject,
)

class OfflineEventSerializer(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun prepare(event: PendingEventEntity): PreparedOfflineRequest {
        val payload = json.parseToJsonElement(event.payloadJson).jsonObject

        return if (event.type == OfflineEventType.GUARD_OCCURRENCE.name) {
            PreparedOfflineRequest(
                functionName = "guard-occurrence",
                body = buildJsonObject {
                    put("client_event_id", event.clientEventId)
                    put("guard_id", payload["guard_id"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("run_client_event_id", payload["run_client_event_id"]?.jsonPrimitive?.contentOrNull ?: "")
                    put("description", payload["description"]?.jsonPrimitive?.contentOrNull ?: "")
                    put(
                        "captured_at_local",
                        payload["captured_at_local"]?.jsonPrimitive?.contentOrNull ?: event.createdAtLocal,
                    )
                },
            )
        } else {
            PreparedOfflineRequest(
                functionName = "offline-ingest",
                body = buildJsonObject {
                    put("client_event_id", event.clientEventId)
                    put("type", event.type)
                    put("created_at_local", event.createdAtLocal)
                    event.monotonicMs?.let { put("monotonic_ms", it) }
                    put("payload", payload)
                },
            )
        }
    }
}
