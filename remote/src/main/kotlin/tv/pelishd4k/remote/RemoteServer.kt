package tv.pelishd4k.remote

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * Servidor HTTP local para el control remoto desde el iPhone (Safari).
 *
 * Seguridad:
 *  - Solo atiende clientes de la red local ([NetPolicy.isPrivateClient]).
 *  - Exige Host con IP literal (anti DNS-rebinding).
 *  - Todas las rutas /api (salvo /api/auth) requieren el header X-Token emitido tras el PIN.
 *  - No emite cabeceras CORS: otras webs no pueden llamar a la API desde el navegador.
 *  - CSP estricta, sin caché y sin sniffing de contenido.
 */
class RemoteServer(
    port: Int,
    private val handler: RemoteHandler,
    private val auth: RemoteAuth,
    private val pageProvider: () -> String?,
) : NanoHTTPD(port) {

    private class HttpStatus(val code: Int, private val text: String) : Response.IStatus {
        override fun getDescription() = "$code $text"
        override fun getRequestStatus() = code
    }

    private companion object {
        val BAD_REQUEST = HttpStatus(400, "Bad Request")
        val UNAUTHORIZED = HttpStatus(401, "Unauthorized")
        val FORBIDDEN = HttpStatus(403, "Forbidden")
        val NOT_FOUND = HttpStatus(404, "Not Found")
        val METHOD_NOT_ALLOWED = HttpStatus(405, "Method Not Allowed")
        val TOO_MANY = HttpStatus(429, "Too Many Requests")
        val TOO_LARGE = HttpStatus(413, "Payload Too Large")
        val UNAVAILABLE = HttpStatus(503, "Service Unavailable")
        val SERVER_ERROR = HttpStatus(500, "Internal Server Error")
        val OK = HttpStatus(200, "OK")

        const val CSP = "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
            "connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
        const val MAX_TEXT = 500
        const val MAX_URL = 2048
        const val MAX_BODY = 16 * 1024

        fun startOnFirstFreePort(ports: IntRange, factory: (Int) -> RemoteServer): RemoteServer {
            var last: IOException? = null
            for (p in ports) {
                val server = factory(p)
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                    return server
                } catch (e: IOException) {
                    last = e
                }
            }
            throw last ?: IOException("Sin puertos libres")
        }
    }

    object Factory {
        /** Arranca el servidor en el primer puerto libre de [ports]. */
        fun start(
            ports: IntRange,
            handler: RemoteHandler,
            auth: RemoteAuth,
            pageProvider: () -> String?,
        ): RemoteServer = startOnFirstFreePort(ports) { RemoteServer(it, handler, auth, pageProvider) }
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            secure(route(session))
        } catch (e: Exception) {
            secure(json(SERVER_ERROR, mapOf("error" to "internal")))
        }
    }

    private fun route(session: IHTTPSession): Response {
        if (!NetPolicy.isPrivateClient(session.remoteIpAddress)) {
            return text(FORBIDDEN, "Solo red local")
        }
        if (!NetPolicy.isAcceptableHost(session.headers["host"])) {
            return text(BAD_REQUEST, "Host no válido")
        }
        val declared = session.headers["content-length"]?.toLongOrNull()
        if (declared != null && declared > MAX_BODY) return text(TOO_LARGE, "Cuerpo demasiado grande")
        val path = session.uri.substringBefore('?')
        val method = session.method
        return when {
            path == "/" || path == "/index.html" -> {
                if (method != Method.GET) return text(METHOD_NOT_ALLOWED, "")
                val page = pageProvider() ?: return text(UNAVAILABLE, "Interfaz no disponible")
                newFixedLengthResponse(OK, "text/html; charset=utf-8", page)
            }
            path.startsWith("/api/") -> api(session, path.removePrefix("/api/"))
            else -> text(NOT_FOUND, "No encontrado")
        }
    }

    private fun readParams(session: IHTTPSession): Map<String, String> {
        if (session.method == Method.POST || session.method == Method.PUT) {
            session.parseBody(HashMap<String, String>())
        }
        val out = HashMap<String, String>()
        for ((k, v) in session.parameters) {
            if (v.isNotEmpty()) out[k] = v[0]
        }
        return out
    }

    private fun api(session: IHTTPSession, name: String): Response {
        val get = session.method == Method.GET
        val post = session.method == Method.POST

        if (name == "auth") {
            if (!post) return text(METHOD_NOT_ALLOWED, "")
            val p = readParams(session)
            val pin = p["pin"].orEmpty().trim()
            if (pin.length != 6 || !pin.all { it.isDigit() }) {
                return json(BAD_REQUEST, mapOf("error" to "PIN de 6 dígitos"))
            }
            return when (val r = auth.login(session.remoteIpAddress ?: "?", pin)) {
                is LoginResult.Success -> json(OK, mapOf("token" to r.token))
                is LoginResult.WrongPin -> json(UNAUTHORIZED, mapOf("error" to "PIN incorrecto"))
                is LoginResult.Locked -> json(TOO_MANY, mapOf("error" to "Demasiados intentos", "retryAfter" to r.retryAfterSec))
            }
        }

        if (!auth.isValid(session.headers["x-token"])) {
            return json(UNAUTHORIZED, mapOf("error" to "No autorizado"))
        }

        if (name == "state") {
            if (!get) return text(METHOD_NOT_ALLOWED, "")
            return json(OK, handler.state())
        }
        if (!post) return text(METHOD_NOT_ALLOWED, "")
        val p = readParams(session)

        return when (name) {
            "key" -> {
                val key = p["key"].orEmpty().uppercase()
                if (key !in RemoteKeys.ALL) return json(BAD_REQUEST, mapOf("error" to "Tecla no válida"))
                ok(handler.key(key))
            }
            "move" -> {
                val dx = num(p["dx"]) ?: return badNumber()
                val dy = num(p["dy"]) ?: return badNumber()
                ok(handler.move(dx.coerceIn(-300f, 300f), dy.coerceIn(-300f, 300f)))
            }
            "tap" -> ok(handler.tap())
            "scroll" -> {
                val dy = num(p["dy"]) ?: return badNumber()
                ok(handler.scroll(dy.coerceIn(-1000f, 1000f)))
            }
            "text" -> {
                val t = p["text"].orEmpty()
                if (t.isEmpty() || t.length > MAX_TEXT) return json(BAD_REQUEST, mapOf("error" to "Texto no válido"))
                ok(handler.text(t))
            }
            "search" -> {
                val q = p["q"].orEmpty().trim()
                if (q.isEmpty() || q.length > 200) return json(BAD_REQUEST, mapOf("error" to "Búsqueda no válida"))
                val err = handler.search(q)
                json(if (err == null) OK else BAD_REQUEST, if (err == null) mapOf("ok" to true) else mapOf("error" to err))
            }
            "open" -> {
                val u = p["url"].orEmpty().trim()
                if (u.isEmpty() || u.length > MAX_URL) return json(BAD_REQUEST, mapOf("error" to "URL no válida"))
                val err = handler.open(u)
                json(if (err == null) OK else BAD_REQUEST, if (err == null) mapOf("ok" to true) else mapOf("error" to err))
            }
            "volume" -> {
                val action = p["action"].orEmpty()
                if (action !in setOf("up", "down", "mute")) return json(BAD_REQUEST, mapOf("error" to "Acción no válida"))
                val v = handler.volume(action)
                json(OK, mapOf("ok" to (v != null), "volume" to v))
            }
            "seek" -> {
                val ms = p["ms"]?.toLongOrNull() ?: return badNumber()
                ok(handler.seek(ms.coerceIn(-600_000L, 600_000L)))
            }
            else -> text(NOT_FOUND, "No encontrado")
        }
    }

    private fun num(s: String?): Float? = s?.toFloatOrNull()?.takeIf { it.isFinite() }
    private fun badNumber() = json(BAD_REQUEST, mapOf("error" to "Número no válido"))
    private fun ok(done: Boolean) = json(OK, mapOf("ok" to done))

    private fun json(status: Response.IStatus, body: Any?): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", MiniJson.stringify(body))

    private fun text(status: Response.IStatus, body: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)

    private fun secure(r: Response): Response {
        // Un rechazo puede dejar el cuerpo de la petición sin leer; cerrar evita que esos bytes
        // se interpreten como la siguiente petición en una conexión keep-alive.
        if (r.status.requestStatus >= 400) r.addHeader("Connection", "close")
        r.addHeader("Cache-Control", "no-store")
        r.addHeader("X-Content-Type-Options", "nosniff")
        r.addHeader("X-Frame-Options", "DENY")
        r.addHeader("Referrer-Policy", "no-referrer")
        r.addHeader("Content-Security-Policy", CSP)
        return r
    }
}
