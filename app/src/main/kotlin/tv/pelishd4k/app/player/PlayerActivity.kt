package tv.pelishd4k.app.player

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import kotlinx.coroutines.launch
import tv.pelishd4k.app.App
import tv.pelishd4k.app.R
import tv.pelishd4k.app.core.UrlPolicy
import tv.pelishd4k.app.core.formatTime
import tv.pelishd4k.app.databinding.ActivityPlayerBinding
import tv.pelishd4k.app.repo
import tv.pelishd4k.app.settings
import tv.pelishd4k.app.ui.AppNavigator
import tv.pelishd4k.app.ui.BaseRemoteActivity
import kotlin.math.min

/**
 * Reproductor nativo (Media3 ExoPlayer): MP4, HLS y DASH desde fuentes accesibles que el usuario
 * (o la página, de forma visible) proporciona. No hay DRM, ni extracción de enlaces, ni
 * evasión de autenticación: si la fuente rechaza la petición se informa y se ofrece la página web.
 */
class PlayerActivity : BaseRemoteActivity() {

    companion object {
        const val EXTRA_URL = "stream_url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_POSTER = "poster"
        const val EXTRA_REFERER = "referer"
        const val EXTRA_COOKIES = "cookies"
        const val EXTRA_SUBTITLES = "subtitles"
        private const val SAVE_INTERVAL_MS = 10_000L
    }

