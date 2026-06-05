package com.onyxlauncher.ui.home

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.onyxlauncher.domain.model.GestureAction

/**
 * Executes the configured gesture action.
 *
 * Must be called on the main thread because it may start Activities.
 */
fun performGestureAction(
    action: GestureAction,
    context: Context,
    onOpenDrawer: () -> Unit,
) {
    when (action) {
        GestureAction.NONE -> Unit

        GestureAction.OPEN_DRAWER -> onOpenDrawer()

        GestureAction.NOTIFICATIONS -> expandNotificationShade(context)

        GestureAction.OPEN_SEARCH -> {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fall back to assist intent
                val assist = Intent(Intent.ACTION_ASSIST).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(assist) }
            }
        }

        GestureAction.LOCK_SCREEN -> {
            // Full implementation requires Accessibility Service (Phase 6).
            // Show a toast-style message for now.
            android.widget.Toast.makeText(
                context,
                "Enable Accessibility Service for lock",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }

        GestureAction.CUSTOM_APP -> {
            // gestureCustomApp is resolved in HomeScreen before calling here;
            // nothing to do in this fallback path — caller launches the activity.
        }
    }
}

@SuppressLint("WrongConstant")
private fun expandNotificationShade(context: Context) {
    runCatching {
        val statusBarService = context.getSystemService("statusbar") ?: return@runCatching
        val sbClass = Class.forName("android.app.StatusBarManager")
        sbClass.getMethod("expandNotificationsPanel").invoke(statusBarService)
    }
}
