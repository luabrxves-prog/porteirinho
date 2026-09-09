package com.rondasafe.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SharedSyncBus {
    private val _epoch = MutableStateFlow(0L)
    val epoch: StateFlow<Long> = _epoch.asStateFlow()

    internal fun publish(version: Long) {
        if (version > _epoch.value) _epoch.value = version
        else _epoch.value = _epoch.value + 1
    }
}
