package com.rondasafe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rondasafe.app.ui.RondaSafeApp
import com.rondasafe.app.ui.theme.RondaSafeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep Android as the source of truth for timezone. Do not force a global
        // process timezone here, otherwise event timestamps may be displayed or
        // interpreted with a zone different from the device configuration.
        OperationalDataReset.runIfNeeded(this)

        setContent {
            RondaSafeTheme {
                RondaSafeApp()
            }
        }
    }
}
