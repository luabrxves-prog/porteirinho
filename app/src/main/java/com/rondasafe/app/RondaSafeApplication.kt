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
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        super.onCreate()

        // Uma única fila persistente cuida da sincronização. Se a rede voltar,
        // apenas garantimos que o trabalho esteja agendado; não reiniciamos uma
        // sincronização que já esteja em andamento.
        OfflineSyncWorker.schedule(this)
        registerConnectivitySync()
    }

    private fun registerConnectivitySync() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runCatching {
                    OfflineSyncWorker.schedule(this@RondaSafeApplication, force = false)
                }
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
