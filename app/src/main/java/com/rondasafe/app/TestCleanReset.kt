package com.rondasafe.app

import android.content.Context
import java.io.File

/**
 * Reset único da versão de homologação 0.6.3.
 *
 * Garante que o APK desta entrega comece sem dados locais de testes anteriores:
 * fila offline, credencial de portaria, installation id, cache e sessão antiga.
 * O marcador impede que o reset rode novamente depois do primeiro uso da versão.
 */
object TestCleanReset {
    private const val MARKER = "clean_test_reset_0_6_3.done"

    fun runOnce(context: Context) {
        val app = context.applicationContext
        val marker = File(app.filesDir, MARKER)
        if (marker.exists()) return

        runCatching { app.deleteDatabase("rondasafe_offline.db") }
        runCatching { clearDirectory(File(app.applicationInfo.dataDir, "shared_prefs")) }
        runCatching { clearDirectory(File(app.applicationInfo.dataDir, "datastore")) }
        runCatching { androidx.work.WorkManager.getInstance(app).cancelAllWork() }

        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("0.6.3")
        }
    }

    private fun clearDirectory(directory: File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }
}
