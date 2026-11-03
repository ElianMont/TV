package tv.pelishd4k.app.web

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Puntero virtual para manejar páginas web con las flechas del mando. Dibuja barato y se oculta solo. */
class CursorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var cx = 0f
        private set
    var cy = 0f
        private set

    private var placed = false
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC2D7BFF.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val hide = Runnable { animate().alpha(0f).setDuration(250).start() }

    init {
        isFocusable = false
        isClickable = false
        alpha = 0f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (!placed) {
            cx = w / 2f
            cy = h / 2f
            placed = true
        } else {
            cx = cx.coerceIn(0f, (w - 1).toFloat())
            cy = cy.coerceIn(0f, (h - 1).toFloat())
        }
    }

    /** Mueve el puntero. Devuelve el desplazamiento vertical que no cupo (para desplazar la página). */
    fun moveBy(dx: Float, dy: Float): Float {
        val nx = (cx + dx).coerceIn(0f, (width - 1).toFloat().coerceAtLeast(0f))
        val ny = (cy + dy).coerceIn(0f, (height - 1).toFloat().coerceAtLeast(0f))
        val leftover = dy - (ny - cy)
        cx = nx
        cy = ny
        wake()
        invalidate()
        return leftover
    }

    fun wake() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 1f
        postDelayed(hide, 3500)
    }

    fun hideNow() {
        removeCallbacks(hide)
        animate().cancel()
        alpha = 0f
    }

    override fun onDraw(canvas: Canvas) {
        if (alpha == 0f) return
        canvas.drawCircle(cx, cy, 11f * density, fill)
        canvas.drawCircle(cx, cy, 11f * density, ring)
        canvas.drawCircle(cx, cy, 2.5f * density, dot)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hide)
        super.onDetachedFromWindow()
    }
}
