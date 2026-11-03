package tv.pelishd4k.remote

import java.net.InetAddress

/** Reglas de red del servidor remoto: solo clientes de la red local y Host por IP literal. */
object NetPolicy {
    private val HOST_REGEX = Regex("^(\\d{1,3}(\\.\\d{1,3}){3}|\\[[0-9a-fA-F:.]+\\]|localhost)(:\\d{1,5})?$")

    /** True si [ip] es loopback, enlace local, RFC1918 o IPv6 ULA (fc00::/7). */
    fun isPrivateClient(ip: String?): Boolean {
        if (ip.isNullOrEmpty()) return false
        val addr = try {
            if (!ip.all { it.isDigit() || it == '.' || it == ':' || it in 'a'..'f' || it in 'A'..'F' || it == '%' }) return false
            InetAddress.getByName(ip.substringBefore('%'))
        } catch (e: Exception) {
            return false
        }
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) return true
        val bytes = addr.address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /**
     * El header Host debe ser una IP literal (o localhost). Esto bloquea ataques de
     * DNS-rebinding, donde un dominio externo apunta a la IP de la TV.
     */
    fun isAcceptableHost(host: String?): Boolean = host != null && HOST_REGEX.matches(host.trim())
}
