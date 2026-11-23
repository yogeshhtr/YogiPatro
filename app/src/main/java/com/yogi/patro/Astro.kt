package com.yogi.patro

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun and Moon positions (Meeus, "Astronomical Algorithms", main terms), tithi, sunrise and sunset.
 * Accurate to well under a minute of arc, so tithi changes are found to within a few minutes.
 * Times are Nepal time (UTC+5:45); places are given by latitude / longitude.
 */
object Astro {
    private fun r(d: Double) = Math.toRadians(d)
    private fun norm(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

    /** Julian day for a day number (days since 1970-01-01) and minutes after midnight, Nepal time. */
    fun jd(day: Long, minutes: Double): Double = 2440587.5 + day + (minutes - 345.0) / 1440.0

    fun sunLon(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(r(m)) + (0.019993 - 0.000101 * t) * sin(r(2 * m)) +
            0.000289 * sin(r(3 * m))
        val om = 125.04 - 1934.136 * t
        return norm(l0 + c - 0.00569 - 0.00478 * sin(r(om)))
    }

    // D, M, M', F, coefficient (millionths of a degree)
    private val TERMS = intArrayOf(
        0, 0, 1, 0, 6288774, 2, 0, -1, 0, 1274027, 2, 0, 0, 0, 658314, 0, 0, 2, 0, 213618, 0, 1, 0, 0, -185116,
        0, 0, 0, 2, -114332, 2, 0, -2, 0, 58793, 2, -1, -1, 0, 57066, 2, 0, 1, 0, 53322, 2, -1, 0, 0, 45758,
        0, 1, -1, 0, -40923, 1, 0, 0, 0, -34720, 0, 1, 1, 0, -30383, 2, 0, 0, -2, 15327, 0, 0, 1, 2, -12528,
        0, 0, 1, -2, 10980, 4, 0, -1, 0, 10675, 0, 0, 3, 0, 10034, 4, 0, -2, 0, 8548, 2, 1, -1, 0, -7888,
        2, 1, 0, 0, -6766, 1, 0, -1, 0, -5163, 1, 1, 0, 0, 4987, 2, -1, 1, 0, 4036, 2, 0, 2, 0, 3994,
        4, 0, 0, 0, 3861, 2, 0, -3, 0, 3665, 0, 1, -2, 0, -2689, 2, 0, -1, 2, -2602, 2, -1, -2, 0, 2390,
        1, 0, 1, 0, -2348, 2, -2, 0, 0, 2236, 0, 1, 2, 0, -2120, 0, 2, 0, 0, -2069
    )

    fun moonLon(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525
        val lp = 218.3164477 + 481267.88123421 * t
        val d = 297.8501921 + 445267.1114034 * t
        val m = 357.5291092 + 35999.0502909 * t
        val mp = 134.9633964 + 477198.8675055 * t
        val f = 93.2720950 + 483202.0175233 * t
        val e = 1 - 0.002516 * t
        var s = 0.0
        var i = 0
        while (i < TERMS.size) {
            val km = TERMS[i + 1]
            val ef = if (km == 0) 1.0 else e.pow(abs(km))
            s += TERMS[i + 4] * ef * sin(r(TERMS[i] * d + km * m + TERMS[i + 2] * mp + TERMS[i + 3] * f))
            i += 5
        }
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        s += 3958 * sin(r(a1)) + 1962 * sin(r(lp - f)) + 318 * sin(r(a2))
        val om = 125.04 - 1934.136 * t
        return norm(lp + s / 1e6 - 0.00478 * sin(r(om)))
    }

    /** Moon minus Sun, 0–360°. */
    fun elongation(jd: Double): Double = norm(moonLon(jd) - sunLon(jd))

    /** 1–15 shukla paksha (15 = purnima), 16–30 krishna paksha (30 = aunsi). */
    fun tithi(jd: Double): Int = (floor(elongation(jd) / 12.0).toInt() + 1).coerceIn(1, 30)

    /** Lahiri ayanamsa, for the sidereal Sun (rashi / sankranti). */
    fun ayanamsa(jd: Double): Double = 23.853 + 0.013969 * ((jd - 2451545.0) / 365.25)

    /** 0 = Mesh (Aries) … 11 = Meen (Pisces), sidereal. */
    fun rashi(jd: Double): Int = (norm(sunLon(jd) - ayanamsa(jd)) / 30.0).toInt().coerceIn(0, 11)

    // ------------------------------------------------------------------ sunrise / sunset (NOAA)

    private fun dayOfYear(day: Long): Int {
        val a = Bs.adFromEpoch(day)
        return (day - Bs.epochDay(a.y, 1, 1)).toInt() + 1
    }

    /** [rise] true = sunrise, false = sunset; minutes after midnight, Nepal time. */
    fun sunMinutes(day: Long, lat: Double, lon: Double, rise: Boolean): Double {
        val g = 2 * Math.PI / 365 * (dayOfYear(day) - 1 + ((if (rise) 6 else 18) - 12) / 24.0)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val de = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val x = (cos(r(90.833)) / (cos(r(lat)) * cos(de)) - tan(r(lat)) * tan(de)).coerceIn(-1.0, 1.0)
        val ha = Math.toDegrees(acos(x))
        val utc = if (rise) 720 - 4 * (lon + ha) - eq else 720 - 4 * (lon - ha) - eq
        return utc + 345.0
    }

    fun sunrise(day: Long): Double = sunMinutes(day, Prefs.lat, Prefs.lon, true)
    fun sunset(day: Long): Double = sunMinutes(day, Prefs.lat, Prefs.lon, false)

    /** "6:42" */
    fun clock(min: Double): String {
        val m = ((min.toInt() % 1440) + 1440) % 1440
        return "${m / 60}:" + (m % 60).toString().padStart(2, '0')
    }

    // ------------------------------------------------------------------ new moons

    /** Exact time (Julian day) of the new moon between [a] and [b] (elongation wraps from 360 to 0). */
    fun newMoonBetween(a: Double, b: Double): Double {
        var lo = a
        var hi = b
        repeat(32) {
            val mid = (lo + hi) / 2
            if (elongation(mid) > 180.0) lo = mid else hi = mid
        }
        return hi
    }

    val TITHI_NP = listOf("प्रतिपदा", "द्वितीया", "तृतीया", "चतुर्थी", "पञ्चमी", "षष्ठी", "सप्तमी", "अष्टमी", "नवमी", "दशमी",
        "एकादशी", "द्वादशी", "त्रयोदशी", "चतुर्दशी", "पूर्णिमा")
    val TITHI_EN = listOf("Pratipada", "Dwitiya", "Tritiya", "Chaturthi", "Panchami", "Shashthi", "Saptami", "Ashtami", "Navami",
        "Dashami", "Ekadashi", "Dwadashi", "Trayodashi", "Chaturdashi", "Purnima")
    /** amanta month 1 = Chaitra … 12 = Phalgun */
    val LUNAR_NP = listOf("चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ", "श्रावण", "भाद्र", "आश्विन", "कार्तिक", "मार्ग", "पौष", "माघ", "फाल्गुन")
    val LUNAR_EN = listOf("Chaitra", "Baisakh", "Jestha", "Asar", "Shrawan", "Bhadra", "Ashwin", "Kartik", "Mangsir", "Poush", "Magh", "Falgun")

    fun tithiName(t: Int, nepali: Boolean = Prefs.nepaliText): String = when {
        t == 30 -> if (nepali) "औंसी" else "Aunsi"
        t == 15 -> if (nepali) "पूर्णिमा" else "Purnima"
        else -> (if (nepali) TITHI_NP else TITHI_EN)[(t - 1) % 15]
    }

    fun pakshaName(t: Int, nepali: Boolean = Prefs.nepaliText): String =
        if (t <= 15) (if (nepali) "शुक्ल पक्ष" else "Shukla paksha") else (if (nepali) "कृष्ण पक्ष" else "Krishna paksha")

    /** Lunar month as Nepali patro show it: the krishna paksha already carries the next month's name. */
    fun lunarName(amanta: Int, t: Int, adhik: Boolean, nepali: Boolean = Prefs.nepaliText): String {
        val idx = if (t > 15) amanta % 12 + 1 else amanta
        val n = (if (nepali) LUNAR_NP else LUNAR_EN)[idx - 1]
        return if (adhik) (if (nepali) "अधिक $n" else "Adhik $n") else n
    }
}
