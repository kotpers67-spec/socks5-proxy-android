package com.socks5proxy.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.socks5proxy.app.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppListRepository(private val context: Context) {

    suspend fun getInstalledApps(selectedPackages: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val myPackage = context.packageName

        // Query installed applications without heavy meta-data parsing
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val resultList = mutableListOf<AppInfo>()

        for (app in apps) {
            // Exclude our own app from selection to avoid routing loop
            if (app.packageName == myPackage) continue

            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appLabel = app.loadLabel(pm).toString()
            val appIconBitmap = try {
                val d = app.loadIcon(pm)
                drawableToScaledBitmap(d, targetSizePx = 96)
            } catch (_: Exception) {
                null
            }

            resultList.add(
                AppInfo(
                    packageName = app.packageName,
                    appName = if (appLabel.isNotBlank()) appLabel else app.packageName,
                    iconBitmap = appIconBitmap,
                    isSystemApp = isSystem,
                    isSelected = selectedPackages.contains(app.packageName)
                )
            )
        }

        // Sort: selected first, then non-system first, then alphabetical by name
        resultList.sortedWith(
            compareByDescending<AppInfo> { it.isSelected }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun drawableToScaledBitmap(drawable: Drawable, targetSizePx: Int): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, targetSizePx, targetSizePx, true)
            } else {
                val bmp = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }
}
