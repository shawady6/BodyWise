package com.shawady.bodywise.widget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.shawady.bodywise.MainActivity
import com.shawady.bodywise.R
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MedStore
import com.shawady.bodywise.data.SlotState
import com.shawady.bodywise.data.slotState
import com.shawady.bodywise.data.slotsFor
import com.shawady.bodywise.util.format12
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Home-screen widget: today's next doses, with a button that marks the first one as taken. */
class MedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = buildViews(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TAKE) {
            val store = MedStore(context)
            val medId = intent.getStringExtra("med") ?: return
            val date = runCatching { LocalDate.parse(intent.getStringExtra("date")) }.getOrNull() ?: return
            val time = runCatching { LocalTime.parse(intent.getStringExtra("time")) }.getOrNull() ?: return
            val med = store.loadMeds().firstOrNull { it.id == medId } ?: return
            store.mark(med, date, time, DoseStatus.TAKEN, LocalDateTime.now())
            context.getSystemService(NotificationManager::class.java).cancel(("false|$medId|$time").hashCode())
            refresh(context)
        }
    }

    companion object {
        const val ACTION_TAKE = "com.shawady.bodywise.widget.TAKE_DOSE"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MedWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val store = MedStore(context)
            val meds = store.loadMeds()
            val logs = store.loadLogs()
            val now = LocalDateTime.now()
            val pending = slotsFor(meds, now.toLocalDate())
                .map { it to slotState(it, logs, now) }
                .filter { it.second != SlotState.TAKEN && it.second != SlotState.SKIPPED }
            val views = RemoteViews(context.packageName, R.layout.widget_meds)
            val lines = listOf(R.id.med_line1, R.id.med_line2, R.id.med_line3)
            val first = pending.firstOrNull()
            if (meds.isEmpty()) {
                views.setTextViewText(R.id.med_line1, "لسه ما أضفتش أدوية")
            } else if (first == null) {
                views.setTextViewText(R.id.med_line1, "خلّصت جرعات النهارده ✅")
            }
            lines.forEachIndexed { i, id ->
                val item = pending.getOrNull(i)
                if (item != null) {
                    val (slot, state) = item
                    val dose = listOf(slot.med.dose, slot.med.unit).filter { it.isNotBlank() }.joinToString(" ")
                    views.setTextViewText(id, "${slot.time.format12()}  ${slot.med.name}" + (if (dose.isNotBlank()) " ($dose)" else "") + if (state == SlotState.MISSED) " • فاتت" else "")
                    views.setViewVisibility(id, View.VISIBLE)
                } else if (!(i == 0 && (meds.isEmpty() || first == null))) {
                    views.setViewVisibility(id, View.GONE)
                } else views.setViewVisibility(id, View.VISIBLE)
            }
            if (first != null) {
                val slot = first.first
                val intent = Intent(context, MedWidgetProvider::class.java).setAction(ACTION_TAKE)
                    .putExtra("med", slot.med.id).putExtra("date", slot.date.toString()).putExtra("time", slot.time.toString())
                val take = PendingIntent.getBroadcast(
                    context, "${slot.med.id}|${slot.time}".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.med_take, take)
                views.setTextViewText(R.id.med_take, "أخدتها ✓ ${slot.med.name}")
                views.setViewVisibility(R.id.med_take, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.med_take, View.GONE)
            }
            val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.med_info, open)
            views.setOnClickPendingIntent(R.id.med_title, open)
            return views
        }
    }
}
