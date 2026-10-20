package com.fctracker.career.telemetry

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/** The phone's own address on the network it is connected to (for "Find my PC"). */
object LocalNetwork {
    /** The phone's IPv4 address, or null when it has no network (or cannot be read). Never throws. */
    fun ipv4(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return null
            val properties = manager.getLinkProperties(network) ?: return null
            properties.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
