package tv.pelishd4k.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetPolicyTest {
    @Test
    fun privateClients() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.4.9", "172.31.255.1", "127.0.0.1", "169.254.3.3", "::1", "fd12:3456::1", "fe80::1")
            .forEach { assertTrue(it, NetPolicy.isPrivateClient(it)) }
    }

    @Test
    fun publicClientsRejected() {
        listOf("8.8.8.8", "172.32.0.1", "11.0.0.1", "2001:4860:4860::8888", "", "evil.com", "1.2.3.4.5")
            .forEach { assertFalse(it, NetPolicy.isPrivateClient(it)) }
        assertFalse(NetPolicy.isPrivateClient(null))
    }

    @Test
    fun hostHeader() {
        assertTrue(NetPolicy.isAcceptableHost("192.168.1.50:8765"))
        assertTrue(NetPolicy.isAcceptableHost("localhost:8765"))
        assertTrue(NetPolicy.isAcceptableHost("[fd00::1]:8765"))
        assertFalse(NetPolicy.isAcceptableHost("attacker.com"))
        assertFalse(NetPolicy.isAcceptableHost("192.168.1.50.attacker.com"))
        assertFalse(NetPolicy.isAcceptableHost(null))
    }
}

class RemoteAuthTest {
    @Test
    fun globalFailuresRegeneratePin() {
        var now = 0L
        val auth = RemoteAuth(clock = { now })
        val first = auth.pin
        val wrong = if (first == "000000") "111111" else "000000"
        // Distintos clientes para evitar el bloqueo por IP: solo cuenta el límite global.
        repeat(RemoteAuth.GLOBAL_FAILURE_LIMIT) { i -> auth.login("c$i", wrong) }
        assertEquals(6, auth.pin.length)
        // El PIN anterior ya no sirve (salvo coincidencia de 1 entre un millón al regenerar).
        if (auth.pin != first) {
            assertTrue(auth.login("fresh-client", first) is LoginResult.WrongPin)
        }
    }

    @Test
    fun tokensExpire() {
        var now = 1_000L
        val auth = RemoteAuth(clock = { now })
        val token = (auth.login("a", auth.pin) as LoginResult.Success).token
        assertTrue(auth.isValid(token))
        now += RemoteAuth.TOKEN_TTL_MS + 1
        assertFalse(auth.isValid(token))
    }

    @Test
    fun lockoutLiftsAfterWaiting() {
        var now = 0L
        val auth = RemoteAuth(clock = { now })
        val wrong = if (auth.pin == "000000") "111111" else "000000"
        repeat(RemoteAuth.MAX_FAILURES_BEFORE_LOCK) { auth.login("a", wrong) }
        assertTrue(auth.login("a", auth.pin) is LoginResult.Locked)
        now += 31_000
        assertTrue(auth.login("a", auth.pin) is LoginResult.Success)
    }

    @Test
    fun tokenCountIsCapped() {
        val auth = RemoteAuth()
        val tokens = (1..RemoteAuth.MAX_TOKENS + 3).map { (auth.login("a", auth.pin) as LoginResult.Success).token }
        assertFalse("El token más antiguo debe haberse descartado", auth.isValid(tokens.first()))
        assertTrue(auth.isValid(tokens.last()))
    }
}
