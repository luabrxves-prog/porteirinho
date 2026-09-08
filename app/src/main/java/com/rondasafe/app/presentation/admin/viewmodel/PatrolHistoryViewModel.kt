package com.rondasafe.app.presentation.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rondasafe.app.data.model.PageCursor
import com.rondasafe.app.data.model.PatrolHistoryItemDto
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PatrolHistoryUiState(
    val items: List<PatrolHistoryItemDto> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val cursor: PageCursor? = null,
)

class PatrolHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PatrolHistoryUiState())
    val state: StateFlow<PatrolHistoryUiState> = _state.asStateFlow()

    private var query: Query? = null

    fun load(
        from: Instant,
        to: Instant,
        status: String? = null,
        guardId: String? = null,
        buildingId: String? = null,
        blockId: String? = null,
        floorId: String? = null,
    ) {
        query = Query(from, to, status, guardId, buildingId, blockId, floorId)
        fetch(reset = true)
    }

    fun refresh() { if (query != null) fetch(reset = true) }
    fun loadMore() { if (_state.value.hasMore && !_state.value.loadingMore) fetch(reset = false) }

    private fun fetch(reset: Boolean) {
        val q = query ?: return
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(
                loading = reset,
                loadingMore = !reset,
                error = null,
                cursor = if (reset) null else current.cursor,
            )
            runCatching {
                PatrolHistoryRepository.page(
                    from = q.from,
                    to = q.to,
                    status = q.status,
                    guardId = q.guardId,
                    buildingId = q.buildingId,
                    blockId = q.blockId,
                    floorId = q.floorId,
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
                    error = it.message ?: "Não foi possível carregar o histórico.",
                )
            }
        }
    }

    private data class Query(
        val from: Instant,
        val to: Instant,
        val status: String?,
        val guardId: String?,
        val buildingId: String?,
        val blockId: String?,
        val floorId: String?,
    )
}
