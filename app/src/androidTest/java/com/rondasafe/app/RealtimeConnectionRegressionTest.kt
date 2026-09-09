package com.rondasafe.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.sync.CentralSyncManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the real read-only Supabase invalidation channel; no production records are mutated. */
@RunWith(AndroidJUnit4::class)
class RealtimeConnectionRegressionTest {
    @Test fun websocketSubscribesAfterRapidForegroundTransitions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(4) {
            CentralSyncManager.start(context)
            delay(100)
            CentralSyncManager.stop()
        }
        CentralSyncManager.start(context)
        try {
            withTimeout(40_000) { CentralSyncManager.connection.first { it == CentralSyncManager.Connection.LIVE } }
            assertEquals(CentralSyncManager.Connection.LIVE, CentralSyncManager.connection.value)
        } finally { CentralSyncManager.stop() }
    }
}
