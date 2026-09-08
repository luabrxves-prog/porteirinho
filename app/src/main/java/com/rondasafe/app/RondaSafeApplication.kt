package com.rondasafe.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.rondasafe.app.data.local.OfflineSyncWorker
import java.util.TimeZone

class RondaSafeApplication : Application() {
    lateinit var container: AppContainer
        private set

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        super.onCreate()

        container = AppContainer(this)
        container.deviceCredentialStore.restore()

        // Reagenda qualquer fila pendente ao abrir o aplicativo e dispara novamente
        // assim que uma rede utilizável reaparecer. O WorkManager continua sendo a
        // garantia persistente caso o processo esteja fechado.
        OfflineSyncWorker.schedule(this)
        registerConnectivitySync()
    }

    private fun registerConnectivitySync() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                OfflineSyncWorker.schedule(this@RondaSafeApplication, force = true)
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
