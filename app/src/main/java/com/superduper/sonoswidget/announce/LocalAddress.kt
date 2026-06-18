package com.superduper.sonoswidget.announce

import java.net.Inet4Address
import java.net.NetworkInterface

/** Finds the phone's LAN IPv4 so Sonos can reach the clip server over Wi-Fi. */
object LocalAddress {
    fun wifiIpv4(): String? {
        val candidates = mutableListOf<Pair<String, String>>() // interfaceName to address
        for (iface in NetworkInterface.getNetworkInterfaces()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (address in iface.inetAddresses) {
                if (address is Inet4Address && address.isSiteLocalAddress) {
                    candidates += iface.name to address.hostAddress.orEmpty()
                }
            }
        }
        // Prefer the Wi-Fi interface; fall back to any site-local IPv4.
        return candidates.firstOrNull { it.first.startsWith("wlan") }?.second
            ?: candidates.firstOrNull()?.second
    }
}
