package com.rondasafe.app

import android.app.Application
import java.util.TimeZone

class RondaSafeApplication : Application() {
    override fun onCreate() {
        // O sistema armazena timestamps em UTC, mas toda a experiência operacional
        // do condomínio deve ser exibida no fuso de São Paulo. Configurar aqui,
        // antes de Activities, Room, Supabase e formatters, evita ZoneId.systemDefault()
        // ser inicializado em UTC pelo processo Android.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        super.onCreate()
    }
}
