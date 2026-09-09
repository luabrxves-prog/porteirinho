package com.rondasafe.app.data.sync

import java.time.Instant

/** Regras puras usadas por repositories para decidir estado e conflito. */
object SyncPolicy {
    fun canShowDefinitiveSuccess(synced: Boolean): Boolean = synced

    fun hasVersionConflict(expectedVersion: Long, serverVersion: Long): Boolean =
        expectedVersion != serverVersion

    fun shouldApplyRemote(
        localVersion: Long,
        remoteVersion: Long,
        localUpdatedAt: String? = null,
        remoteUpdatedAt: String? = null,
    ): Boolean {
        if (remoteVersion != localVersion) return remoteVersion > localVersion
        val local = localUpdatedAt?.let(::parseInstantOrNull)
        val remote = remoteUpdatedAt?.let(::parseInstantOrNull)
        if (local == null || remote == null) return true
        return !remote.isBefore(local)
    }

    private fun parseInstantOrNull(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()
}
