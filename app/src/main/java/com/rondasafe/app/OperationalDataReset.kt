package com.rondasafe.app

import android.content.Context
import com.rondasafe.app.data.local.OfflineOperationalCache

object OperationalDataReset {
    private const val PREFS = "rondasafe_migrations"
    private const val RESET_KEY = "operational_reset_2026_09_09_v2"
    private const val DB_NAME = "rondasafe_offline.db"

    /**
     * One-time cleanup for builds that accumulated stale patrol/outbox data.
     *
     * We intentionally keep:
     * - Supabase admin auth/session (stored by the auth SDK)
     * - device id/secret used to identify this portaria device
     *
     * We remove only operational/offline state. The operational cache is fetched
     * again from the server after the device credentials are restored.
     */
    fun runIfNeeded(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(RESET_KEY, false)) return

        // Delete Room database including WAL/SHM through Android's database API.
        appContext.deleteDatabase(DB_NAME)

        // Remove cached patrols, QR token hashes and offline PIN material. This does
        // NOT remove the device credential nor the administrator Supabase session.
        OfflineOperationalCache.clear(appContext)

        prefs.edit().putBoolean(RESET_KEY, true).apply()
    }
}
