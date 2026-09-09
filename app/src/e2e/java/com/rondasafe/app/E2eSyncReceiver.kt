package com.rondasafe.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.remote.SupabaseProvider
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.security.OfflineCredentialVault
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class E2eSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CONFIGURE -> {
                        val deviceId = requireNotNull(intent.getStringExtra("device_id"))
                        val deviceSecret = requireNotNull(intent.getStringExtra("device_secret"))
                        OfflineCredentialVault.put(appContext, "portaria_device_id", deviceId)
                        OfflineCredentialVault.put(appContext, "portaria_device_secret", deviceSecret)
                        PortariaRepository.restoreDeviceCredential(appContext)
                        PortariaRepository.syncOperationalCache()
                        Log.i(TAG, "E2E_OK CONFIGURE ${deviceId.take(8)}")
                    }
                    ACTION_CACHE -> {
                        val guards = OfflineOperationalCache.guards(appContext)
                        Log.i(TAG, "E2E_OK CACHE count=${guards.size} names=${guards.joinToString("|") { it.name }}")
                    }
                    ACTION_LOGIN -> {
                        val guardId = requireNotNull(intent.getStringExtra("guard_id"))
                        val pin = requireNotNull(intent.getStringExtra("pin"))
                        val result = PortariaRepository.loginGuard(guardId, pin)
                        Log.i(TAG, "E2E_OK LOGIN guard=${result.guard?.id?.take(8)} mustChange=${result.mustChangePin}")
                    }
                    ACTION_CHANGE_PIN -> {
                        val pin = requireNotNull(intent.getStringExtra("pin"))
                        PortariaRepository.changePin(pin)
                        Log.i(TAG, "E2E_OK CHANGE_PIN")
                    }
                    ACTION_START_SHIFT -> {
                        val shift = PortariaRepository.startShift()
                        Log.i(TAG, "E2E_OK START_SHIFT id=${shift.shiftId.take(8)} synced=${shift.synced}")
                    }
                    ACTION_FORCE_SYNC -> {
                        val result = OfflineSyncWorker.syncPending(appContext)
                        Log.i(TAG, "E2E_OK FORCE_SYNC result=$result")
                    }
                    ACTION_SERVER_MUTATION -> {
                        val runId = requireNotNull(intent.getStringExtra("run_id"))
                        val action = requireNotNull(intent.getStringExtra("server_action"))
                        SupabaseProvider.client.postgrest.rpc(
                            "e2e_test_control",
                            buildJsonObject {
                                put("p_action", action)
                                put("p_run_id", runId)
                            },
                        )
                        Log.i(TAG, "E2E_OK SERVER_MUTATION $action")
                    }
                    else -> Log.e(TAG, "E2E_FAIL UNKNOWN_ACTION ${intent.action}")
                }
            } catch (error: Throwable) {
                Log.e(TAG, "E2E_FAIL ${intent.action} ${error.javaClass.simpleName}: ${error.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "RondaSafeE2E"
        const val ACTION_CONFIGURE = "com.rondasafe.app.e2e.CONFIGURE"
        const val ACTION_CACHE = "com.rondasafe.app.e2e.CACHE"
        const val ACTION_LOGIN = "com.rondasafe.app.e2e.LOGIN"
        const val ACTION_CHANGE_PIN = "com.rondasafe.app.e2e.CHANGE_PIN"
        const val ACTION_START_SHIFT = "com.rondasafe.app.e2e.START_SHIFT"
        const val ACTION_FORCE_SYNC = "com.rondasafe.app.e2e.FORCE_SYNC"
        const val ACTION_SERVER_MUTATION = "com.rondasafe.app.e2e.SERVER_MUTATION"
    }
}
