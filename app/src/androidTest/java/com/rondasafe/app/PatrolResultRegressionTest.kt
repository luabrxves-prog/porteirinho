package com.rondasafe.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.ui.portaria.PatrolFinishedScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolResultRegressionTest {
    @get:Rule val ui = createComposeRule()
    @Test fun offlineFinishIsNotShownAsConfirmedCompletion() {
        ui.setContent { MaterialTheme { PatrolFinishedScreen(FinishPatrolDto("COMPLETED", 30, 30, synced = false)) {} } }
        ui.onNodeWithText("Ronda salva no aparelho").assertExists()
        ui.onNodeWithText("Ronda concluída").assertDoesNotExist()
    }
    @Test fun acknowledgedCompletionIsShownAsCompleted() {
        ui.setContent { MaterialTheme { PatrolFinishedScreen(FinishPatrolDto("COMPLETED", 30, 30, synced = true)) {} } }
        ui.onNodeWithText("Ronda concluída").assertExists()
    }
    @Test fun incompletePatrolIsNotShownAsCompleted() {
        ui.setContent { MaterialTheme { PatrolFinishedScreen(FinishPatrolDto("INCOMPLETE", 2, 30, synced = true)) {} } }
        ui.onNodeWithText("Ronda incompleta").assertExists()
        ui.onNodeWithText("Ronda concluída").assertDoesNotExist()
    }
}
