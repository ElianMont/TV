package tv.pelishd4k.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import tv.pelishd4k.app.player.PlayerActivity
import tv.pelishd4k.app.web.BrowserActivity

/** Datos para abrir el reproductor nativo. */
data class PlayRequest(
    val streamUrl: String,
    val title: String,
    /** Página que originó la reproducción; clave para guardar/reanudar el progreso. */
    val pageUrl: String? = null,
    val posterUrl: String? = null,
    val referer: String? = null,
    val cookies: String? = null,
    val subtitles: List<String> = emptyList(),
)

object AppNavigator {
    private fun Intent.withTask(ctx: Context): Intent =
        if (ctx is Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openBrowser(ctx: Context, url: String) {
        ctx.startActivity(
            Intent(ctx, BrowserActivity::class.java)
                .putExtra(BrowserActivity.EXTRA_URL, url)
                .withTask(ctx),
        )
    }

    fun openPlayer(ctx: Context, r: PlayRequest) {
        ctx.startActivity(
            Intent(ctx, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, r.streamUrl)
                .putExtra(PlayerActivity.EXTRA_TITLE, r.title)
                .putExtra(PlayerActivity.EXTRA_PAGE_URL, r.pageUrl)
                .putExtra(PlayerActivity.EXTRA_POSTER, r.posterUrl)
                .putExtra(PlayerActivity.EXTRA_REFERER, r.referer)
                .putExtra(PlayerActivity.EXTRA_COOKIES, r.cookies)
                .putExtra(PlayerActivity.EXTRA_SUBTITLES, r.subtitles.toTypedArray())
                .withTask(ctx),
        )
    }

    /** Abre un elemento de la biblioteca: reanuda en ExoPlayer si hay flujo guardado; si no, la página. */
    fun openLibraryItem(ctx: Context, item: tv.pelishd4k.app.data.LibraryItem) {
        val stream = item.streamUrl
        if (stream != null && item.positionMs > 0) {
            openPlayer(ctx, PlayRequest(stream, item.title, pageUrl = item.url, posterUrl = item.posterUrl, referer = item.url))
        } else {
            openBrowser(ctx, item.url)
        }
    }

    fun goHome(ctx: Context) {
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .withTask(ctx),
        )
    }
}
