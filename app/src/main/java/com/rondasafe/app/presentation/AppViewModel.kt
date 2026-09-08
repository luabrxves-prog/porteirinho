package com.rondasafe.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.rondasafe.app.RondaSafeApplication
import com.rondasafe.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as RondaSafeApplication).container

    private val _deviceReady = MutableStateFlow(container.deviceCredentialStore.current != null)
    val deviceReady: StateFlow<Boolean> = _deviceReady.asStateFlow()

    init {
        container.offlineEventQueue.requestSync()
    }

    fun refreshDeviceStatus() {
        _deviceReady.value = container.deviceCredentialStore.current != null
    }

    fun clearGuardSession() {
        container.guardSessionStore.clear()
    }

    fun hasAdminSession(): Boolean =
        runCatching { AuthRepository.hasSession() }.getOrDefault(false)
}
