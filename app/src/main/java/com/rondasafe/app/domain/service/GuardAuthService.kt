package com.rondasafe.app.domain.service

import android.content.Context
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.GuardLoginDto
import com.rondasafe.app.data.model.GuardLoginResponse
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.data.remote.datasource.PortariaRemoteDataSource
import java.io.IOException

class GuardAuthService(
    context: Context,
    private val remote: PortariaRemoteDataSource,
    private val sessionStore: GuardSessionStore,
) {
    private val appContext = context.applicationContext

    suspend fun listGuards(): List<PortariaGuardDto> = try {
        val online = remote.listGuards().guards
        runCatching { remote.syncOperationalCache() }
        online
    } catch (error: IOException) {
        OfflineOperationalCache.guards(appContext)
            .takeIf { it.isNotEmpty() }
            ?: throw error
    }

    suspend fun loginGuard(guardId: String, pin: String): GuardLoginResponse = try {
        val payload = remote.loginGuard(guardId, pin)
        val guard = payload.guard ?: error("Porteiro não retornado.")
        val token = payload.guardSession ?: error("Sessão do porteiro não retornada.")
        sessionStore.set(
            GuardSessionStore.GuardSession(
                guardId = guard.id,
                guardName = guard.name,
                token = token,
                offline = false,
            )
        )
        OfflineOperationalCache.clearPinLockout(appContext, guard.id)
        runCatching { remote.syncOperationalCache() }
        payload
    } catch (onlineError: IOException) {
        when (val verification = OfflineOperationalCache.verifyPin(appContext, guardId, pin)) {
            is OfflineOperationalCache.PinVerification.Success -> {
                val cached = verification.guard
                sessionStore.set(
                    GuardSessionStore.GuardSession(
                        guardId = cached.id,
                        guardName = cached.name,
                        token = null,
                        offline = true,
                    )
                )
                GuardLoginResponse(
                    guard = GuardLoginDto(cached.id, cached.name, cached.pinState),
                    mustChangePin = false,
                    guardSession = "offline",
                )
            }
            is OfflineOperationalCache.PinVerification.Invalid ->
                error("PIN inválido. Restam ${verification.remainingAttempts} tentativa(s) offline.")
            is OfflineOperationalCache.PinVerification.Locked ->
                error("PIN temporariamente bloqueado neste aparelho por 15 minutos.")
            OfflineOperationalCache.PinVerification.RequiresConnection ->
                error("O primeiro acesso e a troca do PIN temporário precisam de conexão.")
            OfflineOperationalCache.PinVerification.GuardUnavailable -> throw onlineError
        }
    }

    suspend fun changePin(newPin: String) {
        remote.changePin(newPin)
        runCatching { remote.syncOperationalCache() }
    }
}
