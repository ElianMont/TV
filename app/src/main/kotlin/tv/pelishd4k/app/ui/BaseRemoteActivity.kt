package tv.pelishd4k.app.ui

import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import tv.pelishd4k.app.remote.RemoteBridge
import tv.pelishd4k.app.remote.RemoteManager
import tv.pelishd4k.app.remote.RemoteTarget
import kotlin.math.abs

/**
 * Base de todas las pantallas: mantiene el servidor remoto vivo mientras sean visibles y
 * traduce las órdenes del iPhone a eventos de teclado normales (así funcionan igual que el mando).
 */
abstract class BaseRemoteActivity : AppCompatActivity(), RemoteTarget {

    /** Nombre de pantalla que ve el iPhone: home | browser | player. */
    protected abstract val screenName: String

    private var accX = 0f
    private var accY = 0f
    private var accScroll = 0f

    override fun onStart() {
        super.onStart()
        RemoteManager.acquire(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        RemoteBridge.attach(this)
    }

    override fun onPause() {
        RemoteBridge.detach(this)
        super.onPause()
    }

    override fun onStop() {
        RemoteManager.release()
        super.onStop()
    }

    protected fun enterImmersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    protected fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ---- RemoteTarget (comportamiento por defecto: foco con D-pad) ----

    override fun remoteKey(code: Int) {
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun remoteBack() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun remoteHome() {
        AppNavigator.goHome(this)
    }

    /** Sin cursor: el touchpad se convierte en pasos de D-pad (cada ~40 px un paso). */
    override fun remoteMove(dx: Float, dy: Float) {
        accX += dx
        accY += dy
        val step = 40f
        if (abs(accX) >= step || abs(accY) >= step) {
            val horizontal = abs(accX) > abs(accY)
            val code = if (horizontal) {
                if (accX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            } else {
                if (accY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            }
            remoteKey(code)
            accX = 0f
            accY = 0f
        }
    }

    override fun remoteTap() = remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)

    override fun remoteScroll(dy: Float) {
        accScroll += dy
        val step = 60f
        while (abs(accScroll) >= step) {
            remoteKey(if (accScroll > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
            accScroll -= if (accScroll > 0) step else -step
        }
    }

    override fun remoteText(text: String): Boolean {
        val et = currentFocus as? EditText ?: return false
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        et.text.replace(start, end, text)
        return true
    }

    override fun remoteSeek(deltaMs: Long): Boolean = false

    override fun remoteState(): Map<String, Any?> = mapOf("screen" to screenName)
}
