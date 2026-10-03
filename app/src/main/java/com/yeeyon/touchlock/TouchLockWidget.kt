package com.yeeyon.touchlock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class TouchLockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val open = PendingIntent.getActivity(context, 20,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_WIDGET_ENABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (id in ids) {
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.touchlock_widget).apply {
                setOnClickPendingIntent(R.id.widget_button, open)
            })
        }
    }
}
