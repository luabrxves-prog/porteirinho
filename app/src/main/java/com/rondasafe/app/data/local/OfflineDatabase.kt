package com.rondasafe.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey val clientEventId: String,
    val type: String,
    val payloadJson: String,
    val createdAtLocal: String,
    val monotonicMs: Long?,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "offline_guard_access")
data class OfflineGuardAccessEntity(
    @PrimaryKey val guardId: String,
    val guardName: String,
    val verifierHash: String,
    val verifierSalt: String,
    val iterations: Int,
    val pinState: String,
    val credentialVersion: Int,
    val cachedAt: String,
)

@Entity(tableName = "offline_qr_tokens")
data class OfflineQrTokenEntity(
    @PrimaryKey val tokenHash: String,
    val qrTokenId: String,
    val checkpointId: String,
    val checkpointName: String,
    val version: Int,
    val cachedAt: String,
)

@Entity(tableName = "offline_patrol_state")
data class OfflinePatrolStateEntity(
    @PrimaryKey val runId: String,
    val guardId: String,
    val guardName: String,
    val shiftId: String,
    val patrolName: String,
    val requiredPoints: Int,
    val visitedPoints: Int,
    val startedAtLocal: String,
    val active: Boolean,
)

@Dao
interface OfflineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(event: PendingEventEntity)

    @Query("select * from pending_events order by createdAtLocal limit :limit")
    suspend fun pending(limit: Int = 100): List<PendingEventEntity>

    @Query("delete from pending_events where clientEventId = :id")
    suspend fun markSynced(id: String)

    @Query("update pending_events set attempts = attempts + 1, lastError = :error where clientEventId = :id")
    suspend fun markFailed(id: String, error: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheGuard(access: OfflineGuardAccessEntity)

    @Query("select * from offline_guard_access where guardId = :guardId")
    suspend fun guard(guardId: String): OfflineGuardAccessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheQr(tokens: List<OfflineQrTokenEntity>)

    @Query("select * from offline_qr_tokens where tokenHash = :hash limit 1")
    suspend fun qrByHash(hash: String): OfflineQrTokenEntity?

    @Query("delete from offline_qr_tokens")
    suspend fun clearQrs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePatrol(state: OfflinePatrolStateEntity)

    @Query("select * from offline_patrol_state where active = 1 limit 1")
    suspend fun activePatrol(): OfflinePatrolStateEntity?

    @Query("update offline_patrol_state set visitedPoints = :visitedPoints where runId = :runId")
    suspend fun updateVisited(runId: String, visitedPoints: Int)

    @Query("update offline_patrol_state set active = 0 where runId = :runId")
    suspend fun finishPatrol(runId: String)
}

@Database(
    entities = [
        PendingEventEntity::class,
        OfflineGuardAccessEntity::class,
        OfflineQrTokenEntity::class,
        OfflinePatrolStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "rondasafe_offline.db",
                ).build().also { instance = it }
            }
    }
}
