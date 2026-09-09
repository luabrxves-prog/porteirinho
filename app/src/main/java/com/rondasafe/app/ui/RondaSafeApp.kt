package com.rondasafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.data.sync.SharedSyncBus
import com.rondasafe.app.ui.admin.*
import com.rondasafe.app.ui.portaria.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AdminSelection(
    val building: BuildingDto? = null,
    val block: BlockDto? = null,
    val floor: FloorDto? = null,
    val checkpoint: CheckpointDto? = null,
)

enum class AppScreen {
    ENTRY, ADMIN_LOGIN, ADMIN_DASHBOARD, ADMIN_SETTINGS, ALERTS, PATROL_HISTORY,
    REPORTS, PATROL_ASSIGNMENTS, OFFLINE_SYNC, ARCHIVED, LOCATIONS, CHECKPOINTS,
    CHECKPOINT_DETAIL, GUARDS, PATROLS, PATROL_CREATE, PATROL_EDIT, DEVICE_PROVISION,
    GUARD_SELECTION, GUARD_PIN, GUARD_CHANGE_PIN, SHIFT_HOME, AVAILABLE_PATROLS,
    PATROL_SCANNER, PATROL_FINISHED,
}

@Composable
fun RondaSafeApp() {
    val appContext = LocalContext.current.applicationContext
    val syncEpoch by SharedSyncBus.epoch.collectAsState()
    var deviceCredentialLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { PortariaRepository.restoreDeviceCredential(appContext) }
        deviceCredentialLoaded = true
        PortariaRepository.scheduleOfflineSync()
    }
    var screen by remember { mutableStateOf(AppScreen.ENTRY) }
    var selection by remember { mutableStateOf(AdminSelection()) }
    var selectedPatrolTemplate by remember { mutableStateOf<PatrolTemplateDto?>(null) }
    var selectedGuard by remember { mutableStateOf<PortariaGuardDto?>(null) }
    var shift by remember { mutableStateOf<ShiftDto?>(null) }
    var activePatrol by remember { mutableStateOf<AvailablePatrolDto?>(null) }
    var run by remember { mutableStateOf<PatrolRunDto?>(null) }
    var finishResult by remember { mutableStateOf<FinishPatrolDto?>(null) }

    fun returnToGuardLanding() {
        PortariaRepository.clearGuardSession()
        selectedGuard = null
        shift = null
        activePatrol = null
        run = null
        finishResult = null
        screen = AppScreen.ENTRY
    }
    fun openAdmin() {
        screen = if (AuthRepository.hasSession()) AppScreen.ADMIN_DASHBOARD else AppScreen.ADMIN_LOGIN
    }
    BackHandler(enabled = screen != AppScreen.ENTRY) {
        when (screen) {
            AppScreen.ADMIN_LOGIN, AppScreen.ADMIN_DASHBOARD, AppScreen.GUARD_SELECTION -> screen = AppScreen.ENTRY
            AppScreen.ADMIN_SETTINGS, AppScreen.ALERTS, AppScreen.PATROL_HISTORY, AppScreen.REPORTS,
            AppScreen.LOCATIONS, AppScreen.GUARDS, AppScreen.PATROLS -> screen = AppScreen.ADMIN_DASHBOARD
            AppScreen.PATROL_ASSIGNMENTS, AppScreen.OFFLINE_SYNC, AppScreen.ARCHIVED, AppScreen.DEVICE_PROVISION -> screen = AppScreen.ADMIN_SETTINGS
            AppScreen.CHECKPOINTS -> { selection = selection.copy(floor = null, checkpoint = null); screen = AppScreen.LOCATIONS }
            AppScreen.CHECKPOINT_DETAIL -> { selection = selection.copy(checkpoint = null); screen = AppScreen.CHECKPOINTS }
            AppScreen.PATROL_CREATE, AppScreen.PATROL_EDIT -> { selectedPatrolTemplate = null; screen = AppScreen.PATROLS }
            AppScreen.GUARD_PIN -> { selectedGuard = null; screen = AppScreen.ENTRY }
            AppScreen.GUARD_CHANGE_PIN, AppScreen.SHIFT_HOME, AppScreen.PATROL_FINISHED -> returnToGuardLanding()
            AppScreen.AVAILABLE_PATROLS, AppScreen.PATROL_SCANNER, AppScreen.ENTRY -> Unit
        }
    }

    // Command screens own their state and refresh streams. An incoming server update
    // must not cancel the command which caused it or erase a confirmation dialog.
    val refreshEpoch = when (screen) {
        AppScreen.ENTRY, AppScreen.ADMIN_DASHBOARD, AppScreen.ADMIN_SETTINGS,
        AppScreen.ALERTS, AppScreen.PATROL_HISTORY, AppScreen.REPORTS,
        AppScreen.LOCATIONS, AppScreen.CHECKPOINTS, AppScreen.CHECKPOINT_DETAIL,
        AppScreen.GUARDS, AppScreen.PATROLS, AppScreen.GUARD_SELECTION -> syncEpoch
        else -> 0L
    }
    key(refreshEpoch) {
        when (screen) {
            AppScreen.ENTRY -> Column(Modifier.fillMaxSize()) {
                OfflineSyncStatusBanner()
                Box(Modifier.weight(1f)) {
                    PremiumGuardLandingScreen(
                        enabled = deviceCredentialLoaded && PortariaRepository.deviceCredential != null,
                        onGuardSelected = { selectedGuard = it; screen = AppScreen.GUARD_PIN }, onAdmin = ::openAdmin,
                    )
                }
            }
            AppScreen.ADMIN_LOGIN -> PremiumAdminLoginScreen(
                onLoginSuccess = { screen = AppScreen.ADMIN_DASHBOARD }, onBack = { screen = AppScreen.ENTRY },
            )
            AppScreen.ADMIN_DASHBOARD -> AdminDashboardScreenV3(
                onOpenLocations = { screen = AppScreen.LOCATIONS }, onOpenGuards = { screen = AppScreen.GUARDS },
                onOpenPatrols = { screen = AppScreen.PATROLS }, onOpenHistory = { screen = AppScreen.PATROL_HISTORY },
                onOpenReports = { screen = AppScreen.REPORTS }, onOpenAlerts = { screen = AppScreen.ALERTS },
                onOpenSettings = { screen = AppScreen.ADMIN_SETTINGS },
                onLogout = { screen = AppScreen.ENTRY; selection = AdminSelection() },
            )
            AppScreen.ADMIN_SETTINGS -> AdminSettingsScreen(
                onBack = { screen = AppScreen.ADMIN_DASHBOARD }, onOpenAssignments = { screen = AppScreen.PATROL_ASSIGNMENTS },
                onOpenDevice = { screen = AppScreen.DEVICE_PROVISION }, onOpenArchived = { screen = AppScreen.ARCHIVED },
                onOpenSync = { screen = AppScreen.OFFLINE_SYNC },
            )
            AppScreen.ALERTS -> AlertsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD }, onOpenHistory = { screen = AppScreen.PATROL_HISTORY })
            AppScreen.PATROL_HISTORY -> PatrolHistoryScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
            AppScreen.REPORTS -> ReportScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
            AppScreen.PATROL_ASSIGNMENTS -> PatrolAssignmentsScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })
            AppScreen.OFFLINE_SYNC -> OfflineSyncAdminScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })
            AppScreen.ARCHIVED -> ArchivedScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })
            AppScreen.LOCATIONS -> SimplifiedLocationsScreen(
                onBack = { screen = AppScreen.ADMIN_DASHBOARD },
                onOpenFloor = { building, block, floor -> selection = AdminSelection(building, block, floor); screen = AppScreen.CHECKPOINTS },
            )
            AppScreen.CHECKPOINTS -> {
                val floor = selection.floor
                if (floor == null) { LaunchedEffect(Unit) { screen = AppScreen.LOCATIONS } }
                else SimplifiedCheckpointsScreen(floor = floor, onBack = { screen = AppScreen.LOCATIONS }, onSelect = { selection = selection.copy(checkpoint = it); screen = AppScreen.CHECKPOINT_DETAIL })
            }
            AppScreen.CHECKPOINT_DETAIL -> {
                val checkpoint = selection.checkpoint
                if (checkpoint == null) { LaunchedEffect(Unit) { screen = if (selection.floor != null) AppScreen.CHECKPOINTS else AppScreen.LOCATIONS } }
                else CheckpointDetailWithPrintScreen(checkpoint, onBack = { screen = AppScreen.CHECKPOINTS })
            }
            AppScreen.GUARDS -> GuardsScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
            AppScreen.PATROLS -> PatrolTemplatesScreen(
                onBack = { screen = AppScreen.ADMIN_DASHBOARD }, onCreate = { screen = AppScreen.PATROLS },
                onEdit = { selectedPatrolTemplate = it; screen = AppScreen.PATROL_EDIT },
            )
            AppScreen.PATROL_CREATE -> { LaunchedEffect(Unit) { screen = AppScreen.PATROLS } }
            AppScreen.PATROL_EDIT -> {
                val template = selectedPatrolTemplate
                if (template == null) { LaunchedEffect(Unit) { screen = AppScreen.PATROLS } }
                else CreatePatrolTemplateScreen(template = template,
                    onBack = { selectedPatrolTemplate = null; screen = AppScreen.PATROLS },
                    onCreated = { selectedPatrolTemplate = null; screen = AppScreen.PATROLS })
            }
            AppScreen.DEVICE_PROVISION -> DeviceProvisionScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS }, onProvisioned = { screen = AppScreen.ENTRY })
            AppScreen.GUARD_SELECTION -> GuardSelectionScreen(onGuardSelected = { selectedGuard = it; screen = AppScreen.GUARD_PIN }, onBack = { screen = AppScreen.ENTRY })
            AppScreen.GUARD_PIN -> {
                val guard = selectedGuard
                if (guard == null) { LaunchedEffect(Unit) { screen = AppScreen.ENTRY } }
                else GuardPinScreen(guard = guard,
                    onSuccess = { mustChange -> screen = if (mustChange) AppScreen.GUARD_CHANGE_PIN else AppScreen.SHIFT_HOME },
                    onBack = { selectedGuard = null; screen = AppScreen.ENTRY })
            }
            AppScreen.GUARD_CHANGE_PIN -> ChangeGuardPinScreen(onChanged = { screen = AppScreen.SHIFT_HOME })
            AppScreen.SHIFT_HOME -> ShiftHomeScreen(onShiftStarted = { shift = it; screen = AppScreen.AVAILABLE_PATROLS }, onBack = ::returnToGuardLanding)
            AppScreen.AVAILABLE_PATROLS -> {
                val currentShift = shift
                if (currentShift == null) { LaunchedEffect(Unit) { returnToGuardLanding() } }
                else SafeAvailablePatrolsScreen(shift = currentShift,
                    onStart = { patrol, patrolRun -> activePatrol = patrol; run = patrolRun; screen = AppScreen.PATROL_SCANNER },
                    onEndShift = ::returnToGuardLanding)
            }
            AppScreen.PATROL_SCANNER -> {
                val currentRun = run
                val patrol = activePatrol
                if (currentRun == null || patrol == null) { LaunchedEffect(Unit) { returnToGuardLanding() } }
                else SafePatrolScannerScreen(run = currentRun, patrolName = patrol.patrolName, onFinished = { finishResult = it; screen = AppScreen.PATROL_FINISHED })
            }
            AppScreen.PATROL_FINISHED -> {
                val result = finishResult
                if (result == null) { LaunchedEffect(Unit) { returnToGuardLanding() } }
                else SafePatrolFinishedScreen(result = result, onDone = ::returnToGuardLanding, runClientEventId = run?.runId)
            }
        }
    }
}
