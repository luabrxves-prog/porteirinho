package com.rondasafe.app.presentation.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rondasafe.app.core.time.AppTime
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import com.rondasafe.app.presentation.admin.state.AdminDashboardState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdminDashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminDashboardState())
    val state: StateFlow<AdminDashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                coroutineScope {
                    val condominiumDeferred = async { AdminRepository.condominium() }
                    val alertsDeferred = async { AdminRepository.listAlerts().size }
                    val todayDeferred = async {
                        val today = AppTime.nowDate()
                        PatrolHistoryRepository.listRange(
                            from = today.atStartOfDay(AppTime.zone).toInstant(),
                            to = today.plusDays(1).atStartOfDay(AppTime.zone).toInstant().minusMillis(1),
                        )
                    }
                    AdminDashboardState(
                        condominium = condominiumDeferred.await().name,
                        today = todayDeferred.await(),
                        openAlerts = alertsDeferred.await(),
                        loading = false,
                    )
                }
            }.onSuccess { loaded ->
                _state.value = loaded
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Não foi possível carregar o painel.",
                    )
                }
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { AuthRepository.signOut() }
            onDone()
        }
    }
}
