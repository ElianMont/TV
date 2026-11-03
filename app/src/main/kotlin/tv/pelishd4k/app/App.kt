package tv.pelishd4k.app

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import tv.pelishd4k.app.core.AppSettings
import tv.pelishd4k.app.data.AppDatabase
import tv.pelishd4k.app.data.Repository

class App : Application(), ImageLoaderFactory {

    lateinit var settings: AppSettings
        private set
    lateinit var repo: Repository
        private set

    /** Ámbito para trabajo que debe sobrevivir a las pantallas (p. ej. guardar la posición al salir). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = AppSettings(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "pelishd4k.db")
            .fallbackToDestructiveMigration()
            .build()
        repo = Repository(db)
    }

    /** Caché de imágenes contenida: RGB_565 y 15 % de memoria para equipos de 1–2 GB. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(64L * 1024 * 1024).build() }
        .bitmapConfig(Bitmap.Config.RGB_565)
        .crossfade(150)
        .respectCacheHeaders(false)
        .build()

    companion object {
        lateinit var instance: App
            private set
    }
}

val settings: AppSettings get() = App.instance.settings
val repo: Repository get() = App.instance.repo
