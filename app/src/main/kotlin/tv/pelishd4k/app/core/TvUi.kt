package tv.pelishd4k.app.core

import android.view.View
import java.util.Locale

/**
 * Foco de mando a distancia: el borde lo dibuja el fondo (selector) y aquí solo se añade un
 * pequeño escalado barato (propiedades de vista, aceleradas por GPU) con animación corta.
 */
fun View.tvFocusScale(scale: Float = 1.06f) {
    isFocusable = true
    onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
        v.animate().cancel()
        v.animate()
            .scaleX(if (hasFocus) scale else 1f)
            .scaleY(if (hasFocus) scale else 1f)
            .setDuration(120)
            .start()
        v.translationZ = if (hasFocus) 8f else 0f
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
