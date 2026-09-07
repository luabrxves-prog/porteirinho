package com.rondasafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rondasafe.app.ui.RondaSafeApp
import com.rondasafe.app.ui.theme.RondaSafeTheme
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A operação do condomínio usa o fuso America/Sao_Paulo. Definir o fuso
        // explicitamente evita que builds/aparelhos configurados em UTC exibam +3h.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))

        // Não faça I/O, Keystore, Room ou rede antes da primeira tela. O bootstrap
        // operacional é carregado em background dentro de RondaSafeApp.
        setContent {
            RondaSafeTheme {
                RondaSafeApp()
            }
        }
    }
}