    override val screenName = "player"

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())

    private var streamUrl = ""
    private var title = ""
    private var pageUrl: String? = null
    private var posterUrl: String? = null
    private var referer: String? = null
    private var cookies: String? = null
    private var subtitles: List<String> = emptyList()

    private var resumeMs = C.TIME_UNSET
    private var resumeLoaded = false
    private var resumeAnnounced = false
    private var dialogOpen = false
    private var errorShown = false
    private var starting = false

    private val progressKey get() = pageUrl ?: streamUrl
    private val saveLoop = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }
    private val hideHint = Runnable { b.hint.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = UrlPolicy.parse(intent.getStringExtra(EXTRA_URL))
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) {
            Toast.makeText(this, "Fuente no válida: solo se admiten direcciones HTTPS.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        streamUrl = uri.toString()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { uri.host.orEmpty() }
        pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
        posterUrl = intent.getStringExtra(EXTRA_POSTER)
        referer = intent.getStringExtra(EXTRA_REFERER)
        cookies = intent.getStringExtra(EXTRA_COOKIES)
            ?: try {
                CookieManager.getInstance().getCookie(streamUrl)
            } catch (_: Exception) {
                null
            }
        subtitles = intent.getStringArrayExtra(EXTRA_SUBTITLES)?.toList().orEmpty()

        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        keepScreenOn(true)
        b.title.text = title

        b.playerView.setShowSubtitleButton(false)
        b.playerView.setShowNextButton(false)
        b.playerView.setShowPreviousButton(false)
        b.playerView.setShowFastForwardButton(true)
        b.playerView.setShowRewindButton(true)
        b.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility -> b.topBar.visibility = visibility },
        )

        b.btnAudio.setOnClickListener { showTracks(C.TRACK_TYPE_AUDIO, R.string.audio, allowDisable = false, adaptive = false) }
        b.btnSubs.setOnClickListener { showTracks(C.TRACK_TYPE_TEXT, R.string.subtitles, allowDisable = true, adaptive = false) }
        b.btnQuality.setOnClickListener { showTracks(C.TRACK_TYPE_VIDEO, R.string.quality, allowDisable = false, adaptive = true) }
        b.btnSpeed.setOnClickListener { showSpeed() }
    }

    override fun onStart() {
        super.onStart()
        if (!::b.isInitialized || player != null || starting) return
        starting = true
        lifecycleScope.launch {
            if (!resumeLoaded) {
                resumeLoaded = true
                if (settings.autoResume) {
                    val saved = repo.get(progressKey)
                    if (saved != null && saved.positionMs > 5_000 && (saved.durationMs <= 0 || saved.positionMs < saved.durationMs * 0.95)) {
                        resumeMs = saved.positionMs
                    }
                }
            }
            starting = false
            if (player == null && !isFinishing) buildPlayer()
        }
    }

    override fun onStop() {
        if (player != null) {
            resumeMs = player?.currentPosition ?: resumeMs
            savePosition()
        }
        releasePlayer()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ reproductor

    private fun userAgent(): String = try {
        WebSettings.getDefaultUserAgent(this)
    } catch (_: Exception) {
        "PelisHD4KTV/1.0 (Linux; Android ${android.os.Build.VERSION.RELEASE})"
    }

    private fun buildPlayer() {
        val s = settings

        val trackSelector = DefaultTrackSelector(this).also { ts ->
            val pb = ts.buildUponParameters()
                .setPreferredAudioLanguage(s.audioLang.ifEmpty { null })
            if (s.subtitleLang.isEmpty()) {
                pb.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                pb.setPreferredTextLanguage(s.subtitleLang)
            }
            if (s.maxVideoHeight > 0) pb.setMaxVideoSize(Int.MAX_VALUE, s.maxVideoHeight)
            ts.parameters = pb.build()
        }

        // Búferes moderados: equipos de 1–2 GB (Fire TV Cube 1.ª gen., Hisense A4).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 40_000, 2_500, 5_000)
            .build()

        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        val headers = HashMap<String, String>()
        referer?.let { headers["Referer"] = it }
        cookies?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent())
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(this, http)

        val step = s.seekStepSec * 1000L
        val p = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(step)
            .setSeekForwardIncrementMs(step)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .build()

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = showError(error)

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    b.qualityLabel.text = "${videoSize.height}p"
                    b.qualityLabel.visibility = View.VISIBLE
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !resumeAnnounced) {
                    resumeAnnounced = true
                    if (resumeMs != C.TIME_UNSET && resumeMs > 0) showHint(getString(R.string.resume_fmt, formatTime(resumeMs)))
                }
                if (state == Player.STATE_ENDED) {
                    savePosition()
                    finish()
                }
            }
        })

        player = p
        b.playerView.player = p
        session = MediaSession.Builder(this, p).build()

        p.setMediaItem(buildMediaItem(), if (resumeMs != C.TIME_UNSET) resumeMs else C.TIME_UNSET)
        p.prepare()
        p.playWhenReady = true
        handler.postDelayed(saveLoop, SAVE_INTERVAL_MS)
    }

    private fun buildMediaItem(): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setMediaId(streamUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        when (UrlPolicy.mediaKind(streamUrl)) {
            UrlPolicy.MediaKind.HLS -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            UrlPolicy.MediaKind.DASH -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            else -> Unit
        }
        val subs = subtitles.mapIndexedNotNull { i, u ->
            val mime = UrlPolicy.isSubtitle(u) ?: return@mapIndexedNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(u))
                .setMimeType(mime)
                .setLabel(getString(R.string.subtitles) + " " + (i + 1))
                .build()
        }
        if (subs.isNotEmpty()) builder.setSubtitleConfigurations(subs)
        return builder.build()
    }

    private fun releasePlayer() {
        handler.removeCallbacks(saveLoop)
        session?.release()
        session = null
        b.playerView.player = null
        player?.release()
        player = null
    }

    private fun savePosition() {
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        var pos = p.currentPosition.coerceAtLeast(0L)
        if (p.playbackState == Player.STATE_ENDED && dur > 0) pos = dur
        val key = progressKey
        val t = title
        val poster = posterUrl
        val stream = streamUrl
        App.instance.appScope.launch { repo.saveProgress(key, t, poster, stream, pos, dur) }
    }

    // ------------------------------------------------------------------ errores

    private fun showError(error: PlaybackException) {
        if (errorShown || isFinishing) return
        errorShown = true
        val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        val msg = if (code == 401 || code == 403) {
            getString(R.string.player_error_auth)
        } else {
            getString(R.string.player_error_generic, error.errorCodeName + (code?.let { " (HTTP $it)" } ?: ""))
        }
        val builder = AlertDialog.Builder(this, R.style.TvDialog)
            .setTitle(R.string.player_error_title)
            .setMessage(msg)
            .setPositiveButton(R.string.retry) { _, _ ->
                errorShown = false
                player?.let { it.prepare(); it.playWhenReady = true }
            }
            .setNegativeButton(R.string.close) { _, _ -> finish() }
            .setOnCancelListener { finish() }
        pageUrl?.let { page ->
            builder.setNeutralButton(R.string.open_in_web) { _, _ ->
                AppNavigator.openBrowser(this, page)
                finish()
            }
        }
        builder.show()
    }

    // ------------------------------------------------------------------ pistas de audio / subtítulos / calidad

    private fun showTracks(type: Int, titleRes: Int, allowDisable: Boolean, adaptive: Boolean) {
        val p = player ?: return
        if (!p.currentTracks.isTypeSupported(type) || p.currentTracks.groups.none { it.type == type }) {
            showHint(getString(titleRes) + ": no disponible")
            return
        }
        dialogOpen = true
        TrackSelectionDialogBuilder(this, getString(titleRes), p, type)
            .setTheme(R.style.TvDialog)
            .setAllowAdaptiveSelections(adaptive)
            .setShowDisableOption(allowDisable)
            .build()
            .apply {
                setOnDismissListener {
                    dialogOpen = false
                    enterImmersive()
                }
            }
            .show()
    }

    private fun showSpeed() {
        val p = player ?: return
        val speeds = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = speeds.map { (if (it == 1f) "Normal" else "${it}x") }.toTypedArray()
        val current = speeds.indexOfFirst { it == p.playbackParameters.speed }.coerceAtLeast(1)
        dialogOpen = true
        AlertDialog.Builder(this, R.style.TvDialog)
            .setTitle(R.string.speed)
            .setSingleChoiceItems(labels, current) { d, which ->
                p.setPlaybackSpeed(speeds[which])
                d.dismiss()
            }
            .setOnDismissListener { dialogOpen = false; enterImmersive() }
            .show()
    }

    private fun showQuickMenu() {
        dialogOpen = true
        val items = arrayOf(getString(R.string.audio), getString(R.string.subtitles), getString(R.string.quality), getString(R.string.speed))
        AlertDialog.Builder(this, R.style.TvDialog)
            .setItems(items) { _, which ->
                dialogOpen = false
                when (which) {
                    0 -> showTracks(C.TRACK_TYPE_AUDIO, R.string.audio, allowDisable = false, adaptive = false)
                    1 -> showTracks(C.TRACK_TYPE_TEXT, R.string.subtitles, allowDisable = true, adaptive = false)
                    2 -> showTracks(C.TRACK_TYPE_VIDEO, R.string.quality, allowDisable = false, adaptive = true)
                    else -> showSpeed()
                }
            }
            .setOnDismissListener { dialogOpen = false; enterImmersive() }
            .show()
    }

    // ------------------------------------------------------------------ teclas

    private fun showHint(text: String) {
        b.hint.text = text
        b.hint.visibility = View.VISIBLE
        handler.removeCallbacks(hideHint)
        handler.postDelayed(hideHint, 1500)
    }

    private fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, dur))
        val sec = deltaMs / 1000
        showHint((if (sec >= 0) "+" else "") + sec + " s  ·  " + formatTime(p.currentPosition))
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (dialogOpen || errorShown) return super.dispatchKeyEvent(e)
        if (e.action == KeyEvent.ACTION_DOWN) {
            val p = player
            val visible = b.playerView.isControllerFullyVisible
            val step = settings.seekStepSec * 1000L
            when (e.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { p?.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { p?.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> { seekBy(step * 2); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { seekBy(-step * 2); return true }
                KeyEvent.KEYCODE_MENU -> { showQuickMenu(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> if (!visible) {
                    val accel = 1 + min(e.repeatCount / 4, 5)
                    seekBy((if (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -step else step) * accel)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (!visible) {
                    togglePlay()
                    b.playerView.showController()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (!visible) {
                    b.playerView.showController()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ------------------------------------------------------------------ control remoto (iPhone)

    override fun remoteSeek(deltaMs: Long): Boolean {
        if (player == null) return false
        seekBy(deltaMs)
        return true
    }

    override fun remoteState(): Map<String, Any?> {
        val p = player
        return mapOf(
            "screen" to screenName,
            "title" to title,
            "playing" to (p?.isPlaying == true),
            "positionMs" to (p?.currentPosition ?: 0L),
            "durationMs" to (p?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L),
            "quality" to (p?.videoSize?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: ""),
        )
    }
}
