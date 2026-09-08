package com.rondasafe.app

import android.content.Context
import com.rondasafe.app.data.remote.datasource.OfflineSyncRemoteDataSource
import com.rondasafe.app.data.remote.datasource.PortariaRemoteDataSource
import com.rondasafe.app.data.sync.OfflineEventQueue
import com.rondasafe.app.data.sync.OfflineSyncService
import com.rondasafe.app.domain.service.GuardAuthService
import com.rondasafe.app.domain.service.GuardSessionStore
import com.rondasafe.app.domain.service.OccurrenceService
import com.rondasafe.app.domain.service.PatrolAvailabilityService
import com.rondasafe.app.domain.service.PatrolRunService
import com.rondasafe.app.domain.service.QrScanService
import com.rondasafe.app.security.DeviceCredentialStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val deviceCredentialStore = DeviceCredentialStore(appContext)
    val guardSessionStore = GuardSessionStore()

    val offlineSyncRemoteDataSource = OfflineSyncRemoteDataSource(deviceCredentialStore)
    val offlineSyncService = OfflineSyncService(
        context = appContext,
        remote = offlineSyncRemoteDataSource,
    )
    val offlineEventQueue = OfflineEventQueue(appContext)

    val portariaRemoteDataSource = PortariaRemoteDataSource(
        context = appContext,
        deviceCredentialStore = deviceCredentialStore,
        guardSessionStore = guardSessionStore,
    )
    val guardAuthService = GuardAuthService(
        context = appContext,
        remote = portariaRemoteDataSource,
        sessionStore = guardSessionStore,
    )
    val patrolAvailabilityService = PatrolAvailabilityService(
        context = appContext,
        remote = portariaRemoteDataSource,
        sessionStore = guardSessionStore,
    )
    val patrolRunService = PatrolRunService(
        context = appContext,
        sessionStore = guardSessionStore,
        eventQueue = offlineEventQueue,
    )
    val qrScanService = QrScanService(
        context = appContext,
        sessionStore = guardSessionStore,
        eventQueue = offlineEventQueue,
    )
    val occurrenceService = OccurrenceService(
        context = appContext,
        sessionStore = guardSessionStore,
        eventQueue = offlineEventQueue,
    )
}
