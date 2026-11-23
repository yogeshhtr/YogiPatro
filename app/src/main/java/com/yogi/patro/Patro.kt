package com.yogi.patro

/** One festival or special day. [holiday] = usually a public holiday in Nepal (red in the calendar). */
class Fest(val np: String, val en: String, val holiday: Boolean = false, val emoji: String = "🪔") {
    fun name(nepali: Boolean = Prefs.nepaliText) = if (nepali) np else en
}

/** Everything about one day. */
class DayInfo(
    val day: Long,
    val bs: BsDate,
    val ad: AdDate,
    val weekday: Int,
    /** tithi at sunrise (udaya tithi), 1–30 */
    val tithi: Int,
    /** amanta lunar month at sunrise, 1 = Chaitra … 12 = Phalgun */
    val lunar: Int,
    val adhik: Boolean,
    val sunrise: Double,
    val sunset: Double,
    val fests: List<Fest>
) {
    val holiday: Boolean get() = Prefs.weeklyHoliday(weekday) || fests.any { it.holiday }
    fun tithiText(): String = Astro.lunarName(lunar, tithi, adhik) + " " + Astro.pakshaName(tithi) + " " + Astro.tithiName(tithi)
}

/**
 * The Patro engine: works out tithi, lunar months and festivals for a whole BS year at once (on a background
 * thread, about a fifth of a second) and keeps the last few years in memory.
 *
 * Festival days follow the usual Nepali rules: the tithi that rules at a set time of day decides the day
 * (sunrise for most, afternoon for Vijaya Dashami, midnight for Shivaratri and Janmashtami, sunset for
 * Laxmi Puja, Holi and Chhath). Dates are calculated, so a few may differ by a day from the official
 * government list in some years.
 */
object Patro {
    private enum class Kaal { SUNRISE, MORNING, NOON, AFTERNOON, SUNSET, MIDNIGHT }

    /** (amanta month, tithi, time of day, festival); last = take the last matching day (Laxmi Puja) */
    private class Rule(val month: Int, val tithi: Int, val kaal: Kaal, val fest: Fest, val last: Boolean = false)

    private val RULES = listOf(
        Rule(7, 1, Kaal.SUNRISE, Fest("घटस्थापना", "Ghatasthapana", true, "🌾")),
        Rule(7, 7, Kaal.SUNRISE, Fest("फूलपाती", "Fulpati", true, "🌼")),
        Rule(7, 8, Kaal.SUNRISE, Fest("महाअष्टमी", "Maha Ashtami", true, "🪔")),
        Rule(7, 9, Kaal.SUNRISE, Fest("महानवमी", "Maha Navami", true, "🪔")),
        Rule(7, 10, Kaal.AFTERNOON, Fest("विजया दशमी (दशैं टीका)", "Vijaya Dashami (Dashain Tika)", true, "🔴")),
        Rule(7, 11, Kaal.SUNRISE, Fest("पापाङ्कुशा एकादशी", "Papankusha Ekadashi", true, "🪔")),
        Rule(7, 15, Kaal.MIDNIGHT, Fest("कोजाग्रत पूर्णिमा", "Kojagrat Purnima", false, "🌕")),
        Rule(7, 30, Kaal.SUNSET, Fest("लक्ष्मी पूजा", "Laxmi Puja", true, "🪔"), last = true),
        Rule(8, 1, Kaal.SUNRISE, Fest("गोवर्धन पूजा · म्ह: पूजा", "Govardhan Puja · Mha Puja", true, "🐂")),
        Rule(8, 2, Kaal.SUNRISE, Fest("भाइटीका", "Bhai Tika", true, "🌈")),
        Rule(8, 6, Kaal.SUNSET, Fest("छठ पर्व", "Chhath Parva", true, "🌅")),
        Rule(8, 11, Kaal.SUNRISE, Fest("हरिबोधिनी एकादशी", "Haribodhini Ekadashi", false, "🙏")),
        Rule(8, 29, Kaal.SUNRISE, Fest("बाला चतुर्दशी", "Bala Chaturdashi", false, "🪔")),
        Rule(9, 15, Kaal.SUNRISE, Fest("उधौली · योमरी पुन्ही", "Udhauli · Yomari Punhi", false, "🍡")),
        Rule(11, 1, Kaal.SUNRISE, Fest("सोनाम ल्होसार", "Sonam Lhosar", true, "🏔")),
        Rule(11, 5, Kaal.MORNING, Fest("बसन्त पञ्चमी · सरस्वती पूजा", "Basanta Panchami · Saraswati Puja", false, "📚")),
        Rule(11, 29, Kaal.MIDNIGHT, Fest("महाशिवरात्रि", "Maha Shivaratri", true, "🔱")),
        Rule(12, 1, Kaal.SUNRISE, Fest("ग्याल्पो ल्होसार", "Gyalpo Lhosar", true, "🏔")),
        Rule(12, 15, Kaal.SUNSET, Fest("फागु पूर्णिमा (होली)", "Fagu Purnima (Holi)", true, "🎨")),
        Rule(12, 29, Kaal.SUNRISE, Fest("घोडेजात्रा", "Ghode Jatra", false, "🐎")),
        Rule(1, 8, Kaal.SUNRISE, Fest("चैते दशैं", "Chaite Dashain", false, "🪔")),
        Rule(1, 9, Kaal.NOON, Fest("रामनवमी", "Ram Navami", true, "🏹")),
        Rule(1, 30, Kaal.SUNRISE, Fest("मातातीर्थ औंसी (आमाको मुख हेर्ने दिन)", "Mata Tirtha Aunsi (Mother's Day)", false, "💐")),
        Rule(2, 15, Kaal.SUNRISE, Fest("बुद्ध जयन्ती", "Buddha Jayanti", true, "☸️")),
        Rule(4, 15, Kaal.SUNRISE, Fest("गुरु पूर्णिमा", "Guru Purnima", false, "🙏")),
        Rule(5, 5, Kaal.SUNRISE, Fest("नाग पञ्चमी", "Nag Panchami", false, "🐍")),
        Rule(5, 15, Kaal.SUNRISE, Fest("जनै पूर्णिमा · रक्षाबन्धन", "Janai Purnima · Raksha Bandhan", true, "🧵")),
        Rule(5, 16, Kaal.SUNRISE, Fest("गाईजात्रा", "Gai Jatra", false, "🐄")),
        Rule(5, 23, Kaal.MIDNIGHT, Fest("श्रीकृष्ण जन्माष्टमी", "Krishna Janmashtami", true, "🪈")),
        Rule(5, 30, Kaal.SUNRISE, Fest("कुशे औंसी (बुबाको मुख हेर्ने दिन)", "Kushe Aunsi (Father's Day)", false, "👨")),
        Rule(6, 3, Kaal.SUNRISE, Fest("हरितालिका तीज", "Haritalika Teej", true, "💃")),
        Rule(6, 5, Kaal.SUNRISE, Fest("ऋषि पञ्चमी", "Rishi Panchami", false, "🙏")),
        Rule(6, 14, Kaal.SUNRISE, Fest("इन्द्रजात्रा", "Indra Jatra", false, "🎭"))
    )

