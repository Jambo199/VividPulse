package com.jambo199.vividpulse.widget

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.R
import com.jambo199.vividpulse.alerts.Notify
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.SteamSales
import com.jambo199.vividpulse.system.BatteryReader
import com.jambo199.vividpulse.system.BatterySample
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** The home screen widgets. */
object Widgets {
    private fun ids(context: Context, provider: Class<out AppWidgetProvider>): IntArray =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider)) }.getOrDefault(IntArray(0))

    /** Battery, temperature and RAM. Cheap: called whenever there's a fresh reading. */
    fun updateVitals(context: Context, sample: BatterySample? = null) {
        val ids = ids(context, VitalsWidget::class.java)
        if (ids.isEmpty()) return
        val views = vitalsViews(context, sample ?: BatteryReader(context).sample())
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
    }

    fun vitalsViews(context: Context, b: BatterySample): RemoteViews {
        val mem = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it) }
        val level = b.level.roundToInt()
        val watts = b.watts?.takeIf { it > 0.5f && b.charging }
        val views = RemoteViews(context.packageName, R.layout.widget_vitals)
        views.setTextViewText(R.id.w_battery, "$level%" + if (b.charging) " ⚡" else "")
        views.setProgressBar(R.id.w_battery_bar, 100, level, false)
        views.setTextViewText(
            R.id.w_battery_label,
            when {
                b.full -> "Full"
                b.charging -> watts?.let { "Charging · ${Format.watts(it)}" } ?: "Charging"
                else -> "Battery"
            },
        )
        views.setTextViewText(R.id.w_temp, b.temperatureC?.let { Format.temperature(it, Graph.settings.current.fahrenheit) } ?: "—")
        if (mem.totalMem > 0) {
            views.setTextViewText(R.id.w_ram, Format.bytes(mem.totalMem - mem.availMem))
            views.setTextViewText(R.id.w_ram_label, "of ${Format.bytes(mem.totalMem, 0)} RAM")
        }
        views.setOnClickPendingIntent(R.id.vitals_root, Notify.openApp(context, "live", 10))
        return views
    }

    /** The top Steam deals (from the last list fetched) and Steam's next big sale. */
    suspend fun updateDeals(context: Context) {
        val ids = ids(context, DealsWidget::class.java)
        if (ids.isEmpty()) return
        val views = dealsViews(context)
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) }
    }

    suspend fun dealsViews(context: Context): RemoteViews {
        val deals = Graph.games.cached(Chart.DEALS)?.games.orEmpty().take(3)
        val pictures = deals.map { g -> withTimeoutOrNull(6_000) { picture(context, g) } }
        val views = RemoteViews(context.packageName, R.layout.widget_deals)
        views.setTextViewText(R.id.deals_sale, saleLine(Instant.now()))
        views.setOnClickPendingIntent(R.id.deals_header, Notify.openApp(context, "games/deals", 20))
        val rows = listOf(
            Row(R.id.deal1, R.id.deal1_image, R.id.deal1_name, R.id.deal1_price, R.id.deal1_off),
            Row(R.id.deal2, R.id.deal2_image, R.id.deal2_name, R.id.deal2_price, R.id.deal2_off),
            Row(R.id.deal3, R.id.deal3_image, R.id.deal3_name, R.id.deal3_price, R.id.deal3_off),
        )
        views.setViewVisibility(R.id.deals_empty, if (deals.isEmpty()) View.VISIBLE else View.GONE)
        rows.forEachIndexed { i, r ->
            val g = deals.getOrNull(i)
            views.setViewVisibility(r.root, if (g == null) View.GONE else View.VISIBLE)
            if (g == null) return@forEachIndexed
            views.setTextViewText(r.name, g.name)
            views.setTextViewText(r.price, listOfNotNull(g.price, g.originalPrice?.let { "was $it" }).joinToString("  "))
            views.setTextViewText(r.off, "-${g.discountPercent}%")
            views.setViewVisibility(r.off, if (g.discountPercent > 0) View.VISIBLE else View.GONE)
            pictures.getOrNull(i)?.let { views.setImageViewBitmap(r.image, it) }
            views.setOnClickPendingIntent(r.root, Notify.openApp(context, "game/${g.appId}", 21 + i))
        }
        return views
    }

    /** Asks the home screen to add a widget (the launcher shows its own confirmation). */
    fun pin(context: Context, provider: Class<out AppWidgetProvider>): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return runCatching { mgr.requestPinAppWidget(ComponentName(context, provider), null, null) }.getOrDefault(false)
    }

    /** "Winter Sale on · until Mon 4 Jan", or "Winter Sale in 65 days". */
    fun saleLine(now: Instant): String {
        val zone = ZoneId.systemDefault()
        SteamSales.current(now)?.let { s ->
            return "${s.name.removePrefix("Steam ")} on · until ${DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(s.end.atZone(zone))}"
        }
        val next = SteamSales.next(now) ?: return ""
        val days = Duration.between(now, next.start).toDays()
        return next.name.removePrefix("Steam ") + when (days) {
            0L -> " starts today"
            1L -> " tomorrow"
            else -> " in $days days"
        }
    }

    private suspend fun picture(context: Context, g: Game): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(g.imageUrl)
            .size(258, 120)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
        context.imageLoader.execute(request).drawable?.toBitmap(258, 120)
    }.getOrNull()

    private data class Row(val root: Int, val image: Int, val name: Int, val price: Int, val off: Int)
}

/** The vitals widget: battery, charging, temperature, RAM. */
class VitalsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateVitals(context)
    }
}

/** The Steam deals widget. */
class DealsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Graph.scope.launch(Dispatchers.IO) {
            try {
                Widgets.updateDeals(context)
            } finally {
                pending.finish()
            }
        }
    }
}
