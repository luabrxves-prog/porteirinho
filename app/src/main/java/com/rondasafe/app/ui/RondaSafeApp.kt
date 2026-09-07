package com.rondasafe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.BlockDto
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.FloorDto
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.data.model.ShiftDto
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.AdminDashboardScreenV3
import com.rondasafe.app.ui.admin.AdminSettingsScreen
import com.rondasafe.app.ui.admin.AlertsScreen
import com.rondasafe.app.ui.admin.ArchivedScreen
import com.rondasafe.app.ui.admin.CheckpointDetailWithPrintScreen
import com.rondasafe.app.ui.admin.CreatePatrolTemplateScreen
import com.rondasafe.app.ui.admin.DeviceProvisionScreen
import com.rondasafe.app.ui.admin.GuardsScreen
import com.rondasafe.app.ui.admin.OfflineSyncAdminScreen
import com.rondasafe.app.ui.admin.PatrolAssignmentsScreen
import com.rondasafe.app.ui.admin.PatrolHistoryScreen
import com.rondasafe.app.ui.admin.PatrolTemplatesScreen
import com.rondasafe.app.ui.admin.PremiumAdminLoginScreen
import com.rondasafe.app.ui.admin.ReportScreen
import com.rondasafe.app.ui.admin.SimplifiedCheckpointsScreen
import com.rondasafe.app.ui.admin.SimplifiedLocationsScreen
import com.rondasafe.app.ui.portaria.AvailablePatrolsScreen
import com.rondasafe.app.ui.portaria.ChangeGuardPinScreen
import com.rondasafe.app.ui.portaria.GuardPinScreen
import com.rondasafe.app.ui.portaria.GuardSelectionScreen
import com.rondasafe.app.ui.portaria.PatrolFinishedScreen
import com.rondasafe.app.ui.portaria.PatrolScannerScreen
import com.rondasafe.app.ui.portaria.PremiumGuardLandingScreen
import com.rondasafe.app.ui.portaria.ShiftHomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    ADMIN_SETTINGS,
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
    val appContext = LocalContext.current.applicationContext

    var deviceCredentialLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { PortariaRepository.restoreDeviceCredential(appContext) }
        }
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
        screen = if (runCatching { AuthRepository.hasSession() }.getOrDefault(false)) {
            AppScreen.ADMIN_DASHBOARD
        } else {
            AppScreen.ADMIN_LOGIN
        }
    }

    BackHandler(enabled = screen != AppScreen.ENTRY) {
        when (screen) {
            AppScreen.ADMIN_LOGIN,
            AppScreen.ADMIN_DASHBOARD,
            AppScreen.GUARD_SELECTION,
            -> screen = AppScreen.ENTRY

            AppScreen.ADMIN_SETTINGS,
            AppScreen.ALERTS,
            AppScreen.PATROL_HISTORY,
            AppScreen.REPORTS,
            AppScreen.LOCATIONS,
            AppScreen.GUARDS,
            AppScreen.PATROLS,
            -> screen = AppScreen.ADMIN_DASHBOARD

            AppScreen.PATROL_ASSIGNMENTS,
            AppScreen.OFFLINE_SYNC,
            AppScreen.ARCHIVED,
            AppScreen.DEVICE_PROVISION,
            -> screen = AppScreen.ADMIN_SETTINGS

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
                screen = AppScreen.ENTRY
            }

            AppScreen.GUARD_CHANGE_PIN,
            AppScreen.SHIFT_HOME,
            -> returnToGuardLanding()

            AppScreen.AVAILABLE_PATROLS,
            AppScreen.PATROL_SCANNER,
            -> Unit

            AppScreen.PATROL_FINISHED -> returnToGuardLanding()
            AppScreen.ENTRY -> Unit
        }
    }

    when (screen) {
        AppScreen.ENTRY -> PremiumGuardLandingScreen(
            enabled = deviceCredentialLoaded && PortariaRepository.deviceCredential != null,
            onGuardSelected = {
                selectedGuard = it
                screen = AppScreen.GUARD_PIN
            },
            onAdmin = ::openAdmin,
        )

        AppScreen.ADMIN_LOGIN -> PremiumAdminLoginScreen(
            onLoginSuccess = { screen = AppScreen.ADMIN_DASHBOARD },
            onBack = { screen = AppScreen.ENTRY },
        )

        AppScreen.ADMIN_DASHBOARD -> AdminDashboardScreenV3(
            onOpenLocations = { screen = AppScreen.LOCATIONS },
            onOpenGuards = { screen = AppScreen.GUARDS },
            onOpenPatrols = { screen = AppScreen.PATROLS },
            onOpenHistory = { screen = AppScreen.PATROL_HISTORY },
            onOpenReports = { screen = AppScreen.REPORTS },
            onOpenAlerts = { screen = AppScreen.ALERTS },
            onOpenSettings = { screen = AppScreen.ADMIN_SETTINGS },
            onLogout = {
                screen = AppScreen.ENTRY
                selection = AdminSelection()
            },
        )

        AppScreen.ADMIN_SETTINGS -> AdminSettingsScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onOpenAssignments = { screen = AppScreen.PATROL_ASSIGNMENTS },
            onOpenDevice = { screen = AppScreen.DEVICE_PROVISION },
            onOpenArchived = { screen = AppScreen.ARCHIVED },
            onOpenSync = { screen = AppScreen.OFFLINE_SYNC },
        )

        AppScreen.ALERTS -> AlertsScreen(
            onBack = { screen = AppScreen.ADMIN_DASHBOARD },
            onOpenHistory = { screen = AppScreen.PATROL_HISTORY },
        )
        AppScreen.PATROL_HISTORY -> PatrolHistoryScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.REPORTS -> ReportScreen(onBack = { screen = AppScreen.ADMIN_DASHBOARD })
        AppScreen.PATROL_ASSIGNMENTS -> PatrolAssignmentsScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })
        AppScreen.OFFLINE_SYNC -> OfflineSyncAdminScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })
        AppScreen.ARCHIVED -> ArchivedScreen(onBack = { screen = AppScreen.ADMIN_SETTINGS })

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
            onBack = { screen = AppScreen.ADMIN_SETTINGS },
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
                screen = AppScreen.ENTRY
            },
        )
        AppScreen.GUARD_CHANGE_PIN -> ChangeGuardPinScreen(onChanged = { screen = AppScreen.SHIFT_HOME })
        AppScreen.SHIFT_HOME -> ShiftHomeScreen(
            onShiftStarted = {
                shift = it
                screen = AppScreen.AVAILABLE_PATROLS
            },
            onBack = { returnToGuardLanding() },
        )
        AppScreen.AVAILABLE_PATROLS -> AvailablePatrolsScreen(
            shift = requireNotNull(shift),
            onStart = { patrol, patrolRun ->
                activePatrol = patrol
                run = patrolRun
                screen = AppScreen.PATROL_SCANNER
            },
            onEndShift = { returnToGuardLanding() },
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
            onDone = { returnToGuardLanding() },
        )
    }
}
