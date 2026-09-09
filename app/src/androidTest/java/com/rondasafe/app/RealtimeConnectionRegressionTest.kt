package com.rondasafe.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rondasafe.app.data.sync.CentralSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real read-only Supabase channel. No operational records are created or modified. */
@RunWith(AndroidJUnit4::class)
class RealtimeConnectionRegressionTest {
    @Test fun websocketSubscribesAfterRapidForegroundTransitions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val states = CopyOnWriteArrayList<String>()
        val trace = launch(start = CoroutineStart.UNDISPATCHED) {
            CentralSyncManager.connection.collect {
                states += it.name
                Log.i("RondaSafeRealtimeTest", "connection=${it.name}")
            }
        }
        try {
            // A foreground-owned connection needs a foreground Activity. Without it,
            // a delayed ProcessLifecycle ON_STOP from a preceding Compose test may
            // correctly stop the manager while this test is waiting for LIVE.
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                withTimeout(5_000) {
                    while (!withContext(Dispatchers.Main) {
                        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    }) delay(25)
                }
                repeat(4) {
                    CentralSyncManager.start(context)
                    delay(100)
                    CentralSyncManager.stop()
                }
                CentralSyncManager.start(context)
                withTimeout(40_000) {
                    CentralSyncManager.connection.first { it == CentralSyncManager.Connection.LIVE }
                }
                assertEquals(CentralSyncManager.Connection.LIVE, CentralSyncManager.connection.value)

                // Also exercise the actual Application lifecycle, not just direct calls.
                activity.moveToState(Lifecycle.State.CREATED)
                withTimeout(5_000) {
                    CentralSyncManager.connection.first { it == CentralSyncManager.Connection.STOPPED }
                }
                activity.moveToState(Lifecycle.State.RESUMED)
                withTimeout(40_000) {
                    CentralSyncManager.connection.first { it == CentralSyncManager.Connection.LIVE }
                }
                assertEquals(CentralSyncManager.Connection.LIVE, CentralSyncManager.connection.value)
            }
        } catch (e: Exception) {
            val processState = withContext(Dispatchers.Main) {
                ProcessLifecycleOwner.get().lifecycle.currentState.name
            }
            throw AssertionError("Realtime validation failed: process=$processState, transitions=${states.joinToString()}", e)
        } finally {
            CentralSyncManager.stop()
            trace.cancelAndJoin()
        }
    }
}
