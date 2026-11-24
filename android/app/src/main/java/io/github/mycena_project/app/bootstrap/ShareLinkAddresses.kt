// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Picks a reachable IPv4 for the phone-to-phone HTTP URL.
 * Never returns loopback. Does not assume 192.168.43/49 prefixes.
 */
object ShareLinkAddresses {

    data class Ipv4Interface(val name: String, val host: String)

    fun isLoopbackHost(host: String): Boolean {
        val h = host.trim().lowercase()
        return h.isEmpty() ||
            h == "localhost" ||
            h == "::1" ||
            h.startsWith("127.") ||
            h == "0.0.0.0"
    }

    fun isSkippedInterfaceName(name: String): Boolean {
        val n = name.lowercase()
        return n == "lo" ||
            n.startsWith("lo") && n.length <= 3 ||
            n.startsWith("rmnet") ||
            n.startsWith("ccmni") ||
            n.startsWith("dummy") ||
            n.startsWith("tun") ||
            n.startsWith("ppp") ||
            n.startsWith("ipsec") ||
            n.startsWith("radio") ||
            n.contains("clat")
    }

    /** Interfaces whose names usually belong to SoftAP / Wi‑Fi Direct / STA Wi‑Fi. */
    fun isPreferredShareInterfaceName(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("ap") ||
            n.startsWith("p2p") ||
            n.startsWith("wlan") ||
            n.contains("swlan") ||
            n.contains("softap")
    }

    fun collectIpv4Interfaces(): List<Ipv4Interface> {
        val out = mutableListOf<Ipv4Interface>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return out
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            if (isSkippedInterfaceName(intf.name)) continue
            for (addr in intf.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    if (!isLoopbackHost(host)) {
                        out.add(Ipv4Interface(intf.name, host))
                    }
                }
            }
        }
        return out
    }

    fun currentIpv4Hosts(): Set<String> =
        collectIpv4Interfaces().map { it.host }.toSet()

    /**
     * @param preferredHost Address from an Android API (e.g. Wi‑Fi Direct group owner)
     *        or the active Wi‑Fi LinkProperties host. Used if it is a non-loopback IPv4.
     * @param hostsBeforeLink IPv4s observed before hotspot/Direct started; a newly
     *        appeared address is preferred over leftover STA addresses.
     */
    fun pickShareHost(
        preferredHost: String?,
        hostsBeforeLink: Set<String>,
        interfaces: List<Ipv4Interface>,
    ): String? {
        val preferred = preferredHost?.trim()?.substringBefore('%')
        if (preferred != null && isUsableIpv4(preferred)) {
            return preferred
        }
        val usable = interfaces.filter { isUsableIpv4(it.host) }
        if (usable.isEmpty()) return null
        val newlyAppeared = usable.filter { it.host !in hostsBeforeLink }
        val pool = newlyAppeared.ifEmpty { usable }
        pool.firstOrNull { isPreferredShareInterfaceName(it.name) }?.let { return it.host }
        return pool.first().host
    }

    fun isUsableIpv4(host: String): Boolean {
        if (isLoopbackHost(host)) return false
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
    }

    /** Active Wi‑Fi (or Wi‑Fi Aware) IPv4 from ConnectivityManager — for same-LAN share. */
    fun preferredLanHost(context: Context): String? {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val wifi =
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        if (!wifi) return null
        val lp = cm.getLinkProperties(network) ?: return null
        return lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .mapNotNull { it.hostAddress }
            .firstOrNull { isUsableIpv4(it) }
    }
}
