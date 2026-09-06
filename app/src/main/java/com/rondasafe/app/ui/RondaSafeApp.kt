package com.rondasafe.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.*
import com.rondasafe.app.ui.portaria.*

data class AdminSelection(
    val building: BuildingDto? = null,
    val block: BlockDto? = null,
    val floor: FloorDto? = null,
    val checkpoint: CheckpointDto? = null,
)

enum class AppScreen {
    ENTRY,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD,
    ALERTS,
    PATROL_HISTORY,
    PATROL_ASSIGNMENTS,
    OFFLINE_SYNC,
    BUILDINGS,
    BLOCKS,
    FLOORS,
    CHECKPOINTS,
    CHECKPOINT_DETAIL,
    GUARDS,
    PATROLS,
    PATROL_CREATE,
    PATROL_EDIT,
    DEVICE_PROVISION,
    GUARD_SELECTION,
    GUARD_PIN,
    GUARD_CHANGE_PIN,
    SHIFT_HOME,
    AVAILABLE_PATROLS,
    PATROL_SCANNER,
    PATROL_FINISHED,
}

@Composable
fun RondaSafeApp() {
    var screen by remember { mutableStateOf(if (AuthRepository.hasSession()) AppScreen.ADMIN_DASHBOARD else AppScreen.ENTRY) }
    var selection by remember { mutableStateOf(AdminSelection()) }
    var selectedPatrolTemplate by remember { mutableStateOf<PatrolTemplateDto?>(null) }
    var selectedGuard by remember { mutableStateOf<PortariaGuardDto?>(null) }
    var shift by remember { mutableStateOf<ShiftDto?>(null) }
    var activePatrol by remember { mutableStateOf<AvailablePatrolDto?>(null) }
    var run by remember { mutableStateOf<PatrolRunDto?>(null) }
    var finishResult by remember { mutableStateOf<FinishPatrolDto?>(null) }

    when (screen) {
        AppScreen.ENTRY -> EntryScreen(
            portariaEnabled = PortariaRepository.deviceCredential != null,
            onPortaria = { screen = AppScreen.GUARD_SELECTION },
            onAdmin = { screen = if (AuthRepository.hasSession()) AppScreen.ADMIN_DASHBOARD else AppScreen.ADMIN_LOGIN },
        )

        AppScreen.ADMIN_LOGIN -> AdminLoginScreen(onLoginSuccess = { screen = AppScreen.ADMIN_DASHBOARD })

        AppScreen.ADMIN_DASHBOARD -> AdminDashboardScreenV3(
            onOpenLocations = { screen = AppScreen.BUILDINGS },
            onOpenGuards = { screen = AppScreen.GUARDS },
            onOpenPatrols = { screen = AppScreen.PATROLS },
            onOpenAssignments = { screen = AppScreen.PATROL_ASSIGNMENTS },
            onOpenHistory = { screen = AppScreen.PATROL_HISTORY },
            onOpenAlerts = { screen = AppScreen.ALERTS },
            onOpenSync = { screen = AppScreen.OFFLINE_SYNC },
            onOpenDeviceProvision = { screen = AppScreen.DEVICE_PROVISION },
            onLogout = { screen = AppScreen.ENTRY; selection = AdminSelection() },
        )

        AppScreen.ALERTS -> AlertsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_HISTORY -> PatrolHistoryScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_ASSIGNMENTS -> PatrolAssignmentsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.OFFLINE_SYNC -> OfflineSyncAdminScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })

        AppScreen.BUILDINGS -> BuildingsScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onSelect = { selection = AdminSelection(building = it); screen = AppScreen.BLOCKS },
        )
        AppScreen.BLOCKS -> BlocksScreen(
            building = requireNotNull(selection.building),
            onBack = { screen = AppScreen.BUILDINGS },
            onSelect = { selection = selection.copy(block = it, floor = null, checkpoint = null); screen = AppScreen.FLOORS },
        )
        AppScreen.FLOORS -> FloorsScreen(
            block = requireNotNull(selection.block),
            onBack = { screen = AppScreen.BLOCKS },
            onSelect = { selection = selection.copy(floor = it, checkpoint = null); screen = AppScreen.CHECKPOINTS },
        )
        AppScreen.CHECKPOINTS -> CheckpointsScreen(
            floor = requireNotNull(selection.floor),
            onBack = { screen = AppScreen.FLOORS },
            onSelect = { selection = selection.copy(checkpoint = it); screen = AppScreen.CHECKPOINT_DETAIL },
        )
        AppScreen.CHECKPOINT_DETAIL -> CheckpointDetailWithPrintScreen(
            checkpoint = requireNotNull(selection.checkpoint),
            onBack = { screen = AppScreen.CHECKPOINTS },
        )
        AppScreen.GUARDS -> GuardsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROLS -> PatrolTemplatesScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onCreate = { selectedPatrolTemplate = null; screen = AppScreen.PATROL_CREATE },
            onEdit = { selectedPatrolTemplate = it; screen = AppScreen.PATROL_EDIT },
        )
        AppScreen.PATROL_CREATE -> CreatePatrolTemplateScreen(
            onBack = { screen = AppScreen.PATROLS },
            onCreated = { selectedPatrolTemplate = null; screen = AppScreen.PATROLS },
        )
        AppScreen.PATROL_EDIT -> CreatePatrolTemplateScreen(
            template = requireNotNull(selectedPatrolTemplate),
            onBack = { selectedPatrolTemplate = null; screen = AppScreen.PATROLS },
            onCreated = { selectedPatrolTemplate = null; screen = AppScreen.PATROLS },
        )
        AppScreen.DEVICE_PROVISION -> DeviceProvisionScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onProvisioned = { screen = AppScreen.ENTRY },
        )

        AppScreen.GUARD_SELECTION -> GuardSelectionScreen(
            onGuardSelected = { selectedGuard = it; screen = AppScreen.GUARD_PIN },
            onBack = { screen = AppScreen.ENTRY },
        )
        AppScreen.GUARD_PIN -> GuardPinScreen(
            guard = requireNotNull(selectedGuard),
            onSuccess = { mustChange -> screen = if (mustChange) AppScreen.GUARD_CHANGE_PIN else AppScreen.SHIFT_HOME },
            onBack = { screen = AppScreen.GUARD_SELECTION },
        )
        AppScreen.GUARD_CHANGE_PIN -> ChangeGuardPinScreen(onChanged = { screen = AppScreen.SHIFT_HOME })
        AppScreen.SHIFT_HOME -> ShiftHomeScreen(
            onShiftStarted = { shift = it; screen = AppScreen.AVAILABLE_PATROLS },
            onBack = { screen = AppScreen.GUARD_SELECTION },
        )
        AppScreen.AVAILABLE_PATROLS -> AvailablePatrolsScreen(
            shift = requireNotNull(shift),
            onStart = { patrol, patrolRun -> activePatrol = patrol; run = patrolRun; screen = AppScreen.PATROL_SCANNER },
            onEndShift = { shift = null; selectedGuard = null; screen = AppScreen.GUARD_SELECTION },
        )
        AppScreen.PATROL_SCANNER -> PatrolScannerScreen(
            run = requireNotNull(run),
            patrolName = requireNotNull(activePatrol).patrolName,
            onFinished = { finishResult = it; screen = AppScreen.PATROL_FINISHED },
        )
        AppScreen.PATROL_FINISHED -> PatrolFinishedScreen(
            result = requireNotNull(finishResult),
            onDone = {
                run = null
                activePatrol = null
                finishResult = null
                shift = null
                selectedGuard = null
                screen = AppScreen.GUARD_SELECTION
            },
        )
    }
}

@Composable
private fun EntryScreen(portariaEnabled: Boolean, onPortaria: () -> Unit, onAdmin: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RondaSafe", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onPortaria, enabled = portariaEnabled, modifier = Modifier.fillMaxWidth()) { Text("Portaria") }
            if (!portariaEnabled) {
                Spacer(Modifier.height(8.dp))
                Text("Este aparelho ainda não foi configurado como portaria.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth()) { Text("Administrador") }
        }
    }
}
