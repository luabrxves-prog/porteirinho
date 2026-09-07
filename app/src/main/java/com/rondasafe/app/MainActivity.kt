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

        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))

        // Esta versão de homologação precisa iniciar limpa mesmo quando instalada
        // por cima de builds anteriores. O reset roda uma única vez por instalação
        // desta versão e preserva somente o usuário admin que está no Supabase.
        TestCleanReset.runOnce(applicationContext)

        setContent {
            RondaSafeTheme {
                RondaSafeApp()
            }
        }
    }
}
