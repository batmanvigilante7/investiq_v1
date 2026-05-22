package com.example.data.db

import androidx.room.*
import com.example.data.model.TickerThesis
import com.example.data.model.MarketEvent
import com.example.data.model.ThesisSnapshot
import com.example.data.model.WatchlistAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface TickerThesisDao {
    @Query("SELECT * FROM ticker_theses ORDER BY convictionScore DESC")
    fun getAllTheses(): Flow<List<TickerThesis>>

    @Query("SELECT * FROM ticker_theses")
    suspend fun getAllThesesDirect(): List<TickerThesis>

    @Query("SELECT * FROM ticker_theses WHERE symbol = :symbol LIMIT 1")
    fun getThesisBySymbol(symbol: String): Flow<TickerThesis?>

    @Query("SELECT * FROM ticker_theses WHERE symbol = :symbol LIMIT 1")
    suspend fun getThesisBySymbolDirect(symbol: String): TickerThesis?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThesis(thesis: TickerThesis)

    @Delete
    suspend fun deleteThesis(thesis: TickerThesis)
}

@Dao
interface MarketEventDao {
    @Query("SELECT * FROM market_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<MarketEvent>>

    @Query("SELECT * FROM market_events WHERE symbol = :symbol ORDER BY timestamp DESC")
    fun getEventsForSymbol(symbol: String): Flow<List<MarketEvent>>

    @Query("SELECT * FROM market_events WHERE symbol = :symbol ORDER BY timestamp DESC")
    suspend fun getEventsForSymbolDirect(symbol: String): List<MarketEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MarketEvent): Long

    @Query("DELETE FROM market_events")
    suspend fun clearAllEvents()
}

@Dao
interface ThesisSnapshotDao {
    @Query("SELECT * FROM thesis_snapshots WHERE symbol = :symbol ORDER BY timestamp ASC")
    fun getSnapshotsForSymbol(symbol: String): Flow<List<ThesisSnapshot>>

    @Query("SELECT * FROM thesis_snapshots WHERE symbol = :symbol ORDER BY timestamp ASC")
    suspend fun getSnapshotsForSymbolDirect(symbol: String): List<ThesisSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ThesisSnapshot)
}

@Dao
interface WatchlistAlertDao {
    @Query("SELECT * FROM watchlist_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<WatchlistAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: WatchlistAlert)

    @Query("UPDATE watchlist_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Int)

    @Query("DELETE FROM watchlist_alerts")
    suspend fun clearAllAlerts()
}

@Database(
    entities = [
        TickerThesis::class,
        MarketEvent::class,
        ThesisSnapshot::class,
        WatchlistAlert::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tickerThesisDao(): TickerThesisDao
    abstract fun marketEventDao(): MarketEventDao
    abstract fun thesisSnapshotDao(): ThesisSnapshotDao
    abstract fun watchlistAlertDao(): WatchlistAlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "folio_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
