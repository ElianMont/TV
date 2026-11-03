package tv.pelishd4k.app.remote

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import kotlinx.coroutines.launch
import tv.pelishd4k.app.App
import tv.pelishd4k.app.core.UrlPolicy
import tv.pelishd4k.app.ui.AppNavigator
import tv.pelishd4k.app.ui.PlayRequest
import tv.pelishd4k.remote.RemoteHandler
import java.lang.ref.WeakReference
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Puente entre el servidor HTTP (hilos de red) y la pantalla visible (hilo principal).
 * Las pantallas se registran en onResume y se quitan en onPause.
 */
object RemoteBridge : RemoteHandler {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var target: WeakReference<RemoteTarget>? = null

    fun attach(t: RemoteTarget) {
        target = WeakReference(t)
    }

    fun detach(t: RemoteTarget) {
        if (target?.get() === t) target = null
    }

    private inline fun onTarget(crossinline block: (RemoteTarget) -> Unit): Boolean {
        val t = target?.get() ?: return false
        main.post { block(t) }
        return true
    }

    private fun keyCode(name: String): Int? = when (name) {
        "UP" -> KeyEvent.KEYCODE_DPAD_UP
        "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
        "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
        "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "OK" -> KeyEvent.KEYCODE_DPAD_CENTER
        "ENTER" -> KeyEvent.KEYCODE_ENTER
        "MENU" -> KeyEvent.KEYCODE_MENU
        "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
        "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "FF" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        "REW" -> KeyEvent.KEYCODE_MEDIA_REWIND
        "STOP" -> KeyEvent.KEYCODE_MEDIA_STOP
        "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
        "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
        "BACKSPACE" -> KeyEvent.KEYCODE_DEL
        else -> null
    }

    override fun key(name: String): Boolean = when (name) {
        "BACK" -> onTarget { it.remoteBack() }
        "HOME" -> onTarget { it.remoteHome() }
        else -> keyCode(name)?.let { code -> onTarget { it.remoteKey(code) } } ?: false
    }

    override fun move(dx: Float, dy: Float) = onTarget { it.remoteMove(dx, dy) }
    override fun tap() = onTarget { it.remoteTap() }
    override fun scroll(dy: Float) = onTarget { it.remoteScroll(dy) }

    override fun text(text: String): Boolean {
        val t = target?.get() ?: return false
        val task = FutureTask { t.remoteText(text) }
        main.post(task)
        return try {
            task.get(1, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    override fun seek(deltaMs: Long): Boolean {
        val t = target?.get() ?: return false
        val task = FutureTask { t.remoteSeek(deltaMs) }
        main.post(task)
        return try {
            task.get(1, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    override fun search(query: String): String? {
        val url = App.instance.settings.searchUrl(query) ?: return "No se pudo construir la búsqueda; revisa la plantilla en Configuración."
        main.post {
            App.instance.appScope.launch { App.instance.repo.addSearch(query) }
            AppNavigator.openBrowser(App.instance, url)
        }
        return null
    }

    override fun open(url: String): String? {
        val settings = App.instance.settings
        val uri = UrlPolicy.parse(url) ?: return "URL no válida"
        if (uri.scheme.equals("http", ignoreCase = true)) return "Solo se admite HTTPS"
        val kind = UrlPolicy.mediaKind(uri.toString())
        if (kind != null) {
            main.post {
                AppNavigator.openPlayer(App.instance, PlayRequest(streamUrl = uri.toString(), title = uri.host ?: "Vídeo"))
            }
            return null
        }
        if (!UrlPolicy.isHostAllowed(uri.host, settings.allowedDomains())) {
            return "Dominio no permitido. Añádelo en Configuración → Dominios permitidos."
        }
        main.post { AppNavigator.openBrowser(App.instance, uri.toString()) }
        return null
    }

    private fun audio() = App.instance.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun volume(action: String): Int? {
        return try {
            val am = audio()
            val dir = when (action) {
                "up" -> AudioManager.ADJUST_RAISE
                "down" -> AudioManager.ADJUST_LOWER
                else -> AudioManager.ADJUST_TOGGLE_MUTE
            }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        } catch (e: Exception) {
            null
        }
    }

    override fun state(): Map<String, Any?> {
        val t = target?.get() ?: return mapOf("screen" to "none")
        val task = FutureTask { t.remoteState() }
        main.post(task)
        return try {
            task.get(500, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            mapOf("screen" to "busy")
        }
    }
}
