package com.rondasafe.app.data.local

import android.content.Context
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.CachedGuardDto
import com.rondasafe.app.data.model.PortariaCacheResponse
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.security.OfflineCredentialVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.DayOfWeek
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
        val encoded = runCatching { json.encodeToString(cache) }.getOrNull() ?: return
        safeVaultPut(context, CACHE_KEY, encoded)
    }

    fun load(context: Context): PortariaCacheResponse? {
        val raw = safeVaultGet(context, CACHE_KEY) ?: return null
        return runCatching { json.decodeFromString<PortariaCacheResponse>(raw) }
            .onFailure { safeVaultRemove(context, CACHE_KEY) }
            .getOrNull()
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
        val guard = load(context)?.guards?.firstOrNull { it.id == guardId }
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
        safeVaultRemove(context, PIN_LOCK_KEY_PREFIX + guardId)
    }

    fun availablePatrols(context: Context, guardId: String): List<AvailablePatrolDto> {
        val cache = load(context) ?: return emptyList()
        val zone = runCatching { ZoneId.of(cache.building.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalDateTime.now(zone)
        val isoDay = now.dayOfWeek.value

        return cache.windows.mapNotNull { window ->
            val patrol = cache.patrols.firstOrNull { it.id == window.patrolTemplateId } ?: return@mapNotNull null

            val start = parseTimeOrNull(window.startTime) ?: return@mapNotNull null
            val end = parseTimeOrNull(window.endTime) ?: return@mapNotNull null
            val localNow = now.toLocalTime()
            val active = when {
                end > start -> window.dayOfWeek == isoDay && !localNow.isBefore(start) && !localNow.isAfter(end)
                else -> {
                    val previousDay = if (isoDay == DayOfWeek.MONDAY.value) 7 else isoDay - 1
                    (window.dayOfWeek == isoDay && !localNow.isBefore(start)) ||
                        (window.dayOfWeek == previousDay && !localNow.isAfter(end))
                }
            }
            if (!active) return@mapNotNull null

            val scheduledDate = when {
                end > start -> now.toLocalDate()
                !localNow.isBefore(start) -> now.toLocalDate()
                else -> now.toLocalDate().minusDays(1)
            }
            val scheduled = LocalDateTime.of(scheduledDate, start).atZone(zone)
            val availableUntilDate = if (end > start) scheduledDate else scheduledDate.plusDays(1)
            val availableUntil = LocalDateTime.of(availableUntilDate, end).atZone(zone)
            val required = requiredCheckpointIds(context, patrol.id).size

            AvailablePatrolDto(
                patrolTemplateId = patrol.id,
                scheduleWindowId = window.id,
                patrolName = patrol.name,
                scheduledFor = scheduled.toInstant().toString(),
                availableUntil = availableUntil.toInstant().toString(),
                isLate = now.atZone(zone).toInstant().isAfter(scheduled.plusMinutes(window.lateToleranceMinutes.toLong()).toInstant()),
                requiredPoints = required,
            )
        }.sortedBy { it.scheduledFor }
    }

    fun requiredCheckpointIds(context: Context, patrolTemplateId: String): Set<String> =
        load(context)?.patrolCheckpoints.orEmpty()
            .asSequence()
            .filter { it.patrolTemplateId == patrolTemplateId && it.required }
            .map { it.checkpointId }
            .toSet()

    fun qrMatch(context: Context, rawQr: String): QrMatch? {
        val cache = load(context) ?: return null
        val hash = tokenHash(rawQr)
        val qr = cache.qrTokens.firstOrNull { constantTimeEquals(it.tokenHash, hash) } ?: return null
        val checkpoint = cache.checkpoints.firstOrNull { it.id == qr.checkpointId } ?: return null
        return QrMatch(hash, checkpoint.id, checkpoint.name)
    }

    fun tokenHash(rawQr: String): String = sha256Hex(rawQr)

    fun clear(context: Context) {
        safeVaultRemove(context, CACHE_KEY)
    }

    private fun loadPinAttemptState(context: Context, guardId: String): PinAttemptState {
        val raw = safeVaultGet(context, PIN_LOCK_KEY_PREFIX + guardId) ?: return PinAttemptState(0, 0L)
        val parts = raw.split('|')
        if (parts.size != 2) return PinAttemptState(0, 0L)
        return PinAttemptState(
            attempts = parts[0].toIntOrNull()?.coerceIn(0, MAX_PIN_ATTEMPTS) ?: 0,
            lockedUntilEpochMs = parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
        )
    }

    private fun savePinAttemptState(context: Context, guardId: String, state: PinAttemptState) {
        safeVaultPut(
            context,
            PIN_LOCK_KEY_PREFIX + guardId,
            "${state.attempts}|${state.lockedUntilEpochMs}",
        )
    }

    private fun safeVaultGet(context: Context, key: String): String? =
        runCatching { OfflineCredentialVault.get(context, key) }
            .onFailure { runCatching { OfflineCredentialVault.remove(context, key) } }
            .getOrNull()

    private fun safeVaultPut(context: Context, key: String, value: String) {
        runCatching { OfflineCredentialVault.put(context, key, value) }
    }

    private fun safeVaultRemove(context: Context, key: String) {
        runCatching { OfflineCredentialVault.remove(context, key) }
    }

    private fun parseTimeOrNull(value: String): LocalTime? =
        runCatching { LocalTime.parse(value.take(8)) }.getOrNull()

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
