package tv.pelishd4k.app.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener
import tv.pelishd4k.app.R
import tv.pelishd4k.app.core.UrlPolicy
import tv.pelishd4k.app.databinding.ActivityBrowserBinding
import tv.pelishd4k.app.repo
import tv.pelishd4k.app.settings
import tv.pelishd4k.app.ui.AppNavigator
import tv.pelishd4k.app.ui.BaseRemoteActivity
import tv.pelishd4k.app.ui.PlayRequest
import kotlin.math.abs
import kotlin.math.min

/**
 * Navegador del catálogo web con control por mando: cursor virtual (flechas = mover, OK = clic),
 * barra de herramientas (MENÚ o OK largo), pantalla completa de vídeo HTML5 y reproductor nativo
 * opcional cuando la página expone una fuente directa.
 */
class BrowserActivity : BaseRemoteActivity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"
        private const val LONG_PRESS_MS = 600L
    }

    override val screenName = "browser"

    private lateinit var b: ActivityBrowserBinding
    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var cursorMode = true
    private var toolbarVisible = false

    private var currentUrl = ""
    private var currentTitle = ""
    private var pageMeta: PageMeta? = null

    private var okDownAt = 0L
    private var okLongHandled = false
    private var dialogOpen = false
    private val sessionAllowed = HashSet<String>()
    private val hideMessage = Runnable { b.message.visibility = View.GONE }

    private class PageMeta(val title: String, val poster: String?, val hasPlayer: Boolean, val sources: List<String>, val tracks: List<String>)

    private val density get() = resources.displayMetrics.density

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(b.root)
        keepScreenOn(true)
        cursorMode = settings.cursorMode

        setupToolbar()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                when {
                    toolbarVisible -> hideToolbar()
                    customView != null -> hideCustomView()
                    wv != null && wv.canGoBack() -> wv.goBack()
                    else -> finish()
                }
            }
        })

        if (!createWebView()) return
        val start = intent.getStringExtra(EXTRA_URL) ?: settings.homeUrl
        loadUrl(start)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { loadUrl(it) }
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        destroyWebView()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): Boolean {
        val wv = try {
            WebView(this)
        } catch (e: Exception) {
            // Sin componente WebView (o desactualizado): informar en vez de cerrarse.
            Toast.makeText(this, R.string.webview_missing, Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(Color.BLACK)
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.isVerticalScrollBarEnabled = false
        wv.isHorizontalScrollBarEnabled = false

        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            setSupportZoom(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            if (settings.desktopUserAgent) userAgentString = DESKTOP_UA
        }
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, settings.thirdPartyCookies)
        } catch (_: Exception) {
        }
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        wv.webViewClient = Client()
        wv.webChromeClient = Chrome()
        b.webContainer.addView(wv, 0)
        webView = wv
        return true
    }

    private fun destroyWebView() {
        val wv = webView ?: return
        webView = null
        try {
            wv.stopLoading()
            wv.webChromeClient = null
            wv.webViewClient = WebViewClient()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        } catch (_: Exception) {
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleNavigation(request.url.toString(), request.isForMainFrame)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleNavigation(url, true)

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url.startsWith("http")) {
                currentUrl = url
                pageMeta = null
            }
            b.progress.visibility = View.VISIBLE
            b.progress.progress = 5
        }

        override fun onPageFinished(view: WebView, url: String) {
            b.progress.visibility = View.GONE
            if (!url.startsWith("http")) return
            currentUrl = url
            view.evaluateJavascript(WebScripts.PAGE_INFO) { raw -> onPageInfo(url, raw) }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val desc = error.description?.toString().orEmpty()
            showErrorPage(request.url.toString(), desc)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Nunca se acepta un certificado inválido.
            handler.cancel()
            showMessage(getString(R.string.ssl_error), 5000)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // El proceso del visor murió (memoria): se recrea en vez de cerrar toda la app.
            val url = currentUrl
            handler.post {
                destroyWebView()
                if (createWebView()) {
                    showMessage(getString(R.string.webview_crashed), 3500)
                    if (url.isNotEmpty()) loadUrl(url)
                }
            }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            b.progress.progress = newProgress
            if (newProgress >= 100) b.progress.visibility = View.GONE
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) currentTitle = title
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                callback.onCustomViewHidden()
                return
            }
            customView = view
            customCallback = callback
            b.fullscreenContainer.addView(
                view,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            b.fullscreenContainer.visibility = View.VISIBLE
            webView?.visibility = View.INVISIBLE
            hideToolbar()
            enterImmersive()
        }

        override fun onHideCustomView() = hideCustomView()

        // Sin permisos de cámara/micrófono/ubicación para las páginas.
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun hideCustomView() {
        val v = customView ?: return
        b.fullscreenContainer.removeView(v)
        b.fullscreenContainer.visibility = View.GONE
        customView = null
        webView?.visibility = View.VISIBLE
        try {
            customCallback?.onCustomViewHidden()
        } catch (_: Exception) {
        }
        customCallback = null
        webView?.requestFocus()
        enterImmersive()
    }

    // ------------------------------------------------------------------ navegación y seguridad

    private fun isAllowed(host: String?): Boolean =
        UrlPolicy.isHostAllowed(host, settings.allowedDomains()) || UrlPolicy.isHostAllowed(host, sessionAllowed)

    /** Valida la URL antes de cargarla: solo http(s) y dominios permitidos (con excepción configurable). */
    private fun loadUrl(raw: String) {
        val uri = UrlPolicy.parse(raw)
        if (uri == null) {
            showMessage(getString(R.string.blocked_invalid), 3500)
            return
        }
        if (!isAllowed(uri.host)) {
            askAllow(uri.host ?: "", UrlPolicy.toHttps(uri).toString())
            return
        }
        webView?.loadUrl(UrlPolicy.toHttps(uri).toString())
    }

    private fun handleNavigation(url: String, mainFrame: Boolean): Boolean {
        if (!mainFrame) return false // los iframes (reproductores externos) cargan con normalidad
        val uri = UrlPolicy.parse(url)
        if (uri == null) {
            // intent:, market:, javascript:, file:, etc.
            showMessage(getString(R.string.blocked_invalid), 3000)
            return true
        }
        if (!isAllowed(uri.host)) {
            askAllow(uri.host ?: "", UrlPolicy.toHttps(uri).toString())
            return true
        }
        if (uri.scheme.equals("http", ignoreCase = true)) {
            webView?.loadUrl(UrlPolicy.toHttps(uri).toString())
            return true
        }
        return false
    }

    private fun askAllow(host: String, url: String) {
        if (dialogOpen || isFinishing) return
        dialogOpen = true
        AlertDialog.Builder(this, R.style.TvDialog)
            .setTitle(R.string.blocked_title)
            .setMessage(getString(R.string.blocked_msg_fmt, host))
            .setPositiveButton(R.string.blocked_once) { _, _ ->
                sessionAllowed.add(host)
                webView?.loadUrl(url)
            }
            .setNeutralButton(R.string.blocked_always) { _, _ ->
                settings.allowDomain(host)
                webView?.loadUrl(url)
            }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { dialogOpen = false; enterImmersive() }
            .show()
    }

    private fun showErrorPage(url: String, description: String) {
        val msg = TextUtils.htmlEncode(getString(R.string.load_error_fmt, description.ifBlank { "?" }))
        val link = TextUtils.htmlEncode(url)
        val html = "<html><body style=\"background:#0A0F1E;color:#E8EEFF;font-family:sans-serif;padding:12%;font-size:28px\">" +
            "<h2>PelisHD4K TV</h2><p>$msg</p><p style=\"color:#8A97B8;font-size:20px\">$link</p></body></html>"
        webView?.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    // ------------------------------------------------------------------ metadatos de la página

    private fun onPageInfo(url: String, raw: String?) {
        val meta = try {
            val decoded = JSONTokener(raw ?: return).nextValue() as? String ?: return
            val o = JSONObject(decoded)
            val sources = ArrayList<String>()
            o.optJSONArray("sources")?.let { for (i in 0 until it.length()) sources.add(it.getString(i)) }
            val tracks = ArrayList<String>()
            o.optJSONArray("tracks")?.let { for (i in 0 until it.length()) tracks.add(it.getString(i)) }
            PageMeta(
                title = o.optString("title"),
                poster = o.optString("poster").takeIf { it.startsWith("http") },
                hasPlayer = o.optBoolean("hasPlayer"),
                sources = sources.filter { UrlPolicy.mediaKind(it) != null },
                tracks = tracks.filter { UrlPolicy.isSubtitle(it) != null },
            )
        } catch (e: Exception) {
            return
        }
        pageMeta = meta
        if (meta.title.isNotBlank()) currentTitle = meta.title

        // La portada del sitio no se guarda como "visto"; sí el resto de páginas.
        val path = UrlPolicy.parse(url)?.path.orEmpty()
        if (path.isNotEmpty() && path != "/") {
            lifecycleScope.launch { repo.recordVisit(url, currentTitle, meta.poster, meta.hasPlayer) }
        }
        if (meta.sources.isNotEmpty()) showMessage(getString(R.string.player_offer), 5000)
    }

    // ------------------------------------------------------------------ reproductor nativo

    private fun openNativePlayer() {
        val wv = webView ?: return
        hideToolbar()
        wv.evaluateJavascript(WebScripts.PAGE_INFO) { raw ->
            onPageInfo(currentUrl, raw)
            val meta = pageMeta
            val sources = meta?.sources.orEmpty()
            when {
                sources.isEmpty() -> showMessage(getString(R.string.player_none), 5000)
                sources.size == 1 -> playNative(sources[0])
                else -> {
                    val labels = sources.map { UrlPolicy.hostOf(it).orEmpty() + (UrlPolicy.parse(it)?.path ?: "") }
                    AlertDialog.Builder(this, R.style.TvDialog)
                        .setTitle(R.string.player_pick)
                        .setItems(labels.toTypedArray()) { _, i -> playNative(sources[i]) }
                        .setNegativeButton(R.string.cancel, null)
                        .setOnDismissListener { enterImmersive() }
                        .show()
                }
            }
        }
    }

    private fun playNative(streamUrl: String) {
        val meta = pageMeta
        // Se envían las cookies y el Referer propios de la sesión del usuario en esta página; no se falsea nada.
        val cookies = try {
            CookieManager.getInstance().getCookie(streamUrl)
        } catch (_: Exception) {
            null
        }
        webView?.evaluateJavascript(WebScripts.PAUSE_MEDIA, null)
        AppNavigator.openPlayer(
            this,
            PlayRequest(
                streamUrl = streamUrl,
                title = currentTitle.ifBlank { UrlPolicy.hostOf(streamUrl).orEmpty() },
                pageUrl = currentUrl,
                posterUrl = meta?.poster,
                referer = currentUrl,
                cookies = cookies,
                subtitles = meta?.tracks.orEmpty(),
            ),
        )
    }

    // ------------------------------------------------------------------ barra de herramientas

    private fun setupToolbar() {
        b.tbBack.setOnClickListener { webView?.let { if (it.canGoBack()) it.goBack() }; hideToolbar() }
        b.tbForward.setOnClickListener { webView?.let { if (it.canGoForward()) it.goForward() }; hideToolbar() }
        b.tbReload.setOnClickListener { webView?.reload(); hideToolbar() }
        b.tbHome.setOnClickListener { hideToolbar(); loadUrl(settings.homeUrl) }
        b.tbFavorite.setOnClickListener { toggleFavorite() }
        b.tbPlayer.setOnClickListener { openNativePlayer() }
        b.tbCursor.setOnClickListener {
            cursorMode = !cursorMode
            settings.cursorMode = cursorMode
            updateCursorButton()
            if (!cursorMode) b.cursor.hideNow()
            hideToolbar()
        }
        b.tbExit.setOnClickListener { finish() }
        updateCursorButton()
    }

    private fun updateCursorButton() {
        b.tbCursor.text = getString(R.string.tb_cursor) + ": " + getString(if (cursorMode) R.string.on else R.string.off)
    }

    private fun showToolbar() {
        if (toolbarVisible || customView != null) return
        toolbarVisible = true
        b.cursor.hideNow()
        b.toolbar.visibility = View.VISIBLE
        b.tbFavorite.text = getString(R.string.tb_favorite)
        lifecycleScope.launch {
            val fav = repo.isFavorite(currentUrl)
            b.tbFavorite.text = (if (fav) "★ " else "☆ ") + getString(R.string.tb_favorite)
        }
        b.tbBack.requestFocus()
    }

    private fun hideToolbar() {
        if (!toolbarVisible) return
        toolbarVisible = false
        b.toolbar.visibility = View.GONE
        webView?.requestFocus()
    }

    private fun toggleFavorite() {
        val url = currentUrl
        if (!url.startsWith("http")) return
        lifecycleScope.launch {
            val fav = repo.toggleFavorite(url, currentTitle, pageMeta?.poster)
            showMessage(getString(if (fav) R.string.fav_added else R.string.fav_removed), 2000)
            b.tbFavorite.text = (if (fav) "★ " else "☆ ") + getString(R.string.tb_favorite)
        }
    }

    private fun showMessage(text: String, ms: Long) {
        b.message.text = text
        b.message.visibility = View.VISIBLE
        handler.removeCallbacks(hideMessage)
        handler.postDelayed(hideMessage, ms)
    }

    // ------------------------------------------------------------------ teclas del mando

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (toolbarVisible) {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (e.action == KeyEvent.ACTION_UP) hideToolbar()
                return true
            }
            return super.dispatchKeyEvent(e)
        }
        if (dialogOpen) return super.dispatchKeyEvent(e)
        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.action == KeyEvent.ACTION_UP) showToolbar()
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)

        val pointer = cursorMode || customView != null
        if (!pointer) {
            // Modo foco: el WebView recibe las flechas (navegación espacial de la página).
            if (code == KeyEvent.KEYCODE_DPAD_CENTER && handleLongOk(e)) return true
            return super.dispatchKeyEvent(e)
        }

        when (code) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (e.action == KeyEvent.ACTION_DOWN) moveCursorByKey(code, e.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                handleOk(e)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            -> {
                if (e.action == KeyEvent.ACTION_DOWN) mediaKey(code)
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (e.action == KeyEvent.ACTION_DOWN) webView?.pageUp(false)
                return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (e.action == KeyEvent.ACTION_DOWN) webView?.pageDown(false)
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    /** OK largo = abrir la barra de herramientas (los mandos de Hisense/Fire TV no siempre tienen MENÚ). */
    private fun handleLongOk(e: KeyEvent): Boolean {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) {
                    okDownAt = SystemClock.uptimeMillis()
                    okLongHandled = false
                } else if (!okLongHandled && SystemClock.uptimeMillis() - okDownAt >= LONG_PRESS_MS) {
                    okLongHandled = true
                    showToolbar()
                    return true
                }
            }
            KeyEvent.ACTION_UP -> if (okLongHandled) {
                okLongHandled = false
                return true
            }
        }
        return false
    }

    private fun handleOk(e: KeyEvent) {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) {
                    okDownAt = SystemClock.uptimeMillis()
                    okLongHandled = false
                } else if (!okLongHandled && SystemClock.uptimeMillis() - okDownAt >= LONG_PRESS_MS) {
                    okLongHandled = true
                    showToolbar()
                }
            }
            KeyEvent.ACTION_UP -> {
                if (!okLongHandled) tapAtCursor()
                okLongHandled = false
            }
        }
    }

    private fun moveCursorByKey(code: Int, repeat: Int) {
        val step = 16f * density * (1f + min(repeat, 14) * 0.45f)
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursor(0f, -step)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(0f, step)
            KeyEvent.KEYCODE_DPAD_LEFT -> moveCursor(-step, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> moveCursor(step, 0f)
        }
    }

    private fun moveCursor(dx: Float, dy: Float) {
        val leftoverY = b.cursor.moveBy(dx, dy)
        // Si el puntero ya está en el borde, el resto desplaza la página.
        if (customView == null && abs(leftoverY) >= 1f) webView?.scrollBy(0, (leftoverY * 1.6f).toInt())
    }

    private fun tapAtCursor() {
        val target: View = customView ?: webView ?: return
        val x = b.cursor.cx
        val y = b.cursor.cy
        b.cursor.wake()
        target.requestFocus()
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        target.dispatchTouchEvent(down)
        down.recycle()
        handler.postDelayed({
            val up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
                .apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            target.dispatchTouchEvent(up)
            up.recycle()
        }, 45)
    }

    private fun mediaKey(code: Int) {
        val wv = webView ?: return
        when (code) {
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                wv.evaluateJavascript(WebScripts.TOGGLE_MEDIA, null)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT ->
                wv.evaluateJavascript(WebScripts.seekMedia(settings.seekStepSec), null)
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                wv.evaluateJavascript(WebScripts.seekMedia(-settings.seekStepSec), null)
        }
    }

    // ------------------------------------------------------------------ control remoto (iPhone)

    override fun remoteMove(dx: Float, dy: Float) {
        if (toolbarVisible || dialogOpen || !(cursorMode || customView != null)) {
            super.remoteMove(dx, dy)
        } else {
            moveCursor(dx * 1.4f, dy * 1.4f)
        }
    }

    override fun remoteTap() {
        if (toolbarVisible || dialogOpen || !(cursorMode || customView != null)) super.remoteTap() else tapAtCursor()
    }

    override fun remoteScroll(dy: Float) {
        if (toolbarVisible || dialogOpen) super.remoteScroll(dy) else webView?.scrollBy(0, (dy * 2f).toInt())
    }

    override fun remoteText(text: String): Boolean {
        if (toolbarVisible || dialogOpen) return super.remoteText(text)
        webView?.evaluateJavascript(WebScripts.insertText(text), null)
        return true
    }

    override fun remoteSeek(deltaMs: Long): Boolean {
        val wv = webView ?: return false
        wv.evaluateJavascript(WebScripts.seekMedia((deltaMs / 1000).toInt()), null)
        return true
    }

    override fun remoteState(): Map<String, Any?> = mapOf(
        "screen" to screenName,
        "title" to currentTitle,
        "url" to (UrlPolicy.hostOf(currentUrl).orEmpty() + (UrlPolicy.parse(currentUrl)?.path.orEmpty())),
        "cursor" to cursorMode,
        "fullscreen" to (customView != null),
        "menu" to toolbarVisible,
    )
}
