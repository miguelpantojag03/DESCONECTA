package com.desconecta.hp.mobile.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.desconecta.hp.shared.model.DaySummary
import kotlinx.coroutines.flow.Flow

/** Un día terminado (para el calendario-mapa de calor y las estadísticas). */
@Entity(tableName = "day_history")
data class DayRecord(
    @PrimaryKey val dayKey: String,
    val maxHp: Int,
    val hpLeft: Int,
    val gameOver: Boolean,
    val xpEarned: Int,
    /** Minutos por app ese día, "pkg=min;pkg=min" (para el bestiario histórico). */
    val usageByApp: String = "",
) {
    fun toSummary() = DaySummary(dayKey, maxHp, hpLeft, gameOver, xpEarned)
}

/** Copia de los logros con fecha, para el salón de logros. */
@Entity(tableName = "achievements")
data class AchievementRecord(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)

/**
 * Uso agregado por HORA (nunca eventos detallados). Es lo que se sube a app_usage:
 * ahorra batería y datos y nunca contiene qué se hizo dentro de la app.
 */
@Entity(tableName = "hourly_usage", primaryKeys = ["dayKey", "hour", "packageName"])
data class HourlyUsage(
    val dayKey: String,
    val hour: Int,
    val packageName: String,
    val minutes: Int,
    val isAlly: Boolean,
    /** true = cambió desde la última subida. */
    val dirty: Boolean = true,
)

/**
 * Cola de escrituras pendientes hacia Supabase (offline-first). Toda acción se aplica
 * primero en local y se encola aquí; el SyncWorker la sube cuando hay conexión.
 *
 * @param coalesceKey si no es null, una nueva operación con la misma clave REEMPLAZA el
 *   contenido de la anterior conservando su posición (p. ej. el estado del día solo
 *   necesita su última versión).
 */
@Entity(tableName = "sync_queue", indices = [Index(value = ["coalesceKey"], unique = true)])
data class SyncOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val payload: String,
    val coalesceKey: String?,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DayRecord)

    @Query("SELECT * FROM day_history ORDER BY dayKey DESC LIMIT :limit")
    fun recent(limit: Int = 120): Flow<List<DayRecord>>

    @Query("SELECT * FROM day_history WHERE dayKey LIKE :monthPrefix || '%' ORDER BY dayKey")
    fun month(monthPrefix: String): Flow<List<DayRecord>>

    @Query("DELETE FROM day_history")
    suspend fun clear()
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AchievementRecord)

    @Query("SELECT * FROM achievements")
    fun all(): Flow<List<AchievementRecord>>

    @Query("DELETE FROM achievements")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM hourly_usage WHERE dayKey = :day AND hour = :hour AND packageName = :pkg")
    suspend fun get(day: String, hour: Int, pkg: String): HourlyUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: HourlyUsage)

    /** Suma minutos a la hora indicada (máximo 60 por hora, igual que el CHECK del servidor). */
    @Transaction
    suspend fun add(day: String, hour: Int, pkg: String, minutes: Int, isAlly: Boolean) {
        val current = get(day, hour, pkg)?.minutes ?: 0
        put(HourlyUsage(day, hour, pkg, (current + minutes).coerceAtMost(60), isAlly, dirty = true))
    }

    @Query("SELECT * FROM hourly_usage WHERE dirty = 1 ORDER BY dayKey, hour LIMIT 500")
    suspend fun dirty(): List<HourlyUsage>

    @Query("UPDATE hourly_usage SET dirty = 0 WHERE dayKey = :day AND hour = :hour AND packageName = :pkg AND minutes = :minutes")
    suspend fun markClean(day: String, hour: Int, pkg: String, minutes: Int)

    @Query("SELECT * FROM hourly_usage WHERE dayKey >= :fromDay ORDER BY dayKey, hour")
    fun since(fromDay: String): Flow<List<HourlyUsage>>

    @Query("SELECT * FROM hourly_usage WHERE dayKey >= :fromDay ORDER BY dayKey, hour")
    suspend fun sinceOnce(fromDay: String): List<HourlyUsage>

    /** El uso de días pasados como invitado se queda en el teléfono (no se sube a la cuenta). */
    @Query("UPDATE hourly_usage SET dirty = 0 WHERE dayKey < :beforeDay")
    suspend fun keepLocalBefore(beforeDay: String)

    @Query("DELETE FROM hourly_usage WHERE dayKey < :beforeDay AND dirty = 0")
    suspend fun prune(beforeDay: String)
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_queue WHERE coalesceKey = :key")
    suspend fun byKey(key: String): SyncOpEntity?

    @Insert
    suspend fun insert(op: SyncOpEntity): Long

    @Query("UPDATE sync_queue SET payload = :payload, attempts = 0, lastError = NULL WHERE id = :id")
    suspend fun replacePayload(id: Long, payload: String)

    /** Encola respetando la fusión por clave (conserva la posición original en la cola). */
    @Transaction
    suspend fun enqueue(op: SyncOpEntity) {
        val existing = op.coalesceKey?.let { byKey(it) }
        if (existing != null) replacePayload(existing.id, op.payload) else insert(op)
    }

    @Query("SELECT * FROM sync_queue ORDER BY id LIMIT :limit")
    suspend fun next(limit: Int = 50): List<SyncOpEntity>

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE type = :type AND payload LIKE '%' || :fragment || '%'")
    suspend fun countMatching(type: String, fragment: String): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}

/**
 * Ajustes de IA (una sola fila). Se guardan como JSON de [com.desconecta.hp.shared.ai.model.AiSettings]:
 * es la caché local de la tabla ai_settings de Supabase.
 */
@Entity(tableName = "ai_settings")
data class AiSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
)

@Dao
interface AiSettingsDao {
    @Query("SELECT * FROM ai_settings WHERE id = 1")
    fun observe(): Flow<AiSettingsEntity?>

    @Query("SELECT * FROM ai_settings WHERE id = 1")
    suspend fun get(): AiSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AiSettingsEntity)

    @Query("SELECT COUNT(*) FROM day_history WHERE dayKey >= :fromDay")
    fun daysSince(fromDay: String): Flow<Int>
}

/** v2 → v3: añade ai_settings sin borrar el historial ni la cola de sincronización. */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_settings` (`id` INTEGER NOT NULL, `json` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

@Database(
    entities = [DayRecord::class, AchievementRecord::class, HourlyUsage::class, SyncOpEntity::class, AiSettingsEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class HpDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun achievements(): AchievementDao
    abstract fun usage(): UsageDao
    abstract fun sync(): SyncDao
    abstract fun aiSettings(): AiSettingsDao
}
