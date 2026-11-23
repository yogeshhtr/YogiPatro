package com.yogi.patro

import java.util.Calendar
import java.util.TimeZone

/** A Bikram Sambat date. [m] is 1 (Baisakh) … 12 (Chaitra). */
data class BsDate(val y: Int, val m: Int, val d: Int) : Comparable<BsDate> {
    override fun compareTo(other: BsDate): Int = compareValuesBy(this, other, { it.y }, { it.m }, { it.d })
}

/** A plain day in the Gregorian calendar. [m] is 1 … 12. */
data class AdDate(val y: Int, val m: Int, val d: Int)

/**
 * Bikram Sambat ↔ Gregorian conversion. Month lengths for BS 1975–2100 come from the official tables
 * (as published in the nepali-datetime project); checked against known dates such as
 * 1 Baisakh 2000 = 14 Apr 1943 and 1 Baisakh 2081 = 13 Apr 2024.
 */
object Bs {
    const val FIRST = 1975
    const val LAST = 2100

    /** one line per year, each digit = month length − 29 */
    private const val TABLE =
        "223321101011" +  // 1975
        "232321110012" +  // 1976
        "132321110102" +  // 1977
        "223222101011" +  // 1978
        "223321101011" +  // 1979
        "232321110012" +  // 1980
        "222322011011" +  // 1981
        "223222101011" +  // 1982
        "223321101011" +  // 1983
        "232321110012" +  // 1984
        "222322011011" +  // 1985
        "223222101011" +  // 1986
        "232321101011" +  // 1987
        "232321110012" +  // 1988
        "222322011011" +  // 1989
        "223222101011" +  // 1990
        "232321110011" +  // 1991
        "232321110102" +  // 1992
        "223222101011" +  // 1993
        "223222101011" +  // 1994
        "232321110011" +  // 1995
        "232321110102" +  // 1996
        "223222101011" +  // 1997
        "223222101011" +  // 1998
        "232321110012" +  // 1999
        "132321110102" +  // 2000
        "223222101011" +  // 2001
        "223321101011" +  // 2002
        "232321110012" +  // 2003
        "132321110102" +  // 2004
        "223222101011" +  // 2005
        "223321101011" +  // 2006
        "232321110012" +  // 2007
        "222322011002" +  // 2008
        "223222101011" +  // 2009
        "223321101011" +  // 2010
        "232321110012" +  // 2011
        "222322011011" +  // 2012
        "223222101011" +  // 2013
        "223321101011" +  // 2014
        "232321110012" +  // 2015
        "222322011011" +  // 2016
        "223222101011" +  // 2017
        "232321101011" +  // 2018
        "232321110102" +  // 2019
        "222322101011" +  // 2020
        "223222101011" +  // 2021
        "232321110011" +  // 2022
        "232321110102" +  // 2023
        "222322101011" +  // 2024
        "223222101011" +  // 2025
        "232321110012" +  // 2026
        "132321110102" +  // 2027
        "223222101011" +  // 2028
        "223231101011" +  // 2029
        "232321110012" +  // 2030
        "132321110102" +  // 2031
        "223222101011" +  // 2032
        "223321101011" +  // 2033
        "232321110012" +  // 2034
        "132322011002" +  // 2035
        "223222101011" +  // 2036
        "223321101011" +  // 2037
        "232321110012" +  // 2038
        "222322011011" +  // 2039
        "223222101011" +  // 2040
        "223321101011" +  // 2041
        "232321110012" +  // 2042
        "222322011011" +  // 2043
        "223222101011" +  // 2044
        "232321101011" +  // 2045
        "232321110012" +  // 2046
        "222322101011" +  // 2047
        "223222101011" +  // 2048
        "232321110011" +  // 2049
        "232321110102" +  // 2050
        "222322101011" +  // 2051
        "223222101011" +  // 2052
        "232321110011" +  // 2053
        "232321110102" +  // 2054
        "223222101011" +  // 2055
        "223231101011" +  // 2056
        "232321110012" +  // 2057
        "132321110102" +  // 2058
        "223222101011" +  // 2059
        "223321101011" +  // 2060
        "232321110012" +  // 2061
        "222322010102" +  // 2062
        "223222101011" +  // 2063
        "223321101011" +  // 2064
        "232321110012" +  // 2065
        "222322011002" +  // 2066
        "223222101011" +  // 2067
        "223321101011" +  // 2068
        "232321110012" +  // 2069
        "222322011011" +  // 2070
        "223222101011" +  // 2071
        "232321101011" +  // 2072
        "232321110012" +  // 2073
        "222322101011" +  // 2074
        "223222101011" +  // 2075
        "232321110011" +  // 2076
        "232321110102" +  // 2077
        "222322101011" +  // 2078
        "223222101011" +  // 2079
        "232321110011" +  // 2080
        "232321110102" +  // 2081
        "223222101011" +  // 2082
        "223222101011" +  // 2083
        "223221110111" +  // 2084
        "232312110111" +  // 2085
        "132321110111" +  // 2086
        "223222101111" +  // 2087
        "123312110111" +  // 2088
        "132321110111" +  // 2089
        "132321110111" +  // 2090
        "223222110111" +  // 2091
        "123321110111" +  // 2092
        "132321110111" +  // 2093
        "223221110111" +  // 2094
        "223222101111" +  // 2095
        "123321101011" +  // 2096
        "232321110111" +  // 2097
        "223222010102" +  // 2098
        "223222100111" +  // 2099
        "232312101011"     // 2100

