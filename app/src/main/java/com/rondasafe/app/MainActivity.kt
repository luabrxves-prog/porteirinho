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

        if (BuildConfig.DEBUG && BuildConfig.ENABLE_TEST_CLEAN_RESET) {
            TestCleanReset.runOnce(applicationContext)
        }

        setContent {
            RondaSafeTheme {
                RondaSafeApp()
            }
        }
    }
}
