package com.rondasafe.app.security

import android.content.Context

class DeviceCredentialStore(context: Context) {
    data class DeviceCredential(val deviceId: String, val deviceSecret: String)

    private val appContext = context.applicationContext

    var current: DeviceCredential? = null
        private set

    fun restore(): DeviceCredential? {
        val id = safeRead(DEVICE_ID_KEY)
        val secret = safeRead(DEVICE_SECRET_KEY)
        current = if (!id.isNullOrBlank() && !secret.isNullOrBlank()) {
            DeviceCredential(id, secret)
        } else {
            null
        }
        return current
    }

    fun set(credential: DeviceCredential) {
        current = credential
    }

    fun persist() {
        val credential = current ?: return
        OfflineCredentialVault.put(appContext, DEVICE_ID_KEY, credential.deviceId)
        OfflineCredentialVault.put(appContext, DEVICE_SECRET_KEY, credential.deviceSecret)
    }

    fun clear() {
        current = null
        OfflineCredentialVault.remove(appContext, DEVICE_ID_KEY)
        OfflineCredentialVault.remove(appContext, DEVICE_SECRET_KEY)
    }

    private fun safeRead(key: String): String? =
        runCatching { OfflineCredentialVault.get(appContext, key) }
            .getOrElse {
                runCatching { OfflineCredentialVault.remove(appContext, key) }
                null
            }

    private companion object {
        const val DEVICE_ID_KEY = "portaria_device_id"
        const val DEVICE_SECRET_KEY = "portaria_device_secret"
    }
}
