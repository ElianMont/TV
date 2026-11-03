package tv.pelishd4k.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.pelishd4k.app.core.UrlPolicy
import tv.pelishd4k.app.core.UrlPolicy.MediaKind

class UrlPolicyTest {

    @Test
    fun parseAcceptsOnlyHttpAndHttps() {
        assertEquals("https://pelishd4k.com", UrlPolicy.parse("https://pelishd4k.com")?.toString())
        assertEquals("https://pelishd4k.com", UrlPolicy.parse("  pelishd4k.com ")?.toString())
        assertNotNull(UrlPolicy.parse("http://example.com/a"))
        listOf(
            "javascript:alert(1)", "intent://x#Intent;scheme=http;end", "market://details?id=a", "file:///etc/passwd",
            "data:text/html,hi", "content://x", "ftp://a.com", "https://", "", "no spaces here.com", "https://user:pw@a.com",
        ).forEach { assertNull(it, UrlPolicy.parse(it)) }
        assertNull(UrlPolicy.parse(null))
        assertNull(UrlPolicy.parse("https://a.com/" + "x".repeat(3000)))
    }

    @Test
    fun allowedHostsIncludeSubdomainsButNotLookalikes() {
        val allowed = listOf("pelishd4k.com")
        assertTrue(UrlPolicy.isHostAllowed("pelishd4k.com", allowed))
        assertTrue(UrlPolicy.isHostAllowed("www.pelishd4k.com", allowed))
        assertTrue(UrlPolicy.isHostAllowed("CDN.PelisHD4K.com", allowed))
        assertFalse(UrlPolicy.isHostAllowed("evilpelishd4k.com", allowed))
        assertFalse(UrlPolicy.isHostAllowed("pelishd4k.com.evil.net", allowed))
        assertFalse(UrlPolicy.isHostAllowed(null, allowed))
        assertFalse(UrlPolicy.isHostAllowed("a.com", emptyList()))
    }

    @Test
    fun mediaKindFromPathIgnoringQuery() {
        assertEquals(MediaKind.HLS, UrlPolicy.mediaKind("https://cdn.x.com/a/master.m3u8?token=abc"))
        assertEquals(MediaKind.DASH, UrlPolicy.mediaKind("https://cdn.x.com/a/manifest.MPD"))
        assertEquals(MediaKind.PROGRESSIVE, UrlPolicy.mediaKind("https://cdn.x.com/a/movie.mp4#t=10"))
        assertNull(UrlPolicy.mediaKind("https://cdn.x.com/watch?v=1.mp4"))
        assertNull(UrlPolicy.mediaKind("blob:https://x.com/uuid"))
        assertNull(UrlPolicy.mediaKind("https://x.com/page.html"))
    }

    @Test
    fun subtitleMime() {
        assertEquals("text/vtt", UrlPolicy.isSubtitle("https://x.com/es.vtt"))
        assertEquals("application/x-subrip", UrlPolicy.isSubtitle("https://x.com/es.srt?x=1"))
        assertNull(UrlPolicy.isSubtitle("https://x.com/es.txt"))
    }

    @Test
    fun resolveAndSearchTemplate() {
        assertEquals("https://pelishd4k.com/peliculas/", UrlPolicy.resolve("https://pelishd4k.com", "/peliculas/"))
        assertEquals("https://pelishd4k.com/series/", UrlPolicy.resolve("https://pelishd4k.com/inicio/", "series/"))
        assertEquals("https://pelishd4k.com", UrlPolicy.resolve("https://pelishd4k.com", ""))
        assertEquals(
            "https://pelishd4k.com/?s=el+se%C3%B1or+de+los+anillos",
            UrlPolicy.buildSearchUrl("{home}/?s={query}", "https://pelishd4k.com/x", "el señor de los anillos"),
        )
        // Una consulta maliciosa no puede inyectar otro destino: se codifica.
        val url = UrlPolicy.buildSearchUrl("{home}/?s={query}", "https://pelishd4k.com", "a&redirect=https://evil.com#")!!
        assertEquals("pelishd4k.com", java.net.URI(url).host)
        assertFalse(url.contains("&redirect="))
    }

    @Test
    fun httpUpgradesToHttps() {
        val up = UrlPolicy.toHttps(UrlPolicy.parse("http://pelishd4k.com:80/a?b=1")!!)
        assertEquals("https://pelishd4k.com/a?b=1", up.toString())
    }

    @Test
    fun domainListParsing() {
        assertEquals(
            listOf("a.com", "b.net", "c.org"),
            UrlPolicy.parseDomainList("A.com, https://b.net/path ;  c.org\n.a.com"),
        )
        assertTrue(UrlPolicy.parseDomainList("nodots, bad domain!.com, ").isEmpty())
    }
}
