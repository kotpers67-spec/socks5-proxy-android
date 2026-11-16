package com.socks5proxy.app.model

enum class TunnelState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class TunnelStatus(
    val state: TunnelState = TunnelState.DISCONNECTED,
    val message: String? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val connectedAtMillis: Long = 0L,
    val boundPort: Int = 0
) {
    val isRunning: Boolean
        get() = state == TunnelState.CONNECTED || state == TunnelState.CONNECTING

    fun formattedDuration(): String {
        if (connectedAtMillis == 0L || state != TunnelState.CONNECTED) return "00:00:00"
        val seconds = (System.currentTimeMillis() - connectedAtMillis) / 1000
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hrs, mins, secs)
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes Б"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return String.format("%.1f %sБ", bytes / Math.pow(1024.0, exp.toDouble()), pre)
        }
    }
}
