package tv.pelishd4k.app.remote

import android.content.Context
import android.util.Log
import tv.pelishd4k.app.App
import tv.pelishd4k.app.core.AppSettings
import tv.pelishd4k.remote.RemoteAuth
import tv.pelishd4k.remote.RemoteServer

/**
 * Ciclo de vida del servidor remoto. Está activo solo mientras alguna pantalla de la app es
 * visible (conteo de referencias), así no queda un puerto abierto con la app en segundo plano.
 */
object RemoteManager {
    private const val TAG = "RemoteManager"

    private var refs = 0
    private var server: RemoteServer? = null
    private var page: String? = null

    @Volatile
    var port: Int = 0
        private set

    val auth: RemoteAuth by lazy { RemoteAuth(PrefsTokenStore(App.instance)) }

    @Synchronized
    fun acquire(context: Context) {
        refs++
        if (refs == 1) startLocked(context.applicationContext)
    }

    @Synchronized
    fun release() {
        refs = (refs - 1).coerceAtLeast(0)
        if (refs == 0) stopLocked()
    }

    /** Aplica un cambio del ajuste "servidor activado" sin esperar a la siguiente pantalla. */
    @Synchronized
    fun refresh(context: Context) {
        if (refs <= 0) return
        stopLocked()
        startLocked(context.applicationContext)
    }

    val isRunning: Boolean @Synchronized get() = server != null

    /** Dirección que debe abrir el iPhone, p. ej. http://192.168.1.50:8765 */
    fun address(): String? {
        val ip = NetUtils.localIpv4() ?: return null
        val p = port
        return if (isRunning && p > 0) "http://$ip:$p" else null
    }

    private fun startLocked(context: Context) {
        if (server != null || !App.instance.settings.remoteEnabled) return
        try {
            val s = RemoteServer.Factory.start(
                AppSettings.DEFAULT_PORT_START..(AppSettings.DEFAULT_PORT_START + 10),
                RemoteBridge,
                auth,
            ) { loadPage(context) }
            server = s
            port = s.listeningPort
            Log.i(TAG, "Servidor remoto en el puerto $port")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar el servidor remoto", e)
        }
    }

    private fun stopLocked() {
        server?.stop()
        server = null
        port = 0
    }

    private fun loadPage(context: Context): String? {
        page?.let { return it }
        return try {
            context.assets.open("remote/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }.also { page = it }
        } catch (e: Exception) {
            Log.e(TAG, "Falta remote/index.html", e)
            null
        }
    }
}
