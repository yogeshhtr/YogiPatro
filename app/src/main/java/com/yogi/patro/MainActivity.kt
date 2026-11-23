package com.yogi.patro

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var content: FrameLayout
    private lateinit var bar: LinearLayout
    private var tab = 0
    private val h = Handler(Looper.getMainLooper())

    // calendar state
    private var calY = 2083
    private var calM = 1
    private var calSel = -1L

    private fun np(): Boolean = Prefs.nepaliText
    private fun t(nepali: String, english: String) = if (np()) nepali else english

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG
        val root = vbox()
        root.setBackgroundColor(C.BG)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
        bar = hbox()
        bar.setBackgroundColor(C.CARD)
        bar.setPadding(0, dp(4), 0, dp(6))
        root.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        val b = Bs.today()
        calY = b.y
        calM = b.m
        AlarmReceiver.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !Store.bool("asked_notif", false)) {
            Store.setBool("asked_notif", true)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        show(0)
    }

    override fun onResume() {
        super.onResume()
        PatroWidget.updateAll(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (tab != 0) show(0) else super.onBackPressed()
    }

    private fun drawBar() {
        bar.removeAllViews()
        val items = listOf("🪔" to t("आज", "Today"), "🗓" to t("पात्रो", "Calendar"), "🔁" to t("रूपान्तर", "Convert"),
            "🎂" to t("सम्झना", "Reminders"), "⚙️" to t("सेटिङ", "Settings"))
        for ((i, it) in items.withIndex()) {
            val col = vbox()
            col.gravity = Gravity.CENTER
            col.addView(label(it.first, 20f).apply { gravity = Gravity.CENTER })
            col.addView(label(it.second, 11f, if (i == tab) C.GOLD else C.SUB, i == tab).apply { gravity = Gravity.CENTER })
            col.setPadding(0, dp(4), 0, dp(2))
            col.setOnClickListener { show(i) }
            bar.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
    }

    private fun show(i: Int) {
        tab = i
        drawBar()
        content.removeAllViews()
        val (sv, col) = page()
        content.addView(sv)
        when (i) {
            0 -> today(col)
            1 -> calendar(col)
            2 -> converter(col)
            3 -> reminders(col)
            else -> settings(col)
        }
    }

    private fun page(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this)
        sv.isFillViewport = true
        val col = vbox()
        col.setPadding(0, dp(28), 0, dp(24))
        sv.addView(col)
        return sv to col
    }

    private fun header(big: String, small: String = ""): View = vbox().apply {
        setPadding(dp(20), dp(8), dp(20), dp(8))
        addView(label(big, 30f, C.TEXT, true))
        if (small.isNotEmpty()) addView(label(small, 14f, C.SUB))
    }

    private fun section(s: String): View = label(s, 13f, C.SUB, true).apply { setPadding(dp(22), dp(14), dp(22), dp(2)) }

    /** Computes the BS year in the background, then shows [then]. */
    private fun withYear(y: Int, col: LinearLayout, then: (Map<Long, DayInfo>) -> Unit) {
        val wait = label(t("गणना हुँदैछ…", "Working it out…"), 14f, C.SUB).apply { setPadding(dp(22), dp(10), dp(22), dp(10)) }
        col.addView(wait)
        val myTab = tab
        Thread {
            val data = try { Patro.year(y) } catch (_: Throwable) { emptyMap() }
            h.post {
                if (tab != myTab || isFinishing) return@post
                col.removeView(wait)
                then(data)
            }
        }.start()
    }

    private fun daysLeft(n: Long): String = when (n) {
        0L -> t("आज", "today")
        1L -> t("भोलि", "tomorrow")
        else -> if (np()) Bs.num(n) + " दिन बाँकी" else "in $n days"
    }

    // ===================================================================== today

    private fun today(col: LinearLayout) {
        val day = Bs.todayEpoch()
        val b = Bs.fromEpoch(day) ?: return
        val ad = Bs.adFromEpoch(day)
        val hero = card(C.CARD)
        hero.setPadding(dp(20), dp(20), dp(20), dp(20))
        hero.background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF3A1512.toInt(), 0xFF1F1715.toInt())).apply { cornerRadius = dpf(24f) }
        hero.addView(label(Bs.dayName(Bs.weekday(day)), 16f, C.GOLD, true))
        hero.addView(label(Bs.num(b.d) + " " + Bs.monthName(b.m), 40f, C.TEXT, true))
        hero.addView(label(Bs.num(b.y) + (if (np()) " साल" else " BS"), 18f, C.SUB))
        hero.addView(label("${Bs.DAYS_EN[Bs.weekday(day)]}, ${Bs.formatAd(ad)}", 14f, C.SUB).lin(t = dp(6)))
        val tithiLine = label("", 16f, C.TEXT)
        hero.addView(tithiLine.lin(t = dp(12)))
        val sunLine = label("", 14f, C.SUB)
        hero.addView(sunLine.lin(t = dp(4)))
        col.addView(hero)
        withYear(b.y, col) { data ->
            val info = data[day] ?: return@withYear
            tithiLine.text = "🌙  " + info.tithiText()
            sunLine.text = "🌅 " + Astro.clock(info.sunrise) + "    🌇 " + Astro.clock(info.sunset) + "    📍 " +
                Cities.ALL[Prefs.city].first.substringAfter("· ").let { if (np()) Cities.ALL[Prefs.city].first.substringBefore(" ·") else it }
            if (info.fests.isNotEmpty()) {
                val fc = card(0xFF2E1A10.toInt())
                fc.addView(label(t("आजको पर्व", "Today"), 13f, C.GOLD, true))
                for (f in info.fests) fc.addView(label(f.emoji + "  " + f.name(), 18f, C.TEXT, true).lin(t = dp(4)))
                col.addView(fc, 1)
            }
            val ev = Events.upcoming(day).take(4)
            if (ev.isNotEmpty()) {
                col.addView(section(t("तपाईंका सम्झना", "YOUR DAYS")))
                val c = card()
                for ((e, d) in ev) c.addView(row(e.emoji, e.name, Bs.fromEpoch(d)?.let { Bs.format(it) } ?: "", daysLeft(d - day)))
                col.addView(c)
            }
            col.addView(section(t("आउँदा पर्वहरू", "COMING FESTIVALS")))
            val up = Thread {
                val list = Patro.upcoming(day + 1, 10)
                h.post {
                    if (tab != 0) return@post
                    val c = card()
                    for (x in list) {
                        val f = x.fests.first()
                        c.addView(row(f.emoji, x.fests.joinToString(" · ") { it.name() }, Bs.format(x.bs) + " · " + Bs.dayShort(x.weekday),
                            daysLeft(x.day - day), f.holiday).apply { setOnClickListener { openDay(x.day) } })
                    }
                    col.addView(c)
                }
            }
            up.start()
        }
    }

    private fun row(icon: String, title: String, sub: String, right: String, red: Boolean = false): View {
        val r = hbox()
        r.setPadding(0, dp(8), 0, dp(8))
        r.addView(label(icon, 22f).lin(WRAP, WRAP, r = dp(12)))
        val mid = vbox()
        mid.addView(label(title, 15f, if (red) 0xFFFF8A80.toInt() else C.TEXT, true))
        if (sub.isNotEmpty()) mid.addView(label(sub, 12f, C.SUB))
        r.addView(mid.lin(0, WRAP, 1f))
        r.addView(label(right, 12f, C.GOLD, true))
        return r
    }

    private fun openDay(day: Long) {
        val b = Bs.fromEpoch(day) ?: return
        calY = b.y
        calM = b.m
        calSel = day
        show(1)
    }

    // ===================================================================== calendar

    private fun calendar(col: LinearLayout) {
        val head = hbox()
        head.setPadding(dp(12), dp(4), dp(12), dp(4))
        val prev = label("‹", 34f, C.GOLD, true).apply { setPadding(dp(14), 0, dp(14), 0) }
        val title = vbox()
        title.gravity = Gravity.CENTER
        val tt = label(Bs.monthName(calM) + " " + Bs.num(calY), 24f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        val first = Bs.toAd(BsDate(calY, calM, 1))
        val last = Bs.toAd(BsDate(calY, calM, Bs.daysIn(calY, calM)))
        val range = (if (first.m == last.m) Bs.AD_MONTHS[first.m - 1].take(3) else Bs.AD_MONTHS[first.m - 1].take(3) + " – " +
            Bs.AD_MONTHS[last.m - 1].take(3)) + " " + last.y
        title.addView(tt)
        title.addView(label(range, 13f, C.SUB).apply { gravity = Gravity.CENTER })
        val next = label("›", 34f, C.GOLD, true).apply { setPadding(dp(14), 0, dp(14), 0) }
        head.addView(prev)
        head.addView(title.lin(0, WRAP, 1f))
        head.addView(next)
        prev.setOnClickListener { move(-1) }
        next.setOnClickListener { move(1) }
        col.addView(head)
        val todayBtn = label(t("आज", "Today"), 13f, Color.BLACK, true).apply {
            background = ripple(C.GOLD, 14f)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                val b = Bs.today()
                calY = b.y
                calM = b.m
                calSel = Bs.todayEpoch()
                show(1)
            }
        }
        val tb = hbox()
        tb.gravity = Gravity.CENTER
        tb.addView(todayBtn)
        col.addView(tb.lin(b = dp(6)))

        val names = hbox()
        names.setPadding(dp(8), 0, dp(8), 0)
        for (w in 0..6) names.addView(label(Bs.dayShort(w), 12f, if (w == 6) C.RED else C.SUB, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        col.addView(names)
        val mv = MonthView(this)
        mv.year = calY
        mv.month = calM
        mv.selected = calSel
        mv.setPadding(dp(8), 0, dp(8), 0)
        col.addView(mv, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), dp(4), dp(8), 0) })
        mv.onSwipe = { move(it) }
        val detail = vbox()
        col.addView(detail)
        val list = vbox()
        col.addView(list)
        withYear(calY, col) { data ->
            mv.days = data
            mv.invalidate()
            fun showDetail(day: Long) {
                detail.removeAllViews()
                val info = data[day] ?: return
                val c = card(C.CARD2)
                c.addView(label(Bs.dayName(info.weekday) + ", " + Bs.format(info.bs), 18f, C.TEXT, true))
                c.addView(label("${Bs.DAYS_EN[info.weekday]}, ${Bs.formatAd(info.ad)}", 13f, C.SUB))
                c.addView(label("🌙  " + info.tithiText(), 15f, C.TEXT).lin(t = dp(8)))
                c.addView(label("🌅 " + Astro.clock(info.sunrise) + "    🌇 " + Astro.clock(info.sunset), 13f, C.SUB).lin(t = dp(2)))
                for (f in info.fests) c.addView(label(f.emoji + "  " + f.name() + if (f.holiday) t("  (बिदा)", "  (holiday)") else "",
                    15f, if (f.holiday) 0xFFFF8A80.toInt() else C.GOLD, true).lin(t = dp(6)))
                for (e in Events.on(day)) c.addView(label(e.emoji + "  " + e.name, 15f, C.GOLD, true).lin(t = dp(6)))
                val add = label(t("＋ यो दिन सम्झना थप्नुहोस्", "＋ Add a reminder on this day"), 14f, C.SAFFRON, true)
                add.setPadding(0, dp(10), 0, 0)
                add.setOnClickListener { editEvent(null, info.bs.m, info.bs.d) }
                c.addView(add)
                detail.addView(c)
            }
            mv.onPick = { day ->
                calSel = day
                mv.selected = day
                mv.invalidate()
                showDetail(day)
            }
            if (calSel >= 0 && data.containsKey(calSel)) showDetail(calSel)
            // festivals of this month
            val start = Bs.toEpoch(BsDate(calY, calM, 1))
            val fd = (0 until Bs.daysIn(calY, calM)).mapNotNull { data[start + it] }.filter { it.fests.isNotEmpty() }
            if (fd.isNotEmpty()) {
                list.addView(section(t("यस महिनाका पर्व", "FESTIVALS THIS MONTH")))
                val c = card()
                for (x in fd) c.addView(row(x.fests.first().emoji, x.fests.joinToString(" · ") { it.name() },
                    Bs.format(x.bs) + " · " + Bs.dayName(x.weekday), "${x.ad.d} ${Bs.AD_MONTHS[x.ad.m - 1].take(3)}",
                    x.fests.any { it.holiday }).apply {
                    setOnClickListener { mv.onPick?.invoke(x.day) }
                })
                list.addView(c)
            }
        }
    }

    private fun move(k: Int) {
        val (y, m) = Bs.plusMonths(calY, calM, k)
        if (y == calY && m == calM) return
        calY = y
        calM = m
        calSel = -1
        show(1)
    }

    // ===================================================================== converter

    private fun picker(min: Int, max: Int, value: Int, names: List<String>? = null): NumberPicker = NumberPicker(this).apply {
        minValue = min
        maxValue = max
        this.value = value.coerceIn(min, max)
        wrapSelectorWheel = false
        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        if (names != null) displayedValues = names.toTypedArray()
    }

    private fun converter(col: LinearLayout) {
        col.addView(header(t("मिति रूपान्तर", "Date converter"), t("बि.सं. ↔ ई.सं.", "BS ↔ AD")))
        // BS → AD
        val today = Bs.today()
        val c1 = card()
        c1.addView(label(t("बि.सं. बाट ई.सं.", "BS to AD"), 13f, C.GOLD, true))
        val r1 = hbox()
        r1.gravity = Gravity.CENTER
        val by = picker(Bs.FIRST, Bs.LAST, today.y, (Bs.FIRST..Bs.LAST).map { Bs.num(it) })
        val bm = picker(1, 12, today.m, (1..12).map { Bs.monthName(it) })
        val bd = picker(1, 32, today.d, (1..32).map { Bs.num(it) })
        r1.addView(by, LinearLayout.LayoutParams(0, WRAP, 1f))
        r1.addView(bm, LinearLayout.LayoutParams(0, WRAP, 1.3f))
        r1.addView(bd, LinearLayout.LayoutParams(0, WRAP, 0.8f))
        c1.addView(r1)
        val o1 = label("", 18f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        c1.addView(o1.lin(t = dp(6)))
        fun upd1() {
            val dmax = Bs.daysIn(by.value, bm.value)
            if (bd.value > dmax) bd.value = dmax
            val day = Bs.toEpoch(BsDate(by.value, bm.value, bd.value))
            val a = Bs.adFromEpoch(day)
            o1.text = Bs.DAYS_EN[Bs.weekday(day)] + ", " + Bs.formatAd(a) + "\n" + Bs.dayName(Bs.weekday(day))
        }
        for (p in listOf(by, bm, bd)) p.setOnValueChangedListener { _, _, _ -> upd1() }
        upd1()
        col.addView(c1)

        // AD → BS
        val ad = Bs.adFromEpoch(Bs.todayEpoch())
        val c2 = card()
        c2.addView(label(t("ई.सं. बाट बि.सं.", "AD to BS"), 13f, C.GOLD, true))
        val r2 = hbox()
        r2.gravity = Gravity.CENTER
        val ay = picker(1918, 2043, ad.y)
        val am = picker(1, 12, ad.m, Bs.AD_MONTHS.map { it.take(3) })
        val adp = picker(1, 31, ad.d)
        r2.addView(ay, LinearLayout.LayoutParams(0, WRAP, 1f))
        r2.addView(am, LinearLayout.LayoutParams(0, WRAP, 1.3f))
        r2.addView(adp, LinearLayout.LayoutParams(0, WRAP, 0.8f))
        c2.addView(r2)
        val o2 = label("", 18f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        c2.addView(o2.lin(t = dp(6)))
        fun upd2() {
            val cal = java.util.GregorianCalendar(ay.value, am.value - 1, 1)
            val dmax = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            if (adp.value > dmax) adp.value = dmax
            val day = Bs.epochDay(ay.value, am.value, adp.value)
            val b = Bs.fromEpoch(day)
            o2.text = if (b == null) t("यो मिति तालिकाभन्दा बाहिर छ", "Outside the BS table")
            else Bs.dayName(Bs.weekday(day)) + ", " + Bs.format(b)
        }
        for (p in listOf(ay, am, adp)) p.setOnValueChangedListener { _, _, _ -> upd2() }
        upd2()
        col.addView(c2)

        // age
        val c3 = card()
        c3.addView(label(t("उमेर (बि.सं. जन्ममितिबाट)", "Age from a BS birth date"), 13f, C.GOLD, true))
        val o3 = label("", 16f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        fun upd3() {
            val dmax = Bs.daysIn(by.value, bm.value)
            val born = BsDate(by.value, bm.value, bd.value.coerceAtMost(dmax))
            val now = Bs.today()
            if (born > now) { o3.text = t("जन्ममिति भविष्यमा छ", "That date is in the future"); return }
            var y = now.y - born.y
            var m = now.m - born.m
            var d = now.d - born.d
            if (d < 0) {
                m -= 1
                val (py, pm) = Bs.plusMonths(now.y, now.m, -1)
                d += Bs.daysIn(py, pm)
            }
            if (m < 0) { y -= 1; m += 12 }
            val total = Bs.todayEpoch() - Bs.toEpoch(born)
            o3.text = if (np()) "${Bs.num(y)} वर्ष ${Bs.num(m)} महिना ${Bs.num(d)} दिन\n(${Bs.num(total)} दिन)"
            else "$y years $m months $d days\n($total days)"
        }
        c3.addView(label(t("माथिको बि.सं. मितिलाई जन्ममिति मानिन्छ", "Uses the BS date chosen above"), 12f, C.SUB))
        c3.addView(o3.lin(t = dp(6)))
        for (p in listOf(by, bm, bd)) p.setOnValueChangedListener { _, _, _ -> upd1(); upd3() }
        upd3()
        col.addView(c3)
    }

    // ===================================================================== reminders

    private fun reminders(col: LinearLayout) {
        col.addView(header(t("सम्झना", "Reminders"), t("जन्मदिन, वार्षिकोत्सव, श्राद्ध — हरेक वर्ष बि.सं. मितिमा",
            "Birthdays, anniversaries, shraddha — every year on the BS date")))
        val add = label(t("＋  नयाँ सम्झना", "＋  New reminder"), 16f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER
            background = ripple(C.GOLD, 16f)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { val b = Bs.today(); editEvent(null, b.m, b.d) }
        }
        col.addView(add.lin(l = dp(14), r = dp(14), t = dp(4), b = dp(8)))
        val today = Bs.todayEpoch()
        val list = Events.upcoming(today)
        if (list.isEmpty()) {
            col.addView(label(t("अहिलेसम्म कुनै सम्झना छैन। बिहान ७ बजे सूचना आउँछ।",
                "No reminders yet. You'll get a notice at 7 am on the day."), 14f, C.SUB).apply { setPadding(dp(22), dp(8), dp(22), dp(8)) })
            return
        }
        val c = card()
        for ((e, d) in list) {
            val b = Bs.fromEpoch(d)
            val age = if (e.year > 0 && b != null) (if (np()) " · " + Bs.num(b.y - e.year) + " वर्ष" else " · turns ${b.y - e.year}") else ""
            val r = row(e.emoji, e.name, (b?.let { Bs.monthName(it.m) + " " + Bs.num(it.d) } ?: "") + age, daysLeft(d - today))
            r.setOnClickListener { editEvent(e, e.m, e.d) }
            c.addView(r)
        }
        col.addView(c)
    }

    private fun editEvent(e: Event?, m0: Int, d0: Int) {
        val box = vbox()
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val name = EditText(this).apply {
            hint = t("नाम (जस्तै: आमाको जन्मदिन)", "Name (e.g. Mum's birthday)")
            setText(e?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        box.addView(name)
        var kind = e?.kind ?: 0
        val kinds = hbox()
        fun drawKinds() {
            kinds.removeAllViews()
            val names = if (np()) Event.KINDS_NP else Event.KINDS_EN
            for (i in names.indices) kinds.addView(chip(Event.KINDS_EMOJI[i] + " " + names[i], i == kind) { kind = i; drawKinds() })
        }
        drawKinds()
        box.addView(HorizontalScrollView(this).apply { addView(kinds) }.lin(t = dp(8)))
        val r = hbox()
        r.gravity = Gravity.CENTER
        val pm = picker(1, 12, m0, (1..12).map { Bs.monthName(it) })
        val pd = picker(1, 32, d0, (1..32).map { Bs.num(it) })
        r.addView(pm, LinearLayout.LayoutParams(0, WRAP, 1.4f))
        r.addView(pd, LinearLayout.LayoutParams(0, WRAP, 1f))
        box.addView(r)
        val year = EditText(this).apply {
            hint = t("जन्म/सुरु वर्ष बि.सं. (ऐच्छिक, उमेरका लागि)", "Year in BS (optional, to show the age)")
            inputType = InputType.TYPE_CLASS_NUMBER
            if ((e?.year ?: 0) > 0) setText(e!!.year.toString())
        }
        box.addView(year)
        val dlg = AlertDialog.Builder(this)
            .setTitle(if (e == null) t("नयाँ सम्झना", "New reminder") else t("सम्झना", "Reminder"))
            .setView(box)
            .setPositiveButton(t("सुरक्षित", "Save")) { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { toast(t("नाम लेख्नुहोस्", "Write a name")); return@setPositiveButton }
                val y = year.text.toString().trim().toIntOrNull()?.takeIf { it in 1900..2200 } ?: 0
                Events.add(Event(e?.id ?: System.currentTimeMillis(), n, pm.value, pd.value, kind, y))
                show(tab)
            }
            .setNegativeButton(t("रद्द", "Cancel"), null)
        if (e != null) dlg.setNeutralButton(t("मेटाउनुहोस्", "Delete")) { _, _ -> Events.remove(e.id); show(tab) }
        dlg.show()
    }

    // ===================================================================== settings

    private fun choice(name: String, options: List<String>, sel: Int, onPick: (Int) -> Unit): View {
        val box = vbox()
        box.addView(label(name, 13f, C.SUB, true).lin(t = dp(10), b = dp(4)))
        val row = hbox()
        options.forEachIndexed { i, o -> row.addView(chip(o, i == sel) { if (i != sel) onPick(i) }) }
        box.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        return box
    }

    private fun settings(col: LinearLayout) {
        col.addView(header(t("सेटिङ", "Settings")))
        val c = card()
        c.addView(choice(t("अंक", "Numbers"), listOf("२०८३", "2083"), if (Prefs.nepaliDigits) 0 else 1) {
            Store.setBool("np_digits", it == 0); refreshAll()
        })
        c.addView(choice(t("भाषा", "Language"), listOf("नेपाली", "English"), if (Prefs.nepaliText) 0 else 1) {
            Store.setBool("np_text", it == 0); refreshAll()
        })
        c.addView(choice(t("सूर्योदय / सूर्यास्तका लागि सहर", "City for sunrise and sunset"),
            Cities.ALL.map { if (np()) it.first.substringBefore(" ·") else it.first.substringAfter("· ") }, Prefs.city) {
            Store.setInt("city", it)
            Patro.reset()
            refreshAll()
        })
        c.addView(choice(t("बिहान ७ बजे पर्वको सूचना", "Festival notice at 7 am"), listOf(t("हो", "On"), t("होइन", "Off")),
            if (Prefs.festNotify) 0 else 1) { Store.setBool("fest_notify", it == 0); show(4) })
        col.addView(c)
        val w = card()
        w.addView(label(t("होम स्क्रिन विजेट", "Home-screen widget"), 15f, C.TEXT, true))
        w.addView(label(t("होम स्क्रिनमा थिच्नुहोस् → विजेट → Yogi Patro। आजको मिति, तिथि र पर्व देखिन्छ।",
            "Hold the home screen → Widgets → Yogi Patro. Shows today's date, tithi and festival."), 13f, C.SUB).lin(t = dp(4)))
        col.addView(w)
        val a = card()
        a.addView(label("Yogi Patro", 15f, C.TEXT, true))
        a.addView(label(t("बि.सं. १९७५–२१०० का महिनाका दिनहरू आधिकारिक तालिकाबाट। तिथि, पर्व र सूर्योदय फोनमै खगोल गणनाले निकालिन्छ " +
            "(इन्टरनेट चाहिँदैन)। केही पर्वको मिति सरकारी सूचीभन्दा एक दिन फरक पर्न सक्छ।",
            "Month lengths for BS 1975–2100 come from the official tables. Tithi, festivals and sunrise are calculated on the phone " +
                "with real astronomy (no internet). A few festival dates may differ by a day from the government list in some years."),
            12f, C.SUB).lin(t = dp(4)))
        col.addView(a)
    }

    private fun refreshAll() {
        PatroWidget.updateAll(this)
        show(tab)
    }
}
