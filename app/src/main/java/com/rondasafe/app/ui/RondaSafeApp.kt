package com.rondasafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.*
import com.rondasafe.app.ui.components.EntryBuildingBackground
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeMark
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
    REPORTS,
    PATROL_ASSIGNMENTS,
    OFFLINE_SYNC,
    ARCHIVED,
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
    var screen by remember {
        mutableStateOf(
            if (runCatching { AuthRepository.hasSession() }.getOrDefault(false)) {
                AppScreen.ADMIN_DASHBOARD
            } else {
                AppScreen.ENTRY
            },
        )
    }
    var selection by remember { mutableStateOf(AdminSelection()) }
    var selectedPatrolTemplate by remember { mutableStateOf<PatrolTemplateDto?>(null) }
    var selectedGuard by remember { mutableStateOf<PortariaGuardDto?>(null) }
    var shift by remember { mutableStateOf<ShiftDto?>(null) }
    var activePatrol by remember { mutableStateOf<AvailablePatrolDto?>(null) }
    var run by remember { mutableStateOf<PatrolRunDto?>(null) }
    var finishResult by remember { mutableStateOf<FinishPatrolDto?>(null) }

    fun returnToGuardSelection() {
        PortariaRepository.clearGuardSession()
        selectedGuard = null
        shift = null
        activePatrol = null
        run = null
        finishResult = null
        screen = AppScreen.GUARD_SELECTION
    }

    BackHandler(enabled = screen != AppScreen.ENTRY) {
        when (screen) {
            AppScreen.ADMIN_LOGIN,
            AppScreen.ADMIN_DASHBOARD,
            AppScreen.GUARD_SELECTION,
            -> screen = AppScreen.ENTRY

            AppScreen.ALERTS,
            AppScreen.PATROL_HISTORY,
            AppScreen.REPORTS,
            AppScreen.PATROL_ASSIGNMENTS,
            AppScreen.OFFLINE_SYNC,
            AppScreen.ARCHIVED,
            AppScreen.LOCATIONS,
            AppScreen.GUARDS,
            AppScreen.PATROLS,
            AppScreen.DEVICE_PROVISION,
            -> screen = AppScreen.ADMIN_DASHBOARD

            AppScreen.CHECKPOINTS -> {
                selection = selection.copy(floor = null, checkpoint = null)
                screen = AppScreen.LOCATIONS
            }

            AppScreen.CHECKPOINT_DETAIL -> {
                selection = selection.copy(checkpoint = null)
                screen = AppScreen.CHECKPOINTS
            }

            AppScreen.PATROL_CREATE,
            AppScreen.PATROL_EDIT,
            -> {
                selectedPatrolTemplate = null
                screen = AppScreen.PATROLS
            }

            AppScreen.GUARD_PIN -> {
                selectedGuard = null
                screen = AppScreen.GUARD_SELECTION
            }

            AppScreen.GUARD_CHANGE_PIN,
            AppScreen.SHIFT_HOME,
            -> returnToGuardSelection()

            // Um turno ou uma ronda em andamento deve ser encerrado pela ação
            // correspondente da tela, evitando perda acidental de estado.
            AppScreen.AVAILABLE_PATROLS,
            AppScreen.PATROL_SCANNER,
            -> Unit

            AppScreen.PATROL_FINISHED -> returnToGuardSelection()
            AppScreen.ENTRY -> Unit
        }
    }

    when (screen) {
        AppScreen.ENTRY -> EntryScreen(
            portariaEnabled = PortariaRepository.deviceCredential != null,
            onPortaria = { screen = AppScreen.GUARD_SELECTION },
            onAdmin = {
                screen = if (runCatching { AuthRepository.hasSession() }.getOrDefault(false)) {
                    AppScreen.ADMIN_DASHBOARD
                } else {
                    AppScreen.ADMIN_LOGIN
                }
            },
        )

        AppScreen.ADMIN_LOGIN -> PremiumAdminLoginScreen(
            onLoginSuccess = { screen = AppScreen.ADMIN_DASHBOARD },
            onBack = { screen = AppScreen.ENTRY },
        )

        AppScreen.ADMIN_DASHBOARD -> AdminDashboardScreenV3(
            onOpenLocations = { screen = AppScreen.LOCATIONS },
            onOpenGuards = { screen = AppScreen.GUARDS },
            onOpenPatrols = { screen = AppScreen.PATROLS },
            onOpenAssignments = { screen = AppScreen.PATROL_ASSIGNMENTS },
            onOpenHistory = { screen = AppScreen.PATROL_HISTORY },
            onOpenReports = { screen = AppScreen.REPORTS },
            onOpenAlerts = { screen = AppScreen.ALERTS },
            onOpenSync = { screen = AppScreen.OFFLINE_SYNC },
            onOpenArchived = { screen = AppScreen.ARCHIVED },
            onOpenDeviceProvision = { screen = AppScreen.DEVICE_PROVISION },
            onLogout = {
                screen = AppScreen.ENTRY
                selection = AdminSelection()
            },
        )

        AppScreen.ALERTS -> AlertsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_HISTORY -> PatrolHistoryScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.REPORTS -> ReportScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_ASSIGNMENTS -> PatrolAssignmentsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.OFFLINE_SYNC -> OfflineSyncAdminScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.ARCHIVED -> ArchivedScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })

        AppScreen.LOCATIONS -> SimplifiedLocationsScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onOpenFloor = { building, block, floor ->
                selection = AdminSelection(building = building, block = block, floor = floor)
                screen = AppScreen.CHECKPOINTS
            },
        )
        AppScreen.CHECKPOINTS -> SimplifiedCheckpointsScreen(
            floor = requireNotNull(selection.floor),
            onBack = { screen = AppScreen.LOCATIONS },
            onSelect = {
                selection = selection.copy(checkpoint = it)
                screen = AppScreen.CHECKPOINT_DETAIL
            },
        )
        AppScreen.CHECKPOINT_DETAIL -> CheckpointDetailWithPrintScreen(
            checkpoint = requireNotNull(selection.checkpoint),
            onBack = { screen = AppScreen.CHECKPOINTS },
        )
        AppScreen.GUARDS -> GuardsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROLS -> PatrolTemplatesScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onCreate = {
                selectedPatrolTemplate = null
                screen = AppScreen.PATROL_CREATE
            },
            onEdit = {
                selectedPatrolTemplate = it
                screen = AppScreen.PATROL_EDIT
            },
        )
        AppScreen.PATROL_CREATE -> CreatePatrolTemplateScreen(
            onBack = { screen = AppScreen.PATROLS },
            onCreated = {
                selectedPatrolTemplate = null
                screen = AppScreen.PATROLS
            },
        )
        AppScreen.PATROL_EDIT -> CreatePatrolTemplateScreen(
            template = requireNotNull(selectedPatrolTemplate),
            onBack = {
                selectedPatrolTemplate = null
                screen = AppScreen.PATROLS
            },
            onCreated = {
                selectedPatrolTemplate = null
                screen = AppScreen.PATROLS
            },
        )
        AppScreen.DEVICE_PROVISION -> DeviceProvisionScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onProvisioned = { screen = AppScreen.ENTRY },
        )

        AppScreen.GUARD_SELECTION -> GuardSelectionScreen(
            onGuardSelected = {
                selectedGuard = it
                screen = AppScreen.GUARD_PIN
            },
            onBack = { screen = AppScreen.ENTRY },
        )
        AppScreen.GUARD_PIN -> GuardPinScreen(
            guard = requireNotNull(selectedGuard),
            onSuccess = { mustChange ->
                screen = if (mustChange) AppScreen.GUARD_CHANGE_PIN else AppScreen.SHIFT_HOME
            },
            onBack = {
                selectedGuard = null
                screen = AppScreen.GUARD_SELECTION
            },
        )
        AppScreen.GUARD_CHANGE_PIN -> ChangeGuardPinScreen(onChanged = { screen = AppScreen.SHIFT_HOME })
        AppScreen.SHIFT_HOME -> ShiftHomeScreen(
            onShiftStarted = {
                shift = it
                screen = AppScreen.AVAILABLE_PATROLS
            },
            onBack = { returnToGuardSelection() },
        )
        AppScreen.AVAILABLE_PATROLS -> AvailablePatrolsScreen(
            shift = requireNotNull(shift),
            onStart = { patrol, patrolRun ->
                activePatrol = patrol
                run = patrolRun
                screen = AppScreen.PATROL_SCANNER
            },
            onEndShift = {
                shift = null
                selectedGuard = null
                screen = AppScreen.GUARD_SELECTION
            },
        )
        AppScreen.PATROL_SCANNER -> PatrolScannerScreen(
            run = requireNotNull(run),
            patrolName = requireNotNull(activePatrol).patrolName,
            onFinished = {
                finishResult = it
                screen = AppScreen.PATROL_FINISHED
            },
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
private fun EntryScreen(
    portariaEnabled: Boolean,
    onPortaria: () -> Unit,
    onAdmin: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RondaSafeColors.NavyDark),
    ) {
        EntryBuildingBackground(
            Modifier
                .fillMaxSize()
                .offset(y = (-48).dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xE305263B),
                            .38f to Color(0xA305263B),
                            .66f to Color(0xB9031E32),
                            1f to RondaSafeColors.NavyDark,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RondaSafeMark(modifier = Modifier.size(58.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "RondaSafe",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "Segurança que acompanha\ncada passo da sua ronda.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = Color.White,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Acessar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = RondaSafeColors.Navy,
                    )
                    Text(
                        text = "Escolha seu perfil para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RondaSafeColors.Muted,
                    )
                    Spacer(Modifier.height(2.dp))

                    EntryActionCard(
                        title = "Portaria",
                        subtitle = if (portariaEnabled) {
                            "Iniciar turno e realizar rondas"
                        } else {
                            "Configure este aparelho no painel"
                        },
                        icon = Icons.Rounded.Badge,
                        enabled = portariaEnabled,
                        onClick = onPortaria,
                    )
                    EntryActionCard(
                        title = "Administrador",
                        subtitle = "Gerenciar o condomínio",
                        icon = Icons.Rounded.AdminPanelSettings,
                        enabled = true,
                        onClick = onAdmin,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            disabledContainerColor = Color(0xFFF3F5F7),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) Color(0xFFDCE4EA) else Color(0xFFE6EAEE),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (enabled) RondaSafeColors.Navy else Color(0xFFD7DEE4),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) RondaSafeColors.Navy else RondaSafeColors.Muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Muted,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (enabled) RondaSafeColors.Navy else RondaSafeColors.Muted,
            )
        }
    }
}
