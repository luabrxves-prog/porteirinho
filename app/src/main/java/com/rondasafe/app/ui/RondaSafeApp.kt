package com.rondasafe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.*
import com.rondasafe.app.ui.components.RondaSafeBrand
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.SkylineIllustration
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
            onOpenAlerts = { screen = AppScreen.ALERTS },
            onOpenSync = { screen = AppScreen.OFFLINE_SYNC },
            onOpenArchived = { screen = AppScreen.ARCHIVED },
            onOpenDeviceProvision = { screen = AppScreen.DEVICE_PROVISION },
            onLogout = { screen = AppScreen.ENTRY; selection = AdminSelection() },
        )

        AppScreen.ALERTS -> AlertsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_HISTORY -> PatrolHistoryScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
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
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A456F), RondaSafeColors.Navy, RondaSafeColors.NavyDark),
                ),
            ),
    ) {
        SkylineIllustration(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = 55.dp, x = 46.dp)
                .fillMaxWidth(.90f)
                .height(315.dp),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x16031E32), Color(0xD9031E32)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(28.dp))
                RondaSafeBrand(
                    modifier = Modifier.width(190.dp),
                    darkBackground = true,
                    showTagline = false,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    "Segurança que acompanha\ncada passo da sua ronda.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Porteiros, pontos de controle e histórico em um só lugar.",
                    modifier = Modifier.fillMaxWidth(.83f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = .76f),
                )
            }

            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White,
                shadowElevation = 14.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Acessar RondaSafe",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = RondaSafeColors.Navy,
                    )
                    Text(
                        "Escolha como você vai utilizar este aparelho.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RondaSafeColors.Muted,
                    )

                    EntryActionCard(
                        title = "Portaria",
                        subtitle = if (portariaEnabled) "Iniciar turno e realizar rondas" else "Configure este aparelho pelo painel administrativo",
                        icon = Icons.Rounded.Badge,
                        enabled = portariaEnabled,
                        onClick = onPortaria,
                    )
                    EntryActionCard(
                        title = "Administrador",
                        subtitle = "Gerenciar equipe, locais, rondas e histórico",
                        icon = Icons.Rounded.AdminPanelSettings,
                        enabled = true,
                        onClick = onAdmin,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CloudDone, null, tint = RondaSafeColors.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Dados protegidos e sincronizados na nuvem",
                            style = MaterialTheme.typography.bodySmall,
                            color = RondaSafeColors.Muted,
                            textAlign = TextAlign.Center,
                        )
                    }
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
            containerColor = RondaSafeColors.Background,
            disabledContainerColor = Color(0xFFF0F2F4),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) RondaSafeColors.Border else Color(0xFFE4E8EC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = if (enabled) RondaSafeColors.Navy else Color(0xFFD6DCE2),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(23.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) RondaSafeColors.Navy else RondaSafeColors.Muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Muted,
                    maxLines = 2,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = if (enabled) RondaSafeColors.Navy else RondaSafeColors.Muted)
        }
    }
}
