package tv.pelishd4k.remote

import java.security.MessageDigest
import java.security.SecureRandom

/** Persistencia de los tokens emparejados (solo se guardan hashes SHA-256 y su caducidad). */
interface TokenStore {
    fun load(): Map<String, Long>
    fun save(tokens: Map<String, Long>)
}

class InMemoryTokenStore : TokenStore {
    private var data: Map<String, Long> = emptyMap()
    override fun load(): Map<String, Long> = data
    override fun save(tokens: Map<String, Long>) {
        data = tokens
    }
}

sealed class LoginResult {
    data class Success(val token: String) : LoginResult()
    object WrongPin : LoginResult()
    data class Locked(val retryAfterSec: Int) : LoginResult()
}

/**
 * Autenticación del control remoto.
 *
 * - PIN de 6 dígitos mostrado solo en la TV.
 * - Bloqueo progresivo por cliente tras [MAX_FAILURES_BEFORE_LOCK] fallos.
 * - Si hay demasiados fallos globales el PIN se regenera (evita fuerza bruta distribuida).
 * - Los tokens emitidos son aleatorios (192 bits); solo se guarda su hash.
 */
class RemoteAuth(
    private val store: TokenStore = InMemoryTokenStore(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: SecureRandom = SecureRandom(),
) {
    companion object {
        const val MAX_FAILURES_BEFORE_LOCK = 5
        const val GLOBAL_FAILURE_LIMIT = 15
        const val TOKEN_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_TOKENS = 8
        private const val BASE_LOCK_MS = 30_000L
        private const val MAX_LOCK_MS = 15L * 60 * 1000
    }

    private class Attempts(var failures: Int = 0, var lockedUntil: Long = 0)

    private val attempts = HashMap<String, Attempts>()
    private var globalFailures = 0

    @Volatile
    var pin: String = generatePin()
        private set

    private fun generatePin(): String = String.format("%06d", random.nextInt(1_000_000))

    @Synchronized
    fun regeneratePin(revokeTokens: Boolean = true) {
        pin = generatePin()
        attempts.clear()
        globalFailures = 0
        if (revokeTokens) store.save(emptyMap())
    }

    @Synchronized
    fun login(clientId: String, candidate: String): LoginResult {
        val now = clock()
        val a = attempts.getOrPut(clientId) { Attempts() }
        if (a.lockedUntil > now) {
            return LoginResult.Locked(((a.lockedUntil - now + 999) / 1000).toInt())
        }
        val ok = MessageDigest.isEqual(candidate.toByteArray(Charsets.UTF_8), pin.toByteArray(Charsets.UTF_8))
        if (!ok) {
            a.failures++
            globalFailures++
            if (a.failures >= MAX_FAILURES_BEFORE_LOCK) {
                val exp = (a.failures - MAX_FAILURES_BEFORE_LOCK).coerceAtMost(5)
                val lock = (BASE_LOCK_MS shl exp).coerceAtMost(MAX_LOCK_MS)
                a.lockedUntil = now + lock
            }
            if (globalFailures >= GLOBAL_FAILURE_LIMIT) {
                // Demasiados intentos fallidos en total: invalida el PIN actual.
                val keepTokens = store.load()
                pin = generatePin()
                attempts.clear()
                globalFailures = 0
                store.save(keepTokens)
            }
            return LoginResult.WrongPin
        }
        attempts.remove(clientId)
        globalFailures = 0
        return LoginResult.Success(issueToken(now))
    }

    private fun issueToken(now: Long): String {
        val raw = ByteArray(24)
        random.nextBytes(raw)
        val token = raw.joinToString("") { "%02x".format(it) }
        val tokens = LinkedHashMap(store.load().filterValues { it > now })
        tokens[hash(token)] = now + TOKEN_TTL_MS
        // La caducidad crece con la emisión: el de menor caducidad es el más antiguo.
        while (tokens.size > MAX_TOKENS) {
            tokens.remove(tokens.entries.minByOrNull { it.value }!!.key)
        }
        store.save(tokens)
        return token
    }

    @Synchronized
    fun isValid(token: String?): Boolean {
        if (token.isNullOrEmpty() || token.length > 128) return false
        val now = clock()
        val tokens = store.load()
        val exp = tokens[hash(token)] ?: return false
        return exp > now
    }

    @Synchronized
    fun revokeAll() = store.save(emptyMap())

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
