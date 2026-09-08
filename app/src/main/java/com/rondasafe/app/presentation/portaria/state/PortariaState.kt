package com.rondasafe.app.presentation.portaria.state

import com.rondasafe.app.core.result.UiState
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.PortariaGuardDto

data class PortariaState(
    val guards: UiState<List<PortariaGuardDto>> = UiState.Loading,
    val patrols: UiState<List<AvailablePatrolDto>> = UiState.Loading,
    val busy: Boolean = false,
    val error: String? = null,
)
