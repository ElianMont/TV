package tv.pelishd4k.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class RemoteServerTest {

    private class FakeHandler : RemoteHandler {
        val keys = ArrayList<String>()
        var lastMove: Pair<Float, Float>? = null
        var lastText: String? = null
        var lastSearch: String? = null
        var lastOpen: String? = null
        override fun key(name: String): Boolean { keys.add(name); return true }
        override fun move(dx: Float, dy: Float): Boolean { lastMove = dx to dy; return true }
        override fun tap() = true
        override fun scroll(dy: Float) = true
        override fun text(text: String): Boolean { lastText = text; return true }
        override fun search(query: String): String? { lastSearch = query; return null }
        override fun open(url: String): String? { lastOpen = url; return if (url.startsWith("https://")) null else "Solo https" }
        override fun volume(action: String): Int? = 42
        override fun seek(deltaMs: Long) = true
        override fun state(): Map<String, Any?> = mapOf("screen" to "home", "playing" to false)
    }

    private lateinit var server: RemoteServer
    private lateinit var auth: RemoteAuth
    private val handler = FakeHandler()
    private var port = 0

    @Before
    fun setUp() {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true")
        auth = RemoteAuth()
        server = RemoteServer.Factory.start(18700..18790, handler, auth) { "<html>remote</html>" }
        port = server.listeningPort
    }

    @After
    fun tearDown() = server.stop()

    private class Resp(val code: Int, val body: String, val headers: Map<String, List<String>>)

    private fun call(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        host: String? = null,
    ): Resp {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 3000
        c.readTimeout = 3000
        if (token != null) c.setRequestProperty("X-Token", token)
        if (host != null) c.setRequestProperty("Host", host)
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code >= 400) c.errorStream else c.inputStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        return Resp(code, text, c.headerFields.filterKeys { it != null })
    }

    private fun login(pin: String = auth.pin): String {
        val r = call("POST", "/api/auth", "pin=$pin")
        assertEquals(200, r.code)
        return Regex("\"token\":\"([0-9a-f]+)\"").find(r.body)!!.groupValues[1]
    }

    @Test
    fun servesPageWithSecurityHeaders() {
        val r = call("GET", "/")
        assertEquals(200, r.code)
        assertTrue(r.body.contains("remote"))
        assertNotNull(r.headers["Content-Security-Policy"])
        assertEquals(listOf("no-store"), r.headers["Cache-Control"])
        assertNull("No debe haber CORS", r.headers["Access-Control-Allow-Origin"])
    }

    @Test
    fun apiRequiresToken() {
        assertEquals(401, call("POST", "/api/key", "key=UP").code)
        assertEquals(401, call("GET", "/api/state").code)
        assertEquals(401, call("POST", "/api/key", "key=UP", token = "deadbeef").code)
        assertTrue(handler.keys.isEmpty())
    }

    @Test
    fun wrongPinRejectedAndCorrectPinIssuesToken() {
        val wrong = if (auth.pin == "000000") "111111" else "000000"
        assertEquals(401, call("POST", "/api/auth", "pin=$wrong").code)
        val token = login()
        assertEquals(48, token.length)
        assertEquals(200, call("POST", "/api/key", "key=UP", token = token).code)
        assertEquals(listOf("UP"), handler.keys)
    }

    @Test
    fun malformedPinIsRejected() {
        assertEquals(400, call("POST", "/api/auth", "pin=12ab").code)
        assertEquals(400, call("POST", "/api/auth", "pin=").code)
    }

    @Test
    fun bruteForceIsLockedOut() {
        val wrong = if (auth.pin == "000000") "111111" else "000000"
        var last = 0
        repeat(RemoteAuth.MAX_FAILURES_BEFORE_LOCK) { last = call("POST", "/api/auth", "pin=$wrong").code }
        assertEquals(401, last)
        val locked = call("POST", "/api/auth", "pin=${auth.pin}")
        assertEquals("El PIN correcto tampoco entra mientras está bloqueado", 429, locked.code)
    }

    @Test
    fun invalidKeyAndNumbersRejected() {
        val t = login()
        assertEquals(400, call("POST", "/api/key", "key=FORMAT_DISK", token = t).code)
        assertEquals(400, call("POST", "/api/move", "dx=abc&dy=1", token = t).code)
        assertEquals(400, call("POST", "/api/move", "dx=NaN&dy=1", token = t).code)
    }

    @Test
    fun moveIsClamped() {
        val t = login()
        assertEquals(200, call("POST", "/api/move", "dx=99999&dy=-99999", token = t).code)
        assertEquals(300f to -300f, handler.lastMove)
    }

    @Test
    fun textSearchOpenVolumeAndState() {
        val t = login()
        assertEquals(200, call("POST", "/api/text", "text=Hola+%C3%B1", token = t).code)
        assertEquals("Hola ñ", handler.lastText)
        assertEquals(400, call("POST", "/api/text", "text=" + "a".repeat(501), token = t).code)
        assertEquals(200, call("POST", "/api/search", "q=matrix", token = t).code)
        assertEquals("matrix", handler.lastSearch)
        assertEquals(200, call("POST", "/api/open", "url=https%3A%2F%2Fexample.com%2Fa.mp4", token = t).code)
        assertEquals(400, call("POST", "/api/open", "url=http%3A%2F%2Fexample.com", token = t).code)
        val v = call("POST", "/api/volume", "action=up", token = t)
        assertTrue(v.body.contains("\"volume\":42"))
        assertEquals(400, call("POST", "/api/volume", "action=explode", token = t).code)
        val s = call("GET", "/api/state", token = t)
        assertEquals(200, s.code)
        assertTrue(s.body.contains("\"screen\":\"home\""))
    }

    @Test
    fun rebindingHostIsRejected() {
        assertEquals(400, call("GET", "/", host = "evil.example.com").code)
        assertEquals(200, call("GET", "/", host = "192.168.1.50:8765").code)
    }

    @Test
    fun unknownRoutesAndMethods() {
        val t = login()
        assertEquals(404, call("GET", "/etc/passwd").code)
        assertEquals(404, call("POST", "/api/nope", "a=b", token = t).code)
        assertEquals(405, call("GET", "/api/key", token = t).code)
        assertFalse(call("POST", "/", "x=1").code == 200)
    }

    @Test
    fun oversizedBodyIsRejected() {
        assertEquals(413, call("POST", "/api/auth", "pin=" + "1".repeat(20_000)).code)
    }

    @Test
    fun regeneratingPinRevokesTokens() {
        val t = login()
        assertEquals(200, call("GET", "/api/state", token = t).code)
        auth.regeneratePin()
        assertEquals(401, call("GET", "/api/state", token = t).code)
    }
}
