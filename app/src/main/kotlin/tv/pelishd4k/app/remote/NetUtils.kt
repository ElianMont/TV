package tv.pelishd4k.app.remote

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetUtils {
    /** IPv4 privada de la TV en la red local (prefiere Wi-Fi/Ethernet). */
    fun localIpv4(): String? {
        return try {
            var fallback: String? = null
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces() ?: return null)) {
                if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                val name = nif.name.lowercase()
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        if (name.startsWith("wlan") || name.startsWith("eth")) return addr.hostAddress
                        if (fallback == null) fallback = addr.hostAddress
                    }
                }
            }
            fallback
        } catch (e: Exception) {
            null
        }
    }
}
