package com.yogi.patro

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

/** Today: big date, day, tithi, AD date, today's festival and the next one. */
class PatroWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.async(ctx) { Widgets.today(ctx, mgr, ids) }

    companion object {
        /** Refreshes every Yogi Patro widget (all three kinds). */
        fun updateAll(ctx: Context) = Widgets.updateAll(ctx)
    }
}

/** Small: just today's BS date. */
class PatroSmallWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.async(ctx) { Widgets.small(ctx, mgr, ids) }
}

/** The whole BS month, with today marked, festivals and holidays. */
class PatroMonthWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.async(ctx) { Widgets.month(ctx, mgr, ids) }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle?) {
        Widgets.async(ctx) { Widgets.month(ctx, mgr, intArrayOf(id)) }
    }
}

object Widgets {
    fun async(ctx: Context, work: () -> Unit) {
        Thread {
            try {
                Store.init(ctx)
                work()
                AlarmReceiver.schedule(ctx)
            } catch (_: Throwable) {
            }
        }.start()
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        fun ids(c: Class<*>) = try { mgr.getAppWidgetIds(ComponentName(ctx, c)) } catch (_: Exception) { IntArray(0) }
        val a = ids(PatroWidget::class.java)
        val b = ids(PatroSmallWidget::class.java)
        val m = ids(PatroMonthWidget::class.java)
        if (a.isEmpty() && b.isEmpty() && m.isEmpty()) return
        Thread {
            try {
                Store.init(ctx)
                if (a.isNotEmpty()) today(ctx, mgr, a)
                if (b.isNotEmpty()) small(ctx, mgr, b)
                if (m.isNotEmpty()) month(ctx, mgr, m)
            } catch (_: Throwable) {
            }
        }.start()
    }

    private fun open(ctx: Context, tab: Int): PendingIntent =
        PendingIntent.getActivity(ctx, 10 + tab, Intent(ctx, MainActivity::class.java).putExtra("tab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun np() = Prefs.nepaliText

    fun today(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val day = Bs.todayEpoch()
        val info = try { Patro.day(day) } catch (_: Throwable) { null }
        val b = Bs.fromEpoch(day) ?: return
        val ad = Bs.adFromEpoch(day)
        val v = RemoteViews(ctx.packageName, R.layout.widget_patro)
        v.setTextViewText(R.id.w_day, Bs.num(b.d))
        v.setTextViewText(R.id.w_month, Bs.monthName(b.m) + " " + Bs.num(b.y))
        v.setTextViewText(R.id.w_weekday, Bs.dayName(Bs.weekday(day)))
        v.setTextViewText(R.id.w_tithi, info?.let { Astro.lunarName(it.lunar, it.tithi, it.adhik) + " " + Astro.tithiName(it.tithi) } ?: "")
        v.setTextViewText(R.id.w_ad, "${Bs.DAYS_SHORT_EN[Bs.weekday(day)]}, ${ad.d} ${Bs.AD_MONTHS[ad.m - 1]} ${ad.y}")
        val f = info?.fests?.firstOrNull()
        if (f != null) {
            v.setTextViewText(R.id.w_fest, f.emoji + " " + f.name())
            v.setViewVisibility(R.id.w_fest, View.VISIBLE)
        } else v.setViewVisibility(R.id.w_fest, View.GONE)
        val next = try { Patro.upcoming(day + 1, 1).firstOrNull() } catch (_: Throwable) { null }
        if (next != null) {
            val n = next.day - day
            val left = if (np()) (if (n == 1L) "भोलि" else Bs.num(n) + " दिनमा") else (if (n == 1L) "tomorrow" else "in $n days")
            v.setTextViewText(R.id.w_next, (if (np()) "अर्को: " else "Next: ") + next.fests.first().emoji + " " + next.fests.first().name() + " · " + left)
            v.setViewVisibility(R.id.w_next, View.VISIBLE)
        } else v.setViewVisibility(R.id.w_next, View.GONE)
        v.setTextColor(R.id.w_day, if (info?.holiday == true) C.RED else C.GOLD)
        v.setOnClickPendingIntent(R.id.w_root, open(ctx, 0))
        for (id in ids) try { mgr.updateAppWidget(id, v) } catch (_: Exception) {}
    }

    fun small(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val day = Bs.todayEpoch()
        val info = try { Patro.day(day) } catch (_: Throwable) { null }
        val b = Bs.fromEpoch(day) ?: return
        val v = RemoteViews(ctx.packageName, R.layout.widget_small)
        v.setTextViewText(R.id.w_s_weekday, Bs.dayName(Bs.weekday(day)))
        v.setTextViewText(R.id.w_s_day, Bs.num(b.d))
        v.setTextViewText(R.id.w_s_month, Bs.monthName(b.m))
        v.setTextViewText(R.id.w_s_tithi, info?.let { Astro.tithiName(it.tithi) } ?: "")
        v.setTextColor(R.id.w_s_day, if (info?.holiday == true) C.RED else C.TEXT)
        v.setOnClickPendingIntent(R.id.w_s_root, open(ctx, 0))
        for (id in ids) try { mgr.updateAppWidget(id, v) } catch (_: Exception) {}
    }

    fun month(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val today = Bs.todayEpoch()
        val b = Bs.fromEpoch(today) ?: return
        val data = try { Patro.year(b.y) } catch (_: Throwable) { emptyMap() }
        val dens = ctx.resources.displayMetrics.density
        for (id in ids) {
            val o = try { mgr.getAppWidgetOptions(id) } catch (_: Exception) { null }
            val wDp = (o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0).takeIf { it > 0 } ?: 320
            val wPx = (wDp * dens).toInt().coerceIn(300, 1080) - (24 * dens).toInt()
            val mv = MonthView(ctx)
            mv.year = b.y
            mv.month = b.m
            mv.selected = -1
            mv.days = data
            mv.withNames = true
            mv.scale = Prefs.textScale * 0.95f
            mv.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            mv.layout(0, 0, mv.measuredWidth, mv.measuredHeight)
            val bmp = Bitmap.createBitmap(mv.measuredWidth.coerceAtLeast(1), mv.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            mv.draw(Canvas(bmp))
            val v = RemoteViews(ctx.packageName, R.layout.widget_month)
            val first = Bs.toAd(BsDate(b.y, b.m, 1))
            val last = Bs.toAd(BsDate(b.y, b.m, Bs.daysIn(b.y, b.m)))
            v.setTextViewText(R.id.w_m_title, Bs.monthName(b.m) + " " + Bs.num(b.y))
            v.setTextViewText(R.id.w_m_sub, Bs.AD_MONTHS[first.m - 1].take(3) + (if (first.m != last.m) " – " + Bs.AD_MONTHS[last.m - 1].take(3) else "") +
                " " + last.y)
            v.setImageViewBitmap(R.id.w_m_grid, bmp)
            v.setOnClickPendingIntent(R.id.w_m_root, open(ctx, 1))
            try { mgr.updateAppWidget(id, v) } catch (_: Exception) {}
        }
    }
}
