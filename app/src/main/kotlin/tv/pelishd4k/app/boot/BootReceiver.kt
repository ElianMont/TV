package tv.pelishd4k.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import tv.pelishd4k.app.App
import tv.pelishd4k.app.ui.MainActivity

/**
 * Inicio automático opcional (desactivado por defecto).
 * Android 10+ no permite abrir pantallas desde segundo plano; en Android TV 9 (API 28) y
 * Fire OS 6 (API 25) sí funciona, por eso se limita a esas versiones.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return
        if (!App.instance.settings.autostart) return
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
