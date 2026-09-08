package com.rondasafe.app.data.local

import android.content.Context
import com.rondasafe.app.data.local.cache.OperationalCacheIndex
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.CachedGuardDto
import com.rondasafe.app.data.model.PortariaCacheResponse
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.domain.service.PatrolWindowEvaluator
import com.rondasafe.app.security.OfflineCredentialVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object OfflineOperationalCache {
    private const val CACHE_KEY = "portaria_operational_cache_v1"
    private const val PIN_LOCK_KEY_PREFIX = "offline_pin_lock_v1_"
    private const val MAX_PIN_ATTEMPTS = 5
    private const val PIN_LOCKOUT_MS = 15 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var memoryCache: PortariaCacheResponse? = null
    @Volatile private var memoryIndex: OperationalCacheIndex? = null

    sealed interface PinVerification {
        data class Success(val guard: CachedGuardDto) : PinVerification
        data class Invalid(val remainingAttempts: Int) : PinVerification
        data class Locked(val lockedUntilEpochMs: Long) : PinVerification
        data object RequiresConnection : PinVerification
        data object GuardUnavailable : PinVerification
    }

    data class QrMatch(
        val tokenHash: String,
        val checkpointId: String,
        val checkpointName: String,
    )

    private data class PinAttemptState(val attempts: Int, val lockedUntilEpochMs: Long)

    fun save(context: Context, cache: PortariaCacheResponse) {
        OfflineCredentialVault.put(context.applicationContext, CACHE_KEY, json.encodeToString(cache))
        installMemoryCache(cache)
    }

    fun load(context: Context): PortariaCacheResponse? {
        memoryCache?.let { return it }
        return synchronized(this) {
            memoryCache ?: run {
                val raw = OfflineCredentialVault.get(context.applicationContext, CACHE_KEY) ?: return@synchronized null
                runCatching { json.decodeFromString<PortariaCacheResponse>(raw) }
                    .getOrNull()
                    ?.also(::installMemoryCache)
            }
        }
    }

    fun hasCache(context: Context): Boolean = load(context) != null

    fun guards(context: Context): List<PortariaGuardDto> =
        load(context)?.guards.orEmpty().map {
            PortariaGuardDto(
                id = it.id,
                name = it.name,
                photoUrl = it.photoUrl,
                pinState = it.pinState,
            )
        }

    fun verifyPin(context: Context, guardId: String, pin: String): PinVerification {
        val guard = index(context)?.guardById?.get(guardId)
            ?: return PinVerification.GuardUnavailable
        if (guard.credential.mustChangePin) return PinVerification.RequiresConnection

        val now = System.currentTimeMillis()
        var state = loadPinAttemptState(context, guardId)
        if (state.lockedUntilEpochMs > now) return PinVerification.Locked(state.lockedUntilEpochMs)
        if (state.lockedUntilEpochMs > 0L) {
            clearPinLockout(context, guardId)
            state = PinAttemptState(0, 0L)
        }

        val validFormat = pin.length == 6 && pin.all(Char::isDigit)
        val matches = validFormat && runCatching {
            val candidate = pbkdf2Hex(pin, guard.credential.pinSalt, guard.credential.iterations)
            constantTimeEquals(candidate, guard.credential.pinHash)
        }.getOrDefault(false)

        if (matches) {
            clearPinLockout(context, guardId)
            return PinVerification.Success(guard)
        }

        val attempts = state.attempts + 1
        if (attempts >= MAX_PIN_ATTEMPTS) {
            val lockedUntil = now + PIN_LOCKOUT_MS
            savePinAttemptState(context, guardId, PinAttemptState(MAX_PIN_ATTEMPTS, lockedUntil))
            return PinVerification.Locked(lockedUntil)
        }

        savePinAttemptState(context, guardId, PinAttemptState(attempts, 0L))
        return PinVerification.Invalid(MAX_PIN_ATTEMPTS - attempts)
    }

    fun clearPinLockout(context: Context, guardId: String) {
        OfflineCredentialVault.remove(context.applicationContext, PIN_LOCK_KEY_PREFIX + guardId)
    }

    fun availablePatrols(context: Context, guardId: String): List<AvailablePatrolDto> {
        val cache = load(context) ?: return emptyList()
        val idx = index(context) ?: return emptyList()
        val zone = runCatching { ZoneId.of(cache.building.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalDateTime.now(zone)

        return cache.windows.mapNotNull { window ->
            val patrol = idx.patrolById[window.patrolTemplateId] ?: return@mapNotNull null
            val assignments = idx.assignmentsByWindow[window.id].orEmpty()
            if (assignments.isNotEmpty() && assignments.none { it.guardId == guardId }) return@mapNotNull null

            val start = parseTime(window.startTime)
            val end = parseTime(window.endTime)
            val occurrence = PatrolWindowEvaluator.activeOccurrence(
                dayOfWeek = window.dayOfWeek,
                start = start,
                end = end,
                now = now,
            ) ?: return@mapNotNull null

            val scheduled = LocalDateTime.of(occurrence.scheduledDate, start).atZone(zone)
            val availableUntil = LocalDateTime.of(occurrence.availableUntilDate, end).atZone(zone)
            val required = idx.requiredCheckpointsByPatrol[patrol.id].orEmpty().size

            AvailablePatrolDto(
                patrolTemplateId = patrol.id,
                scheduleWindowId = window.id,
                patrolName = patrol.name,
                scheduledFor = scheduled.toInstant().toString(),
                availableUntil = availableUntil.toInstant().toString(),
                isLate = now.atZone(zone).toInstant().isAfter(
                    scheduled.plusMinutes(window.lateToleranceMinutes.toLong()).toInstant()
                ),
                requiredPoints = required,
            )
        }.sortedBy { it.scheduledFor }
    }

    fun requiredCheckpointIds(context: Context, patrolTemplateId: String): Set<String> =
        index(context)?.requiredCheckpointsByPatrol?.get(patrolTemplateId).orEmpty()

    fun qrMatch(context: Context, rawQr: String): QrMatch? {
        val idx = index(context) ?: return null
        val hash = tokenHash(rawQr).lowercase()
        val qr = idx.qrByHash[hash] ?: return null
        val checkpoint = idx.checkpointById[qr.checkpointId] ?: return null
        return QrMatch(hash, checkpoint.id, checkpoint.name)
    }

    fun tokenHash(rawQr: String): String = sha256Hex(rawQr)

    fun clear(context: Context) {
        synchronized(this) {
            memoryCache = null
            memoryIndex = null
        }
        OfflineCredentialVault.remove(context.applicationContext, CACHE_KEY)
    }

    private fun index(context: Context): OperationalCacheIndex? {
        memoryIndex?.let { return it }
        val cache = load(context) ?: return null
        return synchronized(this) {
            memoryIndex ?: OperationalCacheIndex.from(cache).also { memoryIndex = it }
        }
    }

    private fun installMemoryCache(cache: PortariaCacheResponse) {
        memoryCache = cache
        memoryIndex = OperationalCacheIndex.from(cache)
    }

    private fun loadPinAttemptState(context: Context, guardId: String): PinAttemptState {
        val raw = OfflineCredentialVault.get(context.applicationContext, PIN_LOCK_KEY_PREFIX + guardId)
            ?: return PinAttemptState(0, 0L)
        val parts = raw.split('|')
        if (parts.size != 2) return PinAttemptState(0, 0L)
        return PinAttemptState(
            attempts = parts[0].toIntOrNull()?.coerceIn(0, MAX_PIN_ATTEMPTS) ?: 0,
            lockedUntilEpochMs = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        )
    }

    private fun savePinAttemptState(context: Context, guardId: String, state: PinAttemptState) {
        OfflineCredentialVault.put(
            context.applicationContext,
            PIN_LOCK_KEY_PREFIX + guardId,
            "${state.attempts}|${state.lockedUntilEpochMs}",
        )
    }

    private fun parseTime(value: String): LocalTime = LocalTime.parse(value.take(8))

    private fun pbkdf2Hex(pin: String, saltHex: String, iterations: Int): String {
        val salt = hexToBytes(saltHex)
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return key.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hexToBytes(value: String): ByteArray {
        require(value.length % 2 == 0 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "Formato de salt inválido."
        }
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.lowercase().toByteArray(), b.lowercase().toByteArray())
}
