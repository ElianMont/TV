package tv.pelishd4k.app.data

import androidx.room.withTransaction

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    /** Registra la visita a una página. Conserva favorito/posición ya guardados. */
    suspend fun recordVisit(url: String, title: String, posterUrl: String?, isContent: Boolean) = db.withTransaction {
        val now = System.currentTimeMillis()
        val old = dao.byUrl(url)
        if (old == null) {
            dao.insert(
                LibraryItem(
                    url = url,
                    title = title.ifBlank { url },
                    posterUrl = posterUrl,
                    isContent = isContent,
                    lastVisitedAt = now,
                ),
            )
        } else {
            dao.update(
                old.copy(
                    title = title.ifBlank { old.title },
                    posterUrl = posterUrl ?: old.posterUrl,
                    isContent = old.isContent || isContent,
                    lastVisitedAt = now,
                ),
            )
        }
    }

    /** Alterna el favorito. Devuelve el nuevo estado. */
    suspend fun toggleFavorite(url: String, title: String, posterUrl: String?): Boolean = db.withTransaction {
        val old = dao.byUrl(url)
        if (old == null) {
            dao.insert(LibraryItem(url = url, title = title.ifBlank { url }, posterUrl = posterUrl, isFavorite = true))
            true
        } else {
            val fav = !old.isFavorite
            dao.update(
                old.copy(
                    isFavorite = fav,
                    title = title.ifBlank { old.title },
                    posterUrl = posterUrl ?: old.posterUrl,
                ),
            )
            fav
        }
    }

    suspend fun isFavorite(url: String): Boolean = dao.byUrl(url)?.isFavorite == true

    /** Guarda el progreso de reproducción nativa (posición y duración en ms). */
    suspend fun saveProgress(
        url: String,
        title: String,
        posterUrl: String?,
        streamUrl: String?,
        positionMs: Long,
        durationMs: Long,
    ) = db.withTransaction {
        val now = System.currentTimeMillis()
        val old = dao.byUrl(url)
        if (old == null) {
            dao.insert(
                LibraryItem(
                    url = url,
                    title = title.ifBlank { url },
                    posterUrl = posterUrl,
                    isContent = true,
                    lastVisitedAt = now,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    streamUrl = streamUrl,
                ),
            )
        } else {
            dao.update(
                old.copy(
                    title = title.ifBlank { old.title },
                    posterUrl = posterUrl ?: old.posterUrl,
                    isContent = true,
                    lastVisitedAt = now,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    streamUrl = streamUrl ?: old.streamUrl,
                ),
            )
        }
    }

    suspend fun get(url: String): LibraryItem? = dao.byUrl(url)
    suspend fun favorites() = dao.favorites()
    suspend fun history() = dao.history(100)
    suspend fun continueWatching() = dao.continueWatching(30)

    suspend fun removeFavorite(url: String) {
        dao.unfavorite(url)
        dao.deleteOrphans()
    }

    suspend fun removeFromHistory(url: String) {
        dao.clearProgress(url)
        dao.deleteOrphans()
    }

    suspend fun clearHistory() {
        dao.clearAllHistory()
        dao.deleteOrphans()
    }

    suspend fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        dao.putSearch(SearchEntry(q, System.currentTimeMillis()))
        dao.trimSearches()
    }

    suspend fun recentSearches(): List<String> = dao.recentSearches(10).map { it.query }
    suspend fun clearSearches() = dao.clearSearches()
}
