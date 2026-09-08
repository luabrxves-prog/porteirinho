package com.rondasafe.app.data.repository

import android.content.Context
import com.rondasafe.app.AppContainer
import com.rondasafe.app.RondaSafeApplication
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.DeviceProvisionRequest
import com.rondasafe.app.data.model.DeviceProvisionResponse
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.GuardLoginResponse
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.model.PortariaCacheResponse
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.data.model.ScanDto
import com.rondasafe.app.data.model.ShiftDto

/**
 * Compatibility facade while presentation migrates to ViewModels/use cases.
 * Business state and behavior live in AppContainer services, not in this object.
 */
object PortariaRepository {
    data class DeviceCredential(val deviceId: String, val deviceSecret: String)
    data class GuardSession(
        val guardId: String,
        val guardName: String,
        val token: String? = null,
        val offline: Boolean = false,
    )

    private var appContext: Context? = null

    val deviceCredential: DeviceCredential?
        get() = appContainerOrNull()?.deviceCredentialStore?.current?.let {
            DeviceCredential(it.deviceId, it.deviceSecret)
        }

    val guardSession: GuardSession?
        get() = appContainerOrNull()?.guardSessionStore?.current?.let {
            GuardSession(
                guardId = it.guardId,
                guardName = it.guardName,
                token = it.token,
                offline = it.offline,
            )
        }

    fun restoreDeviceCredential(context: Context) {
        appContext = context.applicationContext
        appContainer().deviceCredentialStore.restore()
    }

    fun persistDeviceCredential(context: Context) {
        appContext = context.applicationContext
        appContainer().deviceCredentialStore.persist()
    }

    fun clearDeviceCredential(context: Context) {
        appContext = context.applicationContext
        OfflineOperationalCache.clear(context.applicationContext)
        appContainer().deviceCredentialStore.clear()
    }

    fun clearGuardSession() {
        appContainer().guardSessionStore.clear()
    }

    suspend fun provisionDevice(request: DeviceProvisionRequest): DeviceProvisionResponse =
        appContainer().portariaRemoteDataSource.provisionDevice(request)

    suspend fun syncOperationalCache(): PortariaCacheResponse =
        appContainer().portariaRemoteDataSource.syncOperationalCache()

    suspend fun listGuards(): List<PortariaGuardDto> =
        appContainer().guardAuthService.listGuards()

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse =
        appContainer().guardAuthService.loginGuard(guardId, pin)

    suspend fun changePin(newPin: String) {
        appContainer().guardAuthService.changePin(newPin)
    }

    suspend fun startShift(): ShiftDto =
        appContainer().patrolRunService.startShift()

    suspend fun availablePatrols(): List<AvailablePatrolDto> =
        appContainer().patrolAvailabilityService.availablePatrols()

    suspend fun startPatrol(shiftId: String, patrol: AvailablePatrolDto): PatrolRunDto =
        appContainer().patrolRunService.startPatrol(shiftId, patrol)

    suspend fun scan(runId: String, qr: String, monotonicMs: Long): ScanDto =
        appContainer().qrScanService.scan(runId, qr, monotonicMs)

    suspend fun finishPatrol(runId: String): FinishPatrolDto =
        appContainer().patrolRunService.finishPatrol(runId)

    suspend fun endShift(shiftId: String) {
        appContainer().patrolRunService.endShift(shiftId)
    }

    suspend fun pendingOfflineEvents(): Int =
        appContainer().offlineEventQueue.pendingCount()

    fun scheduleOfflineSync() {
        appContainer().offlineEventQueue.requestSync()
    }

    fun isOfflineSession(): Boolean =
        appContainer().guardSessionStore.isOffline()

    private fun appContainer(): AppContainer =
        appContainerOrNull() ?: error("Contexto do aplicativo indisponível.")

    private fun appContainerOrNull(): AppContainer? {
        val application = appContext?.applicationContext as? RondaSafeApplication
        return application?.container
    }
}