    /** 1 Baisakh 1975 = 13 April 1918 */
    private val EPOCH = epochDay(1918, 4, 13)

    private val lengths: Array<IntArray> = Array(LAST - FIRST + 1) { y ->
        IntArray(12) { m -> TABLE[y * 12 + m] - '0' + 29 }
    }
    /** day number of 1 Baisakh of every year */
    private val yearStart: LongArray = LongArray(LAST - FIRST + 2).also { a ->
        a[0] = EPOCH
        for (i in 0..LAST - FIRST) a[i + 1] = a[i] + lengths[i].sum()
    }

    fun valid(y: Int): Boolean = y in FIRST..LAST

    fun daysIn(y: Int, m: Int): Int = if (valid(y) && m in 1..12) lengths[y - FIRST][m - 1] else 30

    fun daysInYear(y: Int): Int = if (valid(y)) lengths[y - FIRST].sum() else 365

    /** Days since 1970-01-01 for a Gregorian date. */
    fun epochDay(y: Int, m: Int, d: Int): Long {
        // civil-from-days inverse (Howard Hinnant), exact for all dates
        val yy = if (m <= 2) y - 1 else y
        val era = (if (yy >= 0) yy else yy - 399) / 400
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    fun adFromEpoch(z0: Long): AdDate {
        val z = z0 + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return AdDate(y, m, d)
    }

    fun toEpoch(b: BsDate): Long {
        var n = yearStart[b.y - FIRST]
        for (i in 0 until b.m - 1) n += lengths[b.y - FIRST][i]
        return n + b.d - 1
    }

    /** null when the day is outside BS 1975–2100 */
    fun fromEpoch(day: Long): BsDate? {
        if (day < yearStart[0] || day >= yearStart[yearStart.size - 1]) return null
        var yi = 0
        while (yearStart[yi + 1] <= day) yi++
        var left = (day - yearStart[yi]).toInt()
        var m = 0
        while (left >= lengths[yi][m]) {
            left -= lengths[yi][m]
            m++
        }
        return BsDate(FIRST + yi, m + 1, left + 1)
    }

    fun toAd(b: BsDate): AdDate = adFromEpoch(toEpoch(b))
    fun toBs(a: AdDate): BsDate? = fromEpoch(epochDay(a.y, a.m, a.d))

    /** 0 = Sunday (आइतबार) … 6 = Saturday (शनिबार) */
    fun weekday(day: Long): Int = (((day + 4) % 7 + 7) % 7).toInt()

    /** Today in Nepal (Nepal time, UTC+5:45), as a day number. */
    fun todayEpoch(): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kathmandu"))
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun today(): BsDate = fromEpoch(todayEpoch()) ?: BsDate(2083, 1, 1)

    fun plusMonths(y: Int, m: Int, k: Int): Pair<Int, Int> {
        var t = (y * 12 + (m - 1)) + k
        t = t.coerceIn(FIRST * 12, LAST * 12 + 11)
        return (t / 12) to (t % 12 + 1)
    }

    // ------------------------------------------------------------------ names

    val MONTHS_NP = listOf("बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज", "कार्तिक", "मंसिर", "पुष", "माघ", "फागुन", "चैत")
    val MONTHS_EN = listOf("Baisakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Asoj", "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra")
    val DAYS_NP = listOf("आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहीबार", "शुक्रबार", "शनिबार")
    val DAYS_SHORT_NP = listOf("आइत", "सोम", "मंगल", "बुध", "बिही", "शुक्र", "शनि")
    val DAYS_EN = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val DAYS_SHORT_EN = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val AD_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
        "November", "December")

    private const val DIGITS = "०१२३४५६७८९"

    /** Nepali digits (or Western ones when the user prefers them). */
    fun num(n: Number, nepali: Boolean = Prefs.nepaliDigits): String {
        val s = n.toString()
        if (!nepali) return s
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch in '0'..'9') DIGITS[ch - '0'] else ch)
        return sb.toString()
    }

    fun monthName(m: Int, nepali: Boolean = Prefs.nepaliText): String = (if (nepali) MONTHS_NP else MONTHS_EN)[(m - 1).coerceIn(0, 11)]
    fun dayName(w: Int, nepali: Boolean = Prefs.nepaliText): String = (if (nepali) DAYS_NP else DAYS_EN)[w]
    fun dayShort(w: Int, nepali: Boolean = Prefs.nepaliText): String = (if (nepali) DAYS_SHORT_NP else DAYS_SHORT_EN)[w]

    /** "७ मंसिर २०८३" */
    fun format(b: BsDate): String = num(b.d) + " " + monthName(b.m) + " " + num(b.y)

    fun formatAd(a: AdDate): String = "${a.d} ${AD_MONTHS[a.m - 1]} ${a.y}"
}
