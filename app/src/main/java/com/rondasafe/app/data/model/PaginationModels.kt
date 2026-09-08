package com.rondasafe.app.data.model

data class PageCursor(
    val timestamp: String,
    val id: String,
)

data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: PageCursor? = null,
    val hasMore: Boolean = false,
)
