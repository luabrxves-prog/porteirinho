package com.rondasafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.*
import com.rondasafe.app.ui.components.RondaSafeBrand
import com.rondasafe.app.ui.components.RondaSafeColors
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
    LOCATIONS,
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
    var screen by remember { mutableStateOf(if (runCatching { AuthRepository.hasSession() }.getOrDefault(false)) AppScreen.ADMIN_DASHBOARD else AppScreen.ENTRY) }
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
            onAdmin = { screen = if (runCatching { AuthRepository.hasSession() }.getOrDefault(false)) AppScreen.ADMIN_DASHBOARD else AppScreen.ADMIN_LOGIN },
        )

        AppScreen.ADMIN_LOGIN -> AdminLoginScreen(onLoginSuccess = { screen = AppScreen.ADMIN_DASHBOARD })

        AppScreen.ADMIN_DASHBOARD -> AdminDashboardScreenV3(
            onOpenLocations = { screen = AppScreen.LOCATIONS },
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

        AppScreen.LOCATIONS -> SimplifiedLocationsScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onOpenFloor = { building, block, floor ->
                selection = AdminSelection(building = building, block = block, floor = floor)
                screen = AppScreen.CHECKPOINTS
            },
        )
        AppScreen.CHECKPOINTS -> CheckpointsScreen(
            floor = requireNotNull(selection.floor),
            onBack = { screen = AppScreen.LOCATIONS },
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
    Box(
        Modifier
            .fillMaxSize()
            .background(RondaSafeColors.Navy),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            RondaSafeBrand(
                modifier = Modifier.width(190.dp),
                darkBackground = true,
                showTagline = true,
            )
            Spacer(Modifier.weight(0.65f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPortaria,
                    enabled = portariaEnabled,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("Entrar como Portaria", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onAdmin,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = RondaSafeColors.Navy),
                ) {
                    Text("Entrar como Administrador", fontWeight = FontWeight.Bold)
                }
                if (!portariaEnabled) {
                    Text(
                        "Este celular ainda não está configurado como portaria.",
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Condomínios mais seguros com tecnologia simples.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}
