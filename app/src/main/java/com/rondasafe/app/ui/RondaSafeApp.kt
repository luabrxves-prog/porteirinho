package com.rondasafe.app.ui

import androidx.compose.runtime.*
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.ui.admin.*

data class AdminSelection(
    val building: BuildingDto? = null,
    val block: BlockDto? = null,
    val floor: FloorDto? = null,
    val checkpoint: CheckpointDto? = null,
)

enum class AdminScreen {
    LOGIN,
    DASHBOARD,
    BUILDINGS,
    BLOCKS,
    FLOORS,
    CHECKPOINTS,
    CHECKPOINT_DETAIL,
}

@Composable
fun RondaSafeApp() {
    var screen by remember {
        mutableStateOf(if (AuthRepository.hasSession()) AdminScreen.DASHBOARD else AdminScreen.LOGIN)
    }
    var selection by remember { mutableStateOf(AdminSelection()) }

    when (screen) {
        AdminScreen.LOGIN -> AdminLoginScreen(
            onLoginSuccess = { screen = AdminScreen.DASHBOARD },
        )

        AdminScreen.DASHBOARD -> AdminDashboardScreen(
            onOpenLocations = { screen = AdminScreen.BUILDINGS },
            onLogout = {
                screen = AdminScreen.LOGIN
                selection = AdminSelection()
            },
        )

        AdminScreen.BUILDINGS -> BuildingsScreen(
            onBack = { screen = AdminScreen.DASHBOARD },
            onSelect = {
                selection = AdminSelection(building = it)
                screen = AdminScreen.BLOCKS
            },
        )

        AdminScreen.BLOCKS -> BlocksScreen(
            building = requireNotNull(selection.building),
            onBack = { screen = AdminScreen.BUILDINGS },
            onSelect = {
                selection = selection.copy(block = it, floor = null, checkpoint = null)
                screen = AdminScreen.FLOORS
            },
        )

        AdminScreen.FLOORS -> FloorsScreen(
            block = requireNotNull(selection.block),
            onBack = { screen = AdminScreen.BLOCKS },
            onSelect = {
                selection = selection.copy(floor = it, checkpoint = null)
                screen = AdminScreen.CHECKPOINTS
            },
        )

        AdminScreen.CHECKPOINTS -> CheckpointsScreen(
            floor = requireNotNull(selection.floor),
            onBack = { screen = AdminScreen.FLOORS },
            onSelect = {
                selection = selection.copy(checkpoint = it)
                screen = AdminScreen.CHECKPOINT_DETAIL
            },
        )

        AdminScreen.CHECKPOINT_DETAIL -> CheckpointDetailScreen(
            checkpoint = requireNotNull(selection.checkpoint),
            onBack = { screen = AdminScreen.CHECKPOINTS },
        )
    }
}
