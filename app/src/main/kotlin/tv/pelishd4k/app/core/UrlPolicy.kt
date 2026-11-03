package tv.pelishd4k.app.core

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.Locale

/**
 * Validación de URLs y lista de dominios permitidos. Solo usa java.net (sin APIs de Android)
 * para poder probarse con tests JVM.
 */
object UrlPolicy {
    enum class MediaKind { PROGRESSIVE, HLS, DASH }

    private val PROGRESSIVE_EXT = setOf("mp4", "m4v", "webm", "mkv", "mov", "mp3", "m4a", "aac", "ogg")
    private const val MAX_URL_LENGTH = 2048

    /**
     * Normaliza y valida una URL escrita por el usuario o recibida de la página.
     * Solo http/https, con host, sin credenciales embebidas. Si no hay esquema se asume https.
     */
    fun parse(raw: String?): URI? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty() || s.length > MAX_URL_LENGTH) return null
        if (!s.contains("://")) {
            if (s.contains(' ') || !s.contains('.') || s.contains(':')) return null
            s = "https://$s"
        }
        val uri = try {
            URI(s)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        if (host.isEmpty() || uri.userInfo != null) return null
        return uri
    }

    fun hostOf(url: String?): String? = parse(url)?.host?.lowercase(Locale.ROOT)

    /** Un host está permitido si coincide con un dominio de la lista o es un subdominio suyo. */
    fun isHostAllowed(host: String?, allowed: Collection<String>): Boolean {
        val h = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        if (h.isEmpty()) return false
        return allowed.any { d ->
            val dom = d.trim().lowercase(Locale.ROOT).trimStart('.').trimEnd('.')
            dom.isNotEmpty() && (h == dom || h.endsWith(".$dom"))
        }
    }

    /** http → https (los dominios permitidos se cargan siempre por https). */
    fun toHttps(uri: URI): URI =
        if (uri.scheme.equals("http", ignoreCase = true)) {
            URI("https", uri.userInfo, uri.host, if (uri.port == 80) -1 else uri.port, uri.path, uri.query, uri.fragment)
        } else uri

    /** Tipo de contenido multimedia deducido de la extensión de la ruta (ignora query y fragmento). */
    fun mediaKind(url: String?): MediaKind? {
        val path = parse(url)?.path?.lowercase(Locale.ROOT) ?: return null
        val ext = path.substringAfterLast('.', "")
        return when {
            ext == "m3u8" -> MediaKind.HLS
            ext == "mpd" -> MediaKind.DASH
            ext in PROGRESSIVE_EXT -> MediaKind.PROGRESSIVE
            else -> null
        }
    }

    fun isSubtitle(url: String?): String? {
        val path = parse(url)?.path?.lowercase(Locale.ROOT) ?: return null
        return when (path.substringAfterLast('.', "")) {
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            else -> null
        }
    }

    /** Resuelve una ruta relativa ("/peliculas/") contra la URL de inicio. Acepta URLs absolutas. */
    fun resolve(base: String, pathOrUrl: String): String? {
        val b = parse(base) ?: return null
        val p = pathOrUrl.trim()
        if (p.isEmpty()) return b.toString()
        return try {
            val r = if (p.contains("://")) URI(p) else b.resolve(p.let { if (it.startsWith("/") || it.startsWith("?")) it else "/$it" })
            parse(r.toString())?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /** Origen ("https://host[:puerto]") de una URL. */
    fun origin(url: String): String? {
        val u = parse(url) ?: return null
        val port = if (u.port == -1) "" else ":${u.port}"
        return "${u.scheme}://${u.host}$port"
    }

    /** Construye la URL de búsqueda a partir de la plantilla ({home} y {query}). */
    fun buildSearchUrl(template: String, home: String, query: String): String? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val origin = origin(home) ?: return null
        val filled = template.replace("{home}", origin).replace("{query}", q)
        return parse(filled)?.toString()
    }

    /** Lista de dominios escrita por el usuario (separada por comas, espacios o saltos de línea). */
    fun parseDomainList(text: String): List<String> =
        text.split(',', ' ', '\n', ';')
            .map { it.trim().lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").substringBefore('/').trimStart('.') }
            .filter { it.isNotEmpty() && it.length <= 253 && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } && it.contains('.') }
            .distinct()
}
