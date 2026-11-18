package com.socks5proxy.app.model

import androidx.compose.runtime.Immutable
import java.util.Locale

enum class TunnelState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

@Immutable
data class TunnelStatus(
    val state: TunnelState = TunnelState.DISCONNECTED,
    val message: String? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val rxSpeedBps: Long = 0L,
    val txSpeedBps: Long = 0L,
    val connectedAtMillis: Long = 0L,
    val boundPort: Int = 0
) {
    val isRunning: Boolean
        get() = state == TunnelState.CONNECTED || state == TunnelState.CONNECTING

    fun formattedDuration(nowMillis: Long = System.currentTimeMillis()): String {
        if (connectedAtMillis <= 0L || state != TunnelState.CONNECTED) return "00:00:00"
        val seconds = ((nowMillis - connectedAtMillis) / 1000).coerceAtLeast(0L)
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    }

    fun formattedRxSpeed(): String = formatSpeed(rxSpeedBps)
    fun formattedTxSpeed(): String = formatSpeed(txSpeedBps)
    fun formattedBytesIn(): String = formatBytes(bytesIn)
    fun formattedBytesOut(): String = formatBytes(bytesOut)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 Б"
            if (bytes < 1024) return "$bytes Б"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f КБ", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.1f МБ", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f ГБ", gb)
        }

        fun formatSpeed(bytesPerSec: Long): String {
            if (bytesPerSec <= 0) return "0 Б/с"
            if (bytesPerSec < 1024) return "$bytesPerSec Б/с"
            val kbps = bytesPerSec / 1024.0
            if (kbps < 1024.0) return String.format(Locale.US, "%.1f КБ/с", kbps)
            val mbps = kbps / 1024.0
            if (mbps < 1024.0) return String.format(Locale.US, "%.1f МБ/с", mbps)
            val gbps = mbps / 1024.0
            return String.format(Locale.US, "%.2f ГБ/с", gbps)
        }
    }
}
