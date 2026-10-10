package com.desconecta.hp.mobile.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DayRecord)

    @Query("SELECT * FROM day_history ORDER BY dayKey DESC LIMIT :limit")
    fun recent(limit: Int = 120): Flow<List<DayRecord>>

    @Query("SELECT * FROM day_history WHERE dayKey LIKE :monthPrefix || '%' ORDER BY dayKey")
    fun month(monthPrefix: String): Flow<List<DayRecord>>
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: AchievementRecord)

    @Query("SELECT * FROM achievements")
    fun all(): Flow<List<AchievementRecord>>
}

@Database(entities = [DayRecord::class, AchievementRecord::class], version = 1, exportSchema = false)
abstract class HpDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
    abstract fun achievements(): AchievementDao
}
