package com.rondasafe.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object SharedSyncBus {
    private val _epoch = MutableStateFlow(0L)
    val epoch: StateFlow<Long> = _epoch.asStateFlow()
    // UI invalidation tick, not a persisted server cursor. Concurrent publications cannot regress it.
    internal fun publish(version: Long) { _epoch.update { maxOf(it + 1, version) } }
}
