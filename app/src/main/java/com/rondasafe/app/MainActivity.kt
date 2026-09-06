package com.rondasafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.RondaSafeApp
import com.rondasafe.app.ui.theme.RondaSafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Credenciais locais antigas/corrompidas nunca devem impedir o app de abrir.
        runCatching { PortariaRepository.restoreDeviceCredential(this) }

        setContent {
            RondaSafeTheme {
                RondaSafeApp()
            }
        }
    }
}
