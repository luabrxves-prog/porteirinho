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
    private val json = Json { ignoreUnknownKeys = true }

    data class QrMatch(
        val tokenHash: String,
        val checkpointId: String,
        val checkpointName: String,
    )

    fun save(context: Context, cache: PortariaCacheResponse) {
        OfflineCredentialVault.put(context, CACHE_KEY, json.encodeToString(cache))
    }

    fun load(context: Context): PortariaCacheResponse? {
        val raw = OfflineCredentialVault.get(context, CACHE_KEY) ?: return null
        return runCatching { json.decodeFromString<PortariaCacheResponse>(raw) }.getOrNull()
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

    fun verifyPin(context: Context, guardId: String, pin: String): CachedGuardDto? {
        val guard = load(context)?.guards?.firstOrNull { it.id == guardId } ?: return null
        if (pin.length != 6 || pin.any { !it.isDigit() }) return null
        val candidate = pbkdf2Hex(pin, guard.credential.pinSalt, guard.credential.iterations)
        return guard.takeIf { constantTimeEquals(candidate, guard.credential.pinHash) }
    }

    fun availablePatrols(context: Context, guardId: String): List<AvailablePatrolDto> {
        val cache = load(context) ?: return emptyList()
        val zone = runCatching { ZoneId.of(cache.building.timezone) }.getOrDefault(ZoneId.systemDefault())
        val now = LocalDateTime.now(zone)
        val isoDay = now.dayOfWeek.value

        return cache.windows.mapNotNull { window ->
            val patrol = cache.patrols.firstOrNull { it.id == window.patrolTemplateId } ?: return@mapNotNull null
            val assignments = cache.assignments.filter { it.scheduleWindowId == window.id }
            if (assignments.isNotEmpty() && assignments.none { it.guardId == guardId }) return@mapNotNull null

            val start = parseTime(window.startTime)
            val end = parseTime(window.endTime)
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
            val availableUntilDate = if (end > start || localNow.isBefore(start)) scheduledDate else scheduledDate.plusDays(1)
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
        OfflineCredentialVault.remove(context, CACHE_KEY)
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

    private fun hexToBytes(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.lowercase().toByteArray(), b.lowercase().toByteArray())
}
