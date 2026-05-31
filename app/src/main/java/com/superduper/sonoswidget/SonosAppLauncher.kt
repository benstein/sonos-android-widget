package com.superduper.sonoswidget

import android.content.Context
import android.content.Intent

object SonosAppLauncher {
    private val packageNames = listOf(
        "com.sonos.acr2",
        "com.sonos.acr"
    )

    fun resolvePackage(isLaunchable: (String) -> Boolean): String? {
        return packageNames.firstOrNull(isLaunchable)
    }

    fun launchIntent(context: Context): Intent? {
        val packageManager = context.packageManager
        val packageName = resolvePackage { candidate ->
            packageManager.getLaunchIntentForPackage(candidate) != null
        } ?: return null

        return packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(context: Context): Boolean {
        val intent = launchIntent(context) ?: return false
        context.startActivity(intent)
        return true
    }
}
