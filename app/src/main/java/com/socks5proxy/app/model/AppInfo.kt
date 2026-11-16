package com.socks5proxy.app.model

import android.graphics.Bitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val iconBitmap: Bitmap? = null,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = false
) {
    val searchKey: String = "$appName $packageName".lowercase()
}
