package com.shawady.bodywise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.BodyWiseStore
import java.time.LocalDate

/** Home-screen widget: shows today's cups and adds one with a tap, without opening the app. */
class WaterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_ADD) {
            val store = BodyWiseStore(context)
            val snapshot = store.load()
            val today = LocalDate.now().toString()
            val water = snapshot.water.toMutableMap()
            water[today] = ((water[today] ?: 0) + 1).coerceAtMost(60)
            store.save(snapshot.copy(water = water))
            refresh(context)
        }
    }

    companion object {
        const val ACTION_ADD = "com.shawady.bodywise.widget.ADD_CUP"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val snapshot = BodyWiseStore(context).load()
            val cups = snapshot.water[LocalDate.now().toString()] ?: 0
            val views = RemoteViews(context.packageName, R.layout.widget_water)
            views.setTextViewText(R.id.widget_count, "$cups / ${snapshot.waterGoal} كوب")
            val add = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WaterWidgetProvider::class.java).setAction(ACTION_ADD),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_add, add)
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_info, open)
            return views
        }
    }
}
