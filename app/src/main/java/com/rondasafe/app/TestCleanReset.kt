package com.rondasafe.app

import android.content.Context
import java.io.File

/**
 * Reset único da versão de homologação 0.6.2.
 *
 * Serve para instalar o APK por cima das versões de teste anteriores sem carregar
 * fila offline, credencial de portaria, installation id, cache ou sessão admin antiga.
 * O marcador fica em filesDir e impede que o reset rode novamente depois que a
 * usuária começar um novo teste nesta versão.
 */
object TestCleanReset {
    private const val MARKER = "clean_test_reset_0_6_2.done"

    fun runOnce(context: Context) {
        val app = context.applicationContext
        val marker = File(app.filesDir, MARKER)
        if (marker.exists()) return

        // Banco Room operacional e arquivos auxiliares do SQLite.
        runCatching { app.deleteDatabase("rondasafe_offline.db") }

        // Preferências antigas: credencial criptografada da portaria, installation id,
        // cache operacional e armazenamento persistido da sessão Supabase.
        runCatching { clearDirectory(File(app.applicationInfo.dataDir, "shared_prefs")) }
        runCatching { clearDirectory(File(app.applicationInfo.dataDir, "datastore")) }

        // WorkManager mantém banco próprio; cancelar tudo evita que um worker antigo
        // volte a executar depois do reset. O banco do WorkManager não é apagado.
        runCatching { androidx.work.WorkManager.getInstance(app).cancelAllWork() }

        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText("0.6.2")
        }
    }

    private fun clearDirectory(directory: File) {
        if (!directory.exists()) return
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }
}
