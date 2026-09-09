package com.rondasafe.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.data.sync.CentralSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.TimeZone

class RondaSafeApplication : Application(), DefaultLifecycleObserver {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        super<Application>.onCreate()

        PortariaRepository.restoreDeviceCredential(this)
        OfflineSyncWorker.schedule(this)
        registerConnectivitySync()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Cache-first no boot: Room já pode servir a interface imediatamente; em
        // paralelo buscamos o snapshot mais novo do servidor.
        appScope.launch {
            if (PortariaRepository.deviceCredential != null) {
                runCatching { PortariaRepository.syncOperationalCache() }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        CentralSyncManager.start(this)
        appScope.launch { CentralSyncManager.refreshNow(this@RondaSafeApplication) }
    }

    override fun onStop(owner: LifecycleOwner) {
        CentralSyncManager.stop()
    }

    private fun registerConnectivitySync() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching { OfflineSyncWorker.schedule(this@RondaSafeApplication, force = false) }
                appScope.launch { CentralSyncManager.refreshNow(this@RondaSafeApplication) }
            }
        }
        networkCallback = callback

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(callback)
            } else {
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback,
                )
            }
        }
    }
}
