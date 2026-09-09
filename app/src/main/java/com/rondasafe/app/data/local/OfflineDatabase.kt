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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey val clientEventId: String,
    val type: String,
    val payloadJson: String,
    val createdAtLocal: String,
    val monotonicMs: Long?,
    val attempts: Int = 0,
    val lastError: String? = null,
    val state: String = STATE_PENDING,
    val syncedAt: String? = null,
) {
    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_SYNCED = "SYNCED"
        const val STATE_FAILED_PERMANENT = "FAILED_PERMANENT"
    }
}

@Entity(tableName = "local_shifts")
data class LocalShiftEntity(
    @PrimaryKey val shiftClientEventId: String,
    val guardId: String,
    val guardName: String,
    val startedAtLocal: String,
    val active: Boolean,
    val serverShiftId: String? = null,
    val syncState: String = PendingEventEntity.STATE_PENDING,
)

@Entity(tableName = "local_patrol_runs")
data class LocalPatrolRunEntity(
    @PrimaryKey val runClientEventId: String,
    val shiftClientEventId: String,
    val guardId: String,
    val patrolTemplateId: String,
    val scheduleWindowId: String,
    val patrolName: String,
    val scheduledFor: String,
    val isLate: Boolean,
    val requiredPoints: Int,
    val visitedPoints: Int,
    val startedAtLocal: String,
    val active: Boolean,
    val serverRunId: String? = null,
    val finalStatus: String? = null,
    val syncState: String = PendingEventEntity.STATE_PENDING,
)

@Entity(
    tableName = "local_visited_checkpoints",
    primaryKeys = ["runClientEventId", "checkpointId"],
)
data class LocalVisitedCheckpointEntity(
    val runClientEventId: String,
    val checkpointId: String,
    val checkpointName: String,
    val scannedAtLocal: String,
)

@Entity(tableName = "operational_cache")
data class OperationalCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val generatedAt: String,
    val savedAt: String,
)

