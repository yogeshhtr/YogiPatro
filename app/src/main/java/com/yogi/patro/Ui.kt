package com.yogi.patro

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Colours: warm night with vermilion and marigold, like a festival evening. */
object C {
    const val BG = 0xFF120D0C.toInt()
    const val CARD = 0xFF1F1715.toInt()
    const val CARD2 = 0xFF2C211E.toInt()
    const val TEXT = 0xFFFFF7EE.toInt()
    const val SUB = 0xFFB9A79C.toInt()
    const val RED = 0xFFE5383B.toInt()
    const val GOLD = 0xFFFFB627.toInt()
    const val SAFFRON = 0xFFFF7A1A.toInt()
    const val GREEN = 0xFF58C47A.toInt()
}

object Store {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("yogipatro", Context.MODE_PRIVATE)
    }
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun setStr(k: String, v: String) { sp.edit().putString(k, v).apply() }
    fun bool(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun setInt(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    fun arr(k: String): JSONArray = try { JSONArray(str(k, "[]")) } catch (_: Exception) { JSONArray() }
    fun setArr(k: String, a: JSONArray) { setStr(k, a.toString()) }
}

/** Cities for sunrise and sunset. */
object Cities {
    val ALL = listOf(
        Triple("काठमाडौं · Kathmandu", 27.7172, 85.3240), Triple("पोखरा · Pokhara", 28.2096, 83.9856),
        Triple("विराटनगर · Biratnagar", 26.4525, 87.2718), Triple("वीरगंज · Birgunj", 27.0104, 84.8770),
        Triple("भरतपुर · Bharatpur", 27.6766, 84.4353), Triple("बुटवल · Butwal", 27.7006, 83.4484),
        Triple("धरान · Dharan", 26.8065, 87.2846), Triple("जनकपुर · Janakpur", 26.7288, 85.9263),
        Triple("नेपालगञ्ज · Nepalgunj", 28.0500, 81.6167), Triple("धनगढी · Dhangadhi", 28.6833, 80.6000),
        Triple("इटहरी · Itahari", 26.6646, 87.2718), Triple("हेटौंडा · Hetauda", 27.4287, 85.0322),
        Triple("सुर्खेत · Surkhet", 28.6019, 81.6339), Triple("दमक · Damak", 26.6600, 87.7000)
    )
}

object Prefs {
    /** Nepali digits (२०८३) or Western (2083) */
    val nepaliDigits: Boolean get() = try { Store.bool("np_digits", true) } catch (_: Throwable) { true }
    /** Nepali names for months, days and festivals */
    val nepaliText: Boolean get() = try { Store.bool("np_text", true) } catch (_: Throwable) { true }
    val city: Int get() = try { Store.int("city", 0).coerceIn(0, Cities.ALL.size - 1) } catch (_: Throwable) { 0 }
    val lat: Double get() = Cities.ALL[city].second
    val lon: Double get() = Cities.ALL[city].third
    val festNotify: Boolean get() = try { Store.bool("fest_notify", true) } catch (_: Throwable) { true }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, strokeW: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeW > 0) setStroke(strokeW, strokeColor)
    }

fun Context.ripple(color: Int, radiusDp: Float): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(color, dpf(radiusDp)), null)

fun Context.label(s: CharSequence, size: Float = 15f, color: Int = C.TEXT, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun <T : View> T.lin(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): T {
    layoutParams = LinearLayout.LayoutParams(w, h, weight).also { it.setMargins(l, t, r, b) }
    return this
}

fun Context.card(color: Int = C.CARD): LinearLayout = vbox().apply {
    background = rounded(color, dpf(20f))
    setPadding(dp(16), dp(14), dp(16), dp(14))
    layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.setMargins(dp(14), dp(6), dp(14), dp(6)) }
}

fun Context.chip(s: String, selected: Boolean, onClick: () -> Unit): TextView =
    label(s, 14f, if (selected) Color.BLACK else C.TEXT, selected).apply {
        gravity = Gravity.CENTER
        background = ripple(if (selected) C.GOLD else C.CARD2, 14f)
        setPadding(dp(14), dp(9), dp(14), dp(9))
        setOnClickListener { onClick() }
    }.lin(WRAP, WRAP, r = dp(8), b = dp(8))

fun Context.toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
