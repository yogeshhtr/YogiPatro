package com.yogi.patro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Home-screen Patro: today's BS date, day, tithi, AD date and the festival, if any. */
class PatroWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        Thread {
            try {
                Store.init(ctx)
                render(ctx, mgr, ids)
                AlarmReceiver.schedule(ctx)
            } finally {
                done.finish()
            }
        }.start()
    }

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = try { mgr.getAppWidgetIds(ComponentName(ctx, PatroWidget::class.java)) } catch (_: Exception) { IntArray(0) }
            if (ids.isNotEmpty()) Thread { render(ctx, mgr, ids) }.start()
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val today = Bs.todayEpoch()
            val info = try { Patro.day(today) } catch (_: Throwable) { null }
            val b = Bs.fromEpoch(today) ?: return
            val ad = Bs.adFromEpoch(today)
            val v = RemoteViews(ctx.packageName, R.layout.widget_patro)
            v.setTextViewText(R.id.w_day, Bs.num(b.d))
            v.setTextViewText(R.id.w_month, Bs.monthName(b.m) + " " + Bs.num(b.y))
            v.setTextViewText(R.id.w_weekday, Bs.dayName(Bs.weekday(today)))
            v.setTextViewText(R.id.w_tithi, info?.let { Astro.pakshaName(it.tithi) + " " + Astro.tithiName(it.tithi) } ?: "")
            v.setTextViewText(R.id.w_ad, "${ad.d} ${Bs.AD_MONTHS[ad.m - 1]} ${ad.y}")
            val f = info?.fests?.firstOrNull()
            if (f != null) {
                v.setTextViewText(R.id.w_fest, f.emoji + " " + f.name())
                v.setViewVisibility(R.id.w_fest, View.VISIBLE)
            } else v.setViewVisibility(R.id.w_fest, View.GONE)
            v.setTextColor(R.id.w_day, if (info?.holiday == true) C.RED else C.GOLD)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_root, open)
            for (id in ids) try { mgr.updateAppWidget(id, v) } catch (_: Exception) {}
        }
    }
}
