package com.rondasafe.app.presentation.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rondasafe.app.data.model.AlertDto
import com.rondasafe.app.data.model.PageCursor
import com.rondasafe.app.data.repository.AdminRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlertsUiState(
    val items: List<AlertDto> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val resolution: String = "PENDING",
    val severity: String? = null,
    val days: Int = 30,
    val hasMore: Boolean = false,
    val cursor: PageCursor? = null,
)

class AlertsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AlertsUiState())
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    init { refresh() }

    fun setResolution(value: String) {
        _state.value = _state.value.copy(resolution = value)
        refresh()
    }

    fun setSeverity(value: String?) {
        _state.value = _state.value.copy(severity = value)
        refresh()
    }

    fun setDays(value: Int) {
        _state.value = _state.value.copy(days = value.coerceIn(1, 365))
        refresh()
    }

    fun refresh() = load(reset = true)
    fun loadMore() { if (_state.value.hasMore && !_state.value.loadingMore) load(reset = false) }

    fun markRead(alertId: String) = action {
        AdminRepository.markAlertRead(alertId)
        _state.value = _state.value.copy(items = _state.value.items.map {
            if (it.id == alertId && it.readAt == null) it.copy(readAt = Instant.now().toString()) else it
        })
    }

    fun resolve(alertId: String) = action {
        AdminRepository.resolveAlert(alertId)
        refresh()
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(
                loading = reset,
                loadingMore = !reset,
                error = null,
                cursor = if (reset) null else current.cursor,
            )
            val to = Instant.now()
            val from = to.minus(current.days.toLong(), ChronoUnit.DAYS)
            runCatching {
                AdminRepository.alertsPage(
                    from = from,
                    to = to,
                    resolution = current.resolution,
                    severity = current.severity,
                    cursor = if (reset) null else current.cursor,
                )
            }.onSuccess { page ->
                val base = if (reset) emptyList() else current.items
                _state.value = _state.value.copy(
                    items = base + page.items,
                    loading = false,
                    loadingMore = false,
                    hasMore = page.hasMore,
                    cursor = page.nextCursor,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = it.message ?: "Não foi possível carregar os alertas.",
                )
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Não foi possível concluir a ação.")
            }
        }
    }
}
