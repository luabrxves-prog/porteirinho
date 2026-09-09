package com.rondasafe.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuardDto(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("pin_state") val pinState: String,
    val active: Boolean,
    val version: Long = 1,
)

@Serializable
data class GuardFunctionRequest(
    val action: String,
    val name: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("guard_id") val guardId: String? = null,
)

@Serializable
data class GuardFunctionResponse(
    val guard: GuardMutationDto? = null,
    @SerialName("temporary_pin") val temporaryPin: String? = null,
    val error: String? = null,
)

@Serializable
data class GuardMutationDto(
    @SerialName("guard_id") val guardId: String,
    @SerialName("guard_name") val guardName: String,
    @SerialName("pin_state") val pinState: String,
    @SerialName("credential_version") val credentialVersion: Int,
)
