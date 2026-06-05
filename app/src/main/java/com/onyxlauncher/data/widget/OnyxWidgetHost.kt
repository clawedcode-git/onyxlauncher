package com.onyxlauncher.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Minimal AppWidgetHost for the Onyx Launcher.
 * Host ID 1024 is arbitrary but must be stable across reboots.
 *
 * startListening() / stopListening() / allocateAppWidgetId() / deleteAppWidgetId()
 * are all inherited from AppWidgetHost and are already public.
 */
class OnyxWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = AppWidgetHostView(context)

    companion object {
        const val HOST_ID = 1024
    }
}
