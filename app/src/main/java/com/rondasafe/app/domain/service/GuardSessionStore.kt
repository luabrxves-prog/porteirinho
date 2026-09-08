package com.rondasafe.app.domain.service

class GuardSessionStore {
    data class GuardSession(
        val guardId: String,
        val guardName: String,
        val token: String? = null,
        val offline: Boolean = false,
    )

    var current: GuardSession? = null
        private set

    fun set(session: GuardSession) {
        current = session
    }

    fun clear() {
        current = null
    }

    fun isOffline(): Boolean = current?.offline == true
}
