package tv.pelishd4k.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

/**
 * Elemento de la biblioteca local, identificado por la URL de la página (o del flujo directo).
 * Un mismo registro sirve para favoritos, historial y "continuar viendo".
 */
@Entity(tableName = "library", indices = [Index(value = ["url"], unique = true)])
data class LibraryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val posterUrl: String? = null,
    val isFavorite: Boolean = false,
    /** La página contenía un reproductor (vídeo o iframe), así que cuenta como contenido visto. */
    val isContent: Boolean = false,
    val lastVisitedAt: Long = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Último flujo directo reproducido con ExoPlayer (para reanudar). */
    val streamUrl: String? = null,
) {
    /** Fracción vista 0..1, o 0 si no se conoce. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Entity(tableName = "searches")
data class SearchEntry(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): LibraryItem?

    @Insert
    suspend fun insert(item: LibraryItem): Long

    @Update
    suspend fun update(item: LibraryItem)

    @Query("SELECT * FROM library WHERE isFavorite = 1 ORDER BY lastVisitedAt DESC, id DESC")
    suspend fun favorites(): List<LibraryItem>

    @Query("SELECT * FROM library WHERE lastVisitedAt > 0 ORDER BY lastVisitedAt DESC LIMIT :limit")
    suspend fun history(limit: Int): List<LibraryItem>

    @Query(
        "SELECT * FROM library WHERE lastVisitedAt > 0 AND (" +
            "(positionMs > 5000 AND (durationMs <= 0 OR positionMs < durationMs * 0.95)) OR " +
            "(isContent = 1 AND positionMs = 0 AND durationMs = 0)) " +
            "ORDER BY lastVisitedAt DESC LIMIT :limit"
    )
    suspend fun continueWatching(limit: Int): List<LibraryItem>

    @Query("UPDATE library SET lastVisitedAt = 0, positionMs = 0, durationMs = 0 WHERE url = :url")
    suspend fun clearProgress(url: String)

    @Query("UPDATE library SET lastVisitedAt = 0, positionMs = 0, durationMs = 0, isContent = 0")
    suspend fun clearAllHistory()

    @Query("DELETE FROM library WHERE isFavorite = 0 AND lastVisitedAt = 0")
    suspend fun deleteOrphans()

    @Query("UPDATE library SET isFavorite = 0 WHERE url = :url")
    suspend fun unfavorite(url: String)

    @Query("SELECT * FROM searches ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun recentSearches(limit: Int): List<SearchEntry>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun putSearch(entry: SearchEntry)

    @Query("DELETE FROM searches")
    suspend fun clearSearches()

    @Query("DELETE FROM searches WHERE `query` NOT IN (SELECT `query` FROM searches ORDER BY searchedAt DESC LIMIT 30)")
    suspend fun trimSearches()
}

@Database(entities = [LibraryItem::class, SearchEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao
}