    /** Days fixed in the BS calendar: (month, day, festival) */
    private val FIXED_BS = listOf(
        Triple(1, 1, Fest("नयाँ वर्ष", "Nepali New Year", true, "🎉")),
        Triple(1, 11, Fest("लोकतन्त्र दिवस", "Loktantra Diwas", true, "🇳🇵")),
        Triple(1, 18, Fest("मजदुर दिवस", "Labour Day", false, "🛠")),
        Triple(2, 15, Fest("गणतन्त्र दिवस", "Ganatantra Diwas", true, "🇳🇵")),
        Triple(3, 15, Fest("दही चिउरा खाने दिन · रोपाइँ", "Dahi Chiura · Ropain Day", false, "🌾")),
        Triple(4, 1, Fest("साउने संक्रान्ति", "Saune Sankranti", false, "🌿")),
        Triple(6, 3, Fest("संविधान दिवस", "Constitution Day", true, "🇳🇵")),
        Triple(9, 15, Fest("तमु ल्होसार", "Tamu Lhosar", true, "🏔")),
        Triple(9, 27, Fest("पृथ्वी जयन्ती", "Prithvi Jayanti", true, "🇳🇵")),
        Triple(10, 1, Fest("माघे संक्रान्ति", "Maghe Sankranti", true, "🍠")),
        Triple(10, 16, Fest("शहीद दिवस", "Martyrs' Day", false, "🕯")),
        Triple(11, 7, Fest("प्रजातन्त्र दिवस", "Prajatantra Diwas", true, "🇳🇵"))
    )

    /** Days fixed in the Gregorian calendar: (month, day, festival) */
    private val FIXED_AD = listOf(
        Triple(1, 1, Fest("अङ्ग्रेजी नयाँ वर्ष", "New Year's Day", false, "🎆")),
        Triple(3, 8, Fest("अन्तर्राष्ट्रिय नारी दिवस", "International Women's Day", true, "👩")),
        Triple(12, 25, Fest("क्रिसमस", "Christmas", true, "🎄"))
    )

    private val cache = LinkedHashMap<Int, Map<Long, DayInfo>>()

    @Synchronized
    fun year(y: Int): Map<Long, DayInfo> {
        cache[y]?.let { return it }
        val built = build(y)
        cache[y] = built
        while (cache.size > 4) cache.remove(cache.keys.first())
        return built
    }

    fun day(day: Long): DayInfo? {
        val b = Bs.fromEpoch(day) ?: return null
        return year(b.y)[day]
    }

    /** Clears the cache (new city chosen). */
    @Synchronized
    fun reset() { cache.clear() }