@Dao
interface OfflineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(event: PendingEventEntity)

    @Query("select * from pending_events where state = 'PENDING' order by createdAtLocal, rowid limit :limit")
    suspend fun pending(limit: Int = 100): List<PendingEventEntity>

    @Query("select * from pending_events where state = 'FAILED_PERMANENT' order by createdAtLocal desc, rowid desc")
    suspend fun permanentFailures(): List<PendingEventEntity>

    @Query("select count(*) from pending_events where state = 'PENDING'")
    suspend fun pendingCount(): Int

    @Query("select count(*) from pending_events where state = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    @Query("select count(*) from pending_events where state = 'FAILED_PERMANENT'")
    fun permanentFailureCountFlow(): Flow<Int>

    @Query("select lastError from pending_events where state = 'FAILED_PERMANENT' order by createdAtLocal desc, rowid desc limit 1")
    fun latestPermanentFailureFlow(): Flow<String?>

    @Query("select state from pending_events where clientEventId = :id limit 1")
    suspend fun eventState(id: String): String?

    @Query("update pending_events set state = 'SYNCED', syncedAt = :syncedAt, lastError = null where clientEventId = :id")
    suspend fun markSynced(id: String, syncedAt: String)

    @Query("delete from pending_events where state = 'SYNCED' and syncedAt < :before")
    suspend fun purgeSynced(before: String)

    @Query("update pending_events set attempts = attempts + 1, lastError = :error where clientEventId = :id")
    suspend fun markFailed(id: String, error: String?)

    @Query("update pending_events set attempts = attempts + 1, lastError = :error, state = 'FAILED_PERMANENT' where clientEventId = :id")
    suspend fun markPermanentFailure(id: String, error: String?)

    @Query("update pending_events set state = 'PENDING', syncedAt = null, lastError = null where clientEventId = :id and state = 'FAILED_PERMANENT'")
    suspend fun requeuePermanentFailure(id: String)

    @Query("""
        update pending_events
        set state = 'PENDING', syncedAt = null, attempts = 0, lastError = null
        where state = 'FAILED_PERMANENT'
          and (
            lastError like '%PARENT_RUN_NOT_SYNCED%'
            or lastError like '%PARENT_SHIFT_NOT_SYNCED%'
            or lastError like 'Registro antigo incompatível com a configuração atual do aparelho%'
          )
    """)
    suspend fun recoverLegacyCompatibilityFailures()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocalShift(shift: LocalShiftEntity)

    @Query("select * from local_shifts where active = 1 limit 1")
    suspend fun activeLocalShift(): LocalShiftEntity?

    @Query("select * from local_shifts where shiftClientEventId = :id limit 1")
    suspend fun localShift(id: String): LocalShiftEntity?

    @Query("update local_shifts set active = 0 where shiftClientEventId = :id")
    suspend fun finishLocalShift(id: String)

    @Query("update local_shifts set serverShiftId = :serverId, syncState = 'SYNCED' where shiftClientEventId = :clientId")
    suspend fun markShiftSynced(clientId: String, serverId: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLocalRun(run: LocalPatrolRunEntity)

    @Query("select * from local_patrol_runs where active = 1 limit 1")
    suspend fun activeLocalRun(): LocalPatrolRunEntity?

    @Query("select * from local_patrol_runs where runClientEventId = :id limit 1")
    suspend fun localRun(id: String): LocalPatrolRunEntity?

    @Query("select * from local_patrol_runs where scheduleWindowId = :scheduleWindowId and scheduledFor = :scheduledFor order by startedAtLocal desc limit 1")
    suspend fun localOccurrence(scheduleWindowId: String, scheduledFor: String): LocalPatrolRunEntity?

    @Query("update local_patrol_runs set visitedPoints = :count where runClientEventId = :id")
    suspend fun updateLocalVisited(id: String, count: Int)

    @Query("update local_patrol_runs set active = 0 where runClientEventId = :id")
    suspend fun finishLocalRun(id: String)

    @Query("update local_patrol_runs set serverRunId = :serverId, syncState = 'SYNCED' where runClientEventId = :clientId")
    suspend fun markRunStartedSynced(clientId: String, serverId: String?)

    @Query("update local_patrol_runs set finalStatus = :status, syncState = 'SYNCED', active = 0 where runClientEventId = :clientId")
    suspend fun markRunFinishedSynced(clientId: String, status: String?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLocalVisit(visit: LocalVisitedCheckpointEntity): Long

    @Query("select count(*) from local_visited_checkpoints where runClientEventId = :runId")
    suspend fun localVisitCount(runId: String): Int

    @Query("select checkpointId from local_visited_checkpoints where runClientEventId = :runId")
    suspend fun localVisitedCheckpointIds(runId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOperationalCache(cache: OperationalCacheEntity)

    @Query("select * from operational_cache where cacheKey = :key limit 1")
    suspend fun operationalCache(key: String): OperationalCacheEntity?

    @Query("delete from operational_cache where cacheKey = :key")
    suspend fun deleteOperationalCache(key: String)
}

@Database(
    entities = [
        PendingEventEntity::class,
        LocalShiftEntity::class,
        LocalPatrolRunEntity::class,
        LocalVisitedCheckpointEntity::class,
        OperationalCacheEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_shifts` (`shiftClientEventId` TEXT NOT NULL, `guardId` TEXT NOT NULL, `guardName` TEXT NOT NULL, `startedAtLocal` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`shiftClientEventId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_patrol_runs` (`runClientEventId` TEXT NOT NULL, `shiftClientEventId` TEXT NOT NULL, `guardId` TEXT NOT NULL, `patrolTemplateId` TEXT NOT NULL, `scheduleWindowId` TEXT NOT NULL, `patrolName` TEXT NOT NULL, `scheduledFor` TEXT NOT NULL, `isLate` INTEGER NOT NULL, `requiredPoints` INTEGER NOT NULL, `visitedPoints` INTEGER NOT NULL, `startedAtLocal` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`runClientEventId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_visited_checkpoints` (`runClientEventId` TEXT NOT NULL, `checkpointId` TEXT NOT NULL, `checkpointName` TEXT NOT NULL, `scannedAtLocal` TEXT NOT NULL, PRIMARY KEY(`runClientEventId`, `checkpointId`))")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `offline_guard_access`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_events` ADD COLUMN `state` TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `offline_qr_tokens`")
                db.execSQL("DROP TABLE IF EXISTS `offline_patrol_state`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_events` ADD COLUMN `syncedAt` TEXT")
                db.execSQL("ALTER TABLE `local_shifts` ADD COLUMN `serverShiftId` TEXT")
                db.execSQL("ALTER TABLE `local_shifts` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE `local_patrol_runs` ADD COLUMN `serverRunId` TEXT")
                db.execSQL("ALTER TABLE `local_patrol_runs` ADD COLUMN `finalStatus` TEXT")
                db.execSQL("ALTER TABLE `local_patrol_runs` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `operational_cache` (`cacheKey` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `generatedAt` TEXT NOT NULL, `savedAt` TEXT NOT NULL, PRIMARY KEY(`cacheKey`))")
            }
        }

        fun get(context: Context): OfflineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "rondasafe_offline.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
