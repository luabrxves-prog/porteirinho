package com.rondasafe.app.data.sync

import android.util.Log

/** Allowlisted diagnostics only: never log request bodies, exception messages or headers. */
object SyncLogger {
    private const val TAG = "RondaSafeSync"
    private val events = setOf("LOCAL_SAVE", "REMOTE_SAVE", "SYNC_PENDING", "SYNC_SUCCESS", "SYNC_ERROR", "REALTIME_INSERT", "REALTIME_UPDATE", "REALTIME_DELETE", "CONFLICT")
    private val safeFields = Regex("\\b(type|status|result|phase)=([A-Z][A-Z0-9_]{0,60})(?=\\s|$)|\\b(id|template)=([a-f0-9]{8})(?=\\s|$)|\\b(permanent|retryable)=(true|false)(?=\\s|$)")
    fun event(event: String, detail: String? = null) {
        if (event !in events) return
        val fields = safeFields.findAll(detail.orEmpty()).joinToString(" ") { it.value }.take(160)
        Log.i(TAG, if (fields.isBlank()) event else "$event $fields")
    }
    fun error(event: String, error: Throwable) {
        if (event !in events) return
        Log.w(TAG, "$event class=${error.javaClass.simpleName.take(80)}")
    }
}
