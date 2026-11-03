package tv.pelishd4k.app.remote

import android.content.Context
import tv.pelishd4k.remote.TokenStore

/** Guarda solo hashes de los tokens emparejados, para no pedir el PIN en cada arranque. */
class PrefsTokenStore(context: Context) : TokenStore {
    private val sp = context.getSharedPreferences("remote_tokens", Context.MODE_PRIVATE)

    override fun load(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (entry in sp.getStringSet("t", emptySet()).orEmpty()) {
            val parts = entry.split('=')
            val exp = parts.getOrNull(1)?.toLongOrNull() ?: continue
            if (parts.size == 2) out[parts[0]] = exp
        }
        return out
    }

    override fun save(tokens: Map<String, Long>) {
        sp.edit().putStringSet("t", tokens.map { "${it.key}=${it.value}" }.toSet()).apply()
    }
}
