package tv.pelishd4k.app.core

import android.content.Context
import android.content.SharedPreferences

/** Ajustes de la aplicación (SharedPreferences: lectura síncrona, necesaria al configurar el WebView). */
class AppSettings(context: Context) {
    companion object {
        const val DEFAULT_HOME = "https://pelishd4k.com"
        // Rutas SUPUESTAS: no se pudo verificar la estructura del sitio. Configurables en Ajustes.
        const val DEFAULT_PATH_MOVIES = "/peliculas/"
        const val DEFAULT_PATH_SERIES = "/series/"
        const val DEFAULT_PATH_TRENDING = "/tendencias/"
        const val DEFAULT_SEARCH_TEMPLATE = "{home}/?s={query}"
        const val DEFAULT_PORT_START = 8765
    }

    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()
    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun put(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var homeUrl: String
        get() = UrlPolicy.parse(str("home", DEFAULT_HOME))?.toString() ?: DEFAULT_HOME
        set(v) = put("home", v)

    var pathMovies: String
        get() = str("path_movies", DEFAULT_PATH_MOVIES)
        set(v) = put("path_movies", v)

    var pathSeries: String
        get() = str("path_series", DEFAULT_PATH_SERIES)
        set(v) = put("path_series", v)

    var pathTrending: String
        get() = str("path_trending", DEFAULT_PATH_TRENDING)
        set(v) = put("path_trending", v)

    var searchTemplate: String
        get() = str("search_tpl", DEFAULT_SEARCH_TEMPLATE)
        set(v) = put("search_tpl", v)

    /** Dominios extra permitidos (el del inicio siempre lo está). */
    var extraDomains: String
        get() = str("domains", "")
        set(v) = put("domains", v)

    /** Dominios permitidos efectivos: el host de inicio + los configurados. */
    fun allowedDomains(): Set<String> {
        val set = LinkedHashSet<String>()
        UrlPolicy.hostOf(homeUrl)?.let { set.add(it.removePrefix("www.")) }
        set.addAll(UrlPolicy.parseDomainList(extraDomains))
        return set
    }

    fun allowDomain(domain: String) {
        val current = UrlPolicy.parseDomainList(extraDomains).toMutableList()
        val d = domain.lowercase().removePrefix("www.")
        if (d !in current && d !in allowedDomains()) current.add(d)
        extraDomains = current.joinToString(",")
    }

    var cursorMode: Boolean
        get() = bool("cursor_mode", true)
        set(v) = put("cursor_mode", v)

    var desktopUserAgent: Boolean
        get() = bool("desktop_ua", false)
        set(v) = put("desktop_ua", v)

    var thirdPartyCookies: Boolean
        get() = bool("third_party_cookies", true)
        set(v) = put("third_party_cookies", v)

    /** Altura máxima de vídeo en ExoPlayer; 0 = automática. */
    var maxVideoHeight: Int
        get() = int("max_height", 0)
        set(v) = put("max_height", v)

    var seekStepSec: Int
        get() = int("seek_step", 10)
        set(v) = put("seek_step", v)

    /** "" = idioma del sistema. */
    var audioLang: String
        get() = str("audio_lang", "es")
        set(v) = put("audio_lang", v)

    /** "" = subtítulos desactivados por defecto. */
    var subtitleLang: String
        get() = str("subtitle_lang", "")
        set(v) = put("subtitle_lang", v)

    var autoResume: Boolean
        get() = bool("auto_resume", true)
        set(v) = put("auto_resume", v)

    var remoteEnabled: Boolean
        get() = bool("remote_enabled", true)
        set(v) = put("remote_enabled", v)

    var autostart: Boolean
        get() = bool("autostart", false)
        set(v) = put("autostart", v)

    fun sectionUrl(section: Section): String {
        val path = when (section) {
            Section.HOME -> ""
            Section.MOVIES -> pathMovies
            Section.SERIES -> pathSeries
            Section.TRENDING -> pathTrending
        }
        return UrlPolicy.resolve(homeUrl, path) ?: homeUrl
    }

    fun searchUrl(query: String): String? = UrlPolicy.buildSearchUrl(searchTemplate, homeUrl, query)

    enum class Section { HOME, MOVIES, SERIES, TRENDING }
}