    private fun build(y: Int): Map<Long, DayInfo> {
        if (!Bs.valid(y)) return emptyMap()
        val start = Bs.toEpoch(BsDate(y, 1, 1))
        val end = start + Bs.daysInYear(y) - 1
        // margin so lunar months that start before Baisakh 1 or end after Chaitra are complete
        val a = start - 45
        val b = end + 45

        // new moons in the range, with the Sun's rashi at each (for month names and adhik maas)
        val moons = ArrayList<Double>()
        var prevJd = Astro.jd(a, 720.0)
        var prevE = Astro.elongation(prevJd)
        for (d in a + 1..b + 35) {
            val jd = Astro.jd(d, 720.0)
            val e = Astro.elongation(jd)
            if (e < prevE) moons.add(Astro.newMoonBetween(prevJd, jd))
            prevJd = jd
            prevE = e
        }
        val rashis = moons.map { Astro.rashi(it) }

        val n = (b - a + 2).toInt()
        val tithi = IntArray(n)
        val month = IntArray(n)
        val adhik = BooleanArray(n)
        val rise = DoubleArray(n)
        val set = DoubleArray(n)
        for (i in 0 until n) {
            val d = a + i
            rise[i] = Astro.sunrise(d)
            set[i] = Astro.sunset(d)
            val jd = Astro.jd(d, rise[i])
            tithi[i] = Astro.tithi(jd)
            var k = -1
            for (j in moons.indices) if (moons[j] <= jd) k = j else break
            if (k >= 0) {
                month[i] = ((rashis[k] - 11) % 12 + 12) % 12 + 1
                adhik[i] = k + 1 < rashis.size && rashis[k + 1] == rashis[k]
            }
        }

        val fests = HashMap<Long, MutableList<Fest>>()
        fun add(d: Long, f: Fest) { fests.getOrPut(d) { ArrayList() }.add(f) }

        fun kaalMin(k: Kaal, i: Int): Double = when (k) {
            Kaal.SUNRISE -> rise[i]
            Kaal.MORNING -> 630.0
            Kaal.NOON -> 720.0
            Kaal.AFTERNOON -> 840.0
            Kaal.SUNSET -> set[i]
            Kaal.MIDNIGHT -> 1440.0
        }

        // lunar festivals, once per lunar month instance (a year can hold the same month twice at its ends)
        for (rule in RULES) {
            var i = 0
            while (i < n - 1) {
                if (month[i] != rule.month || adhik[i]) { i++; continue }
                // this run of days belongs to one lunar month
                var j = i
                while (j < n - 1 && month[j] == rule.month && !adhik[j]) j++
                var hit = -1
                for (x in i until j) {
                    val t = if (rule.kaal == Kaal.SUNRISE) tithi[x] else Astro.tithi(Astro.jd(a + x, kaalMin(rule.kaal, x)))
                    if (t == rule.tithi) {
                        hit = x
                        if (!rule.last) break
                    }
                }
                if (hit < 0) {
                    // the tithi was skipped (kshaya): it belongs to the day in which it falls
                    for (x in i until j) {
                        val t0 = tithi[x]
                        val t1 = tithi[x + 1]
                        if (((rule.tithi - t0 + 30) % 30) in 1 until ((t1 - t0 + 30) % 30)) { hit = x; break }
                    }
                }
                if (hit >= 0) {
                    val d = a + hit
                    if (d in start..end) add(d, rule.fest)
                    // Tihar: Kaag and Kukur Tihar are the two days before Laxmi Puja
                    if (rule.tithi == 30 && rule.month == 7) {
                        if (d - 2 in start..end) add(d - 2, Fest("काग तिहार", "Kaag Tihar", false, "🐦"))
                        if (d - 1 in start..end) add(d - 1, Fest("कुकुर तिहार · नरक चतुर्दशी", "Kukur Tihar", false, "🐕"))
                    }
                }
                i = j
            }
        }
        for ((m, dd, f) in FIXED_BS) if (dd <= Bs.daysIn(y, m)) add(Bs.toEpoch(BsDate(y, m, dd)), f)
        for (d in start..end) {
            val ad = Bs.adFromEpoch(d)
            for ((m, dd, f) in FIXED_AD) if (ad.m == m && ad.d == dd) add(d, f)
        }

        val out = HashMap<Long, DayInfo>()
        for (d in start..end) {
            val i = (d - a).toInt()
            out[d] = DayInfo(d, Bs.fromEpoch(d)!!, Bs.adFromEpoch(d), Bs.weekday(d), tithi[i], month[i], adhik[i], rise[i], set[i],
                fests[d] ?: emptyList())
        }
        return out
    }

    /** Next festivals from [from] on (this year and the next). */
    fun upcoming(from: Long, max: Int = 8): List<DayInfo> {
        val b = Bs.fromEpoch(from) ?: return emptyList()
        val out = ArrayList<DayInfo>()
        for (y in listOf(b.y, b.y + 1)) {
            if (!Bs.valid(y)) continue
            year(y).values.filter { it.day >= from && it.fests.isNotEmpty() }.sortedBy { it.day }.forEach { out.add(it) }
            if (out.size >= max) break
        }
        return out.take(max)
    }
}
