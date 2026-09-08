package com.rondasafe.app.presentation.portaria.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rondasafe.app.RondaSafeApplication
import com.rondasafe.app.core.result.UiState
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.model.ScanDto
import com.rondasafe.app.data.model.ShiftDto
import com.rondasafe.app.presentation.portaria.state.PortariaState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PortariaViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as RondaSafeApplication).container

    private val _state = MutableStateFlow(PortariaState())
    val state: StateFlow<PortariaState> = _state.asStateFlow()

    val guardName: String
        get() = container.guardSessionStore.current?.guardName.orEmpty()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSession() {
        container.guardSessionStore.clear()
    }

    fun loadGuards() {
        viewModelScope.launch {
            _state.update { it.copy(guards = UiState.Loading, error = null) }
            runCatching { container.guardAuthService.listGuards() }
                .onSuccess { guards ->
                    _state.update { it.copy(guards = UiState.Success(guards)) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Não foi possível carregar os porteiros."
                    _state.update { it.copy(guards = UiState.Error(message), error = message) }
                }
        }
    }

    fun loginGuard(guardId: String, pin: String, onSuccess: (Boolean) -> Unit) = action {
        val result = container.guardAuthService.loginGuard(guardId, pin)
        onSuccess(result.mustChangePin)
    }

    fun changePin(newPin: String, onSuccess: () -> Unit) = action {
        container.guardAuthService.changePin(newPin)
        onSuccess()
    }

    fun startShift(onSuccess: (ShiftDto) -> Unit) = action {
        onSuccess(container.patrolRunService.startShift())
    }

    fun refreshPatrols() {
        viewModelScope.launch {
            _state.update { it.copy(patrols = UiState.Loading, error = null) }
            runCatching { container.patrolAvailabilityService.availablePatrols() }
                .onSuccess { patrols ->
                    _state.update { it.copy(patrols = UiState.Success(patrols)) }
                }
                .onFailure { error ->
                    val message = error.message ?: "Não foi possível carregar as rondas."
                    _state.update { it.copy(patrols = UiState.Error(message), error = message) }
                }
        }
    }

    fun startPatrol(
        shiftId: String,
        patrol: AvailablePatrolDto,
        onSuccess: (PatrolRunDto) -> Unit,
    ) = action(
        onFailure = { refreshPatrols() },
    ) {
        onSuccess(container.patrolRunService.startPatrol(shiftId, patrol))
    }

    fun endShift(shiftId: String, onSuccess: () -> Unit) = action {
        container.patrolRunService.endShift(shiftId)
        onSuccess()
    }

    fun scan(runId: String, qr: String, monotonicMs: Long, onSuccess: (ScanDto) -> Unit) = action {
        onSuccess(container.qrScanService.scan(runId, qr, monotonicMs))
    }

    fun finishPatrol(runId: String, onSuccess: (FinishPatrolDto) -> Unit) = action {
        onSuccess(container.patrolRunService.finishPatrol(runId))
    }

    fun reportOccurrence(runId: String, description: String, onSuccess: () -> Unit) = action {
        container.occurrenceService.report(runId, description)
        onSuccess()
    }

    private fun action(
        onFailure: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message ?: "Ocorreu um erro inesperado.") }
                    onFailure?.invoke()
                }
            _state.update { it.copy(busy = false) }
        }
    }
}
