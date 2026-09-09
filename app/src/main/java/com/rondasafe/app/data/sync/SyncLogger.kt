package com.rondasafe.app.data.sync

import android.util.Log

object SyncLogger {
    private const val TAG = "RondaSafeSync"

    fun event(event: String, detail: String? = null) {
        val safeDetail = detail
            ?.replace(Regex("(?i)(pin|password|token|secret)=[^ ,;]+"), "$1=[redacted]")
            ?.take(180)
        Log.i(TAG, if (safeDetail.isNullOrBlank()) event else "$event • $safeDetail")
    }

    fun error(event: String, error: Throwable) {
        val message = error.message
            .orEmpty()
            .replace(Regex("(?i)(pin|password|token|secret)=[^ ,;]+"), "$1=[redacted]")
            .take(180)
        Log.w(TAG, "$event • ${error.javaClass.simpleName}: $message")
    }
}
