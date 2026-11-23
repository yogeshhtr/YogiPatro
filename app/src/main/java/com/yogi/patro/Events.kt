package com.yogi.patro

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** A personal day to remember, by BS date: birthdays, anniversaries, shraddha… repeats every year. */
class Event(val id: Long, val name: String, val m: Int, val d: Int, val kind: Int, val year: Int) {
    fun json(): JSONObject = JSONObject().put("id", id).put("n", name).put("m", m).put("d", d).put("k", kind).put("y", year)

    val emoji: String get() = KINDS_EMOJI[kind.coerceIn(0, KINDS_EMOJI.size - 1)]

    /** The day it falls on in BS year [y] (day 32 becomes the last day of short months). */
    fun dayIn(y: Int): Long? {
        if (!Bs.valid(y)) return null
        return Bs.toEpoch(BsDate(y, m, d.coerceAtMost(Bs.daysIn(y, m))))
    }

    companion object {
        val KINDS_NP = listOf("जन्मदिन", "वार्षिकोत्सव", "श्राद्ध / पुण्यतिथि", "अन्य")
        val KINDS_EN = listOf("Birthday", "Anniversary", "Shraddha / remembrance", "Other")
        val KINDS_EMOJI = listOf("🎂", "💍", "🪔", "📌")
        fun from(o: JSONObject) = Event(o.optLong("id"), o.optString("n"), o.optInt("m", 1), o.optInt("d", 1), o.optInt("k", 0), o.optInt("y", 0))
    }
}

object Events {
    fun all(): List<Event> = Store.arr("events").objects().map { Event.from(it) }

    fun save(list: List<Event>) {
        val a = JSONArray()
        for (e in list) a.put(e.json())
        Store.setArr("events", a)
    }

    fun add(e: Event) = save(all().filter { it.id != e.id } + e)
    fun remove(id: Long) = save(all().filter { it.id != id })

    fun on(day: Long): List<Event> {
        val b = Bs.fromEpoch(day) ?: return emptyList()
        return all().filter { it.dayIn(b.y) == day }
    }

    /** Next date of every event, soonest first: (event, day). */
    fun upcoming(from: Long): List<Pair<Event, Long>> {
        val y = Bs.fromEpoch(from)?.y ?: return emptyList()
        return all().mapNotNull { e ->
            val d = e.dayIn(y)?.takeIf { it >= from } ?: e.dayIn(y + 1)
            d?.let { e to it }
        }.sortedBy { it.second }
    }
}

/**
 * Morning notice (7:00 Nepal time): today's festivals and your events, plus tomorrow's events.
 * Also refreshes the home-screen widget every midnight.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        when (intent.action) {
            ACTION_MORNING -> morning(ctx)
            ACTION_MIDNIGHT -> PatroWidget.updateAll(ctx)
            else -> {}   // boot, time change, app update
        }
        PatroWidget.updateAll(ctx)
        schedule(ctx)
    }

    private fun morning(ctx: Context) {
        val today = Bs.todayEpoch()
        val info = try { Patro.day(today) } catch (_: Throwable) { null }
        val lines = ArrayList<String>()
        if (Prefs.festNotify) info?.fests?.forEach { lines.add(it.emoji + " " + it.name()) }
        for (e in Events.on(today)) lines.add(e.emoji + " " + e.name)
        for (e in Events.on(today + 1)) lines.add((if (Prefs.nepaliText) "भोलि: " else "Tomorrow: ") + e.emoji + " " + e.name)
        if (lines.isEmpty()) return
        val title = info?.let { Bs.format(it.bs) + " · " + Bs.dayName(it.weekday) } ?: "Yogi Patro"
        notify(ctx, title, lines.joinToString("\n"))
    }

    companion object {
        const val ACTION_MORNING = "com.yogi.patro.MORNING"
        const val ACTION_MIDNIGHT = "com.yogi.patro.MIDNIGHT"
        private const val CHANNEL = "days"

        fun notify(ctx: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Festivals & reminders", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            @Suppress("DEPRECATION")
            val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, CHANNEL) else android.app.Notification.Builder(ctx)
            val n = b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text.lineSequence().first())
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { nm.notify(1, n) } catch (_: Exception) {}
        }

        /** Next 7:00 and next midnight, Nepal time. Inexact alarms: no special permission, kind to the battery. */
        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC_WAKEUP, next(7, 0), pi(ctx, ACTION_MORNING, 1))
            am.set(AlarmManager.RTC, next(0, 1), pi(ctx, ACTION_MIDNIGHT, 2))
        }

        private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun next(h: Int, m: Int): Long {
            val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kathmandu"))
            val now = c.timeInMillis
            c.set(Calendar.HOUR_OF_DAY, h)
            c.set(Calendar.MINUTE, m)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= now + 1000) c.add(Calendar.DAY_OF_MONTH, 1)
            return c.timeInMillis
        }
    }
}
