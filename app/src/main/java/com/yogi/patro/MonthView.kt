package com.yogi.patro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** One BS month as a 7-column grid (Sunday first), drawn directly for speed. Swipe sideways to change month. */
class MonthView(ctx: Context) : View(ctx) {
    var year = 2083
    var month = 1
    var selected: Long = -1
    var days: Map<Long, DayInfo> = emptyMap()
    var onPick: ((Long) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null
    /** text size multiplier (setting); the widget uses its own */
    var scale = Prefs.textScale
    /** draw the weekday names on top (widget) */
    var withNames = false

    private val d = resources.displayMetrics.density
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun first(): Long = Bs.toEpoch(BsDate(year, month, 1))
    private fun offset(): Int = Bs.weekday(first())
    private fun rows(): Int = (offset() + Bs.daysIn(year, month) + 6) / 7

    private fun namesH(): Float = if (withNames) 22 * d * scale else 0f

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        val cell = width / 7
        setMeasuredDimension(width, (cell * (1.0f + 0.12f * scale) * rows() + namesH()).toInt())
    }

    override fun onDraw(c: Canvas) {
        val top = namesH()
        val cw = width / 7f
        val ch = (height - top) / rows().toFloat()
        if (withNames) {
            small.textSize = 12 * d * scale
            for (w in 0..6) {
                small.color = if (Prefs.weeklyHoliday(w)) C.RED else C.SUB
                c.drawText(Bs.dayShort(w), w * cw + cw / 2, top * 0.7f, small)
            }
        }
        val start = first()
        val off = offset()
        val today = Bs.todayEpoch()
        for (i in 0 until Bs.daysIn(year, month)) {
            val day = start + i
            val pos = off + i
            val col = pos % 7
            val row = pos / 7
            val x = col * cw
            val y = top + row * ch
            val info = days[day]
            val holiday = Prefs.weeklyHoliday(col) || info?.holiday == true
            rect.set(x + 2 * d, y + 2 * d, x + cw - 2 * d, y + ch - 2 * d)
            when {
                day == selected -> { fill.color = C.GOLD; c.drawRoundRect(rect, 12 * d, 12 * d, fill) }
                day == today -> { fill.color = 0x33FFB627; c.drawRoundRect(rect, 12 * d, 12 * d, fill) }
                info?.fests?.isNotEmpty() == true -> { fill.color = 0x14FFFFFF; c.drawRoundRect(rect, 12 * d, 12 * d, fill) }
            }
            val sel = day == selected
            big.color = if (sel) 0xFF1A1110.toInt() else if (holiday) C.RED else C.TEXT
            big.textSize = (ch * 0.34f * scale).coerceAtMost(cw * 0.5f)
            c.drawText(Bs.num(i + 1), x + cw / 2, y + ch * 0.52f, big)
            val ad = Bs.adFromEpoch(day)
            small.color = if (sel) 0xAA1A1110.toInt() else C.SUB
            small.textSize = (ch * 0.16f * scale).coerceAtMost(cw * 0.26f)
            small.textAlign = Paint.Align.RIGHT
            c.drawText(ad.d.toString(), x + cw - 6 * d, y + ch * 0.22f, small)
            small.textAlign = Paint.Align.CENTER
            if (info != null) {
                val t = info.tithi
                val short = when (t) { 15 -> "पूर्णिमा"; 30 -> "औंसी"; else -> Bs.num((t - 1) % 15 + 1) }
                small.textSize = (ch * 0.14f * scale).coerceAtMost(cw * 0.22f)
                small.color = if (sel) 0xAA1A1110.toInt() else if (t == 15 || t == 30) C.GOLD else C.SUB
                c.drawText(if (Prefs.nepaliText) short else Astro.tithiName(t, false).take(4), x + cw / 2, y + ch * 0.82f, small)
                if (info.fests.isNotEmpty()) {
                    fill.color = if (sel) 0xFF1A1110.toInt() else if (info.fests.any { it.holiday }) C.RED else C.SAFFRON
                    c.drawCircle(x + 9 * d, y + ch * 0.18f, 3 * d, fill)
                }
            }
        }
    }

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val cw = width / 7f
            val ch = (height - namesH()) / rows().toFloat()
            val pos = ((e.y - namesH()) / ch).toInt() * 7 + (e.x / cw).toInt()
            val i = pos - offset()
            if (i in 0 until Bs.daysIn(year, month)) {
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                onPick?.invoke(first() + i)
            }
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > abs(vy) * 1.3f && abs(vx) > 600) {
                onSwipe?.invoke(if (vx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    private var dx0 = 0f
    private var dy0 = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx0 = e.x; dy0 = e.y }
            // sideways = change month (keep the touch); up/down = let the page scroll
            MotionEvent.ACTION_MOVE -> if (abs(e.x - dx0) > abs(e.y - dy0) * 1.3f && abs(e.x - dx0) > 12 * d)
                parent?.requestDisallowInterceptTouchEvent(true)
        }
        return gd.onTouchEvent(e) || super.onTouchEvent(e)
    }
}
