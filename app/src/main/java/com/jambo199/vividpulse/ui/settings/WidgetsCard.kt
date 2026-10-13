package com.jambo199.vividpulse.ui.settings

import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jambo199.vividpulse.system.BatteryReader
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.PillButton
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.widget.DealsWidget
import com.jambo199.vividpulse.widget.VitalsWidget
import com.jambo199.vividpulse.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The home screen widgets as they look now, with a button to add each. */
@Composable
fun WidgetsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vitals by produceState<RemoteViews?>(null) {
        value = withContext(Dispatchers.IO) { Widgets.vitalsViews(context, BatteryReader(context).sample()) }
    }
    val deals by produceState<RemoteViews?>(null) {
        value = withContext(Dispatchers.IO) { Widgets.dealsViews(context) }
    }
    fun add(provider: Class<out android.appwidget.AppWidgetProvider>) {
        if (!Widgets.pin(context, provider)) {
            Toast.makeText(context, "Long-press your home screen, tap Widgets and find Vivid Pulse", Toast.LENGTH_LONG).show()
        }
    }
    GlassCard(modifier.fillMaxWidth(), accent = Accents.download) {
        CardTitle(Icons.Rounded.Widgets, "Home screen widgets", Accents.download)
        Text(
            "Your battery, temperature and RAM, and today's Steam deals, without opening the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        Preview(vitals, 76)
        Spacer(Modifier.height(8.dp))
        PillButton("Add vitals widget", Accents.download, { add(VitalsWidget::class.java) })
        Spacer(Modifier.height(14.dp))
        Preview(deals, 190)
        Spacer(Modifier.height(8.dp))
        PillButton("Add deals widget", Accents.download, { add(DealsWidget::class.java) })
    }
}

/** A widget drawn the way the home screen will draw it. */
@Composable
private fun Preview(views: RemoteViews?, heightDp: Int) {
    if (views == null) {
        Spacer(Modifier.fillMaxWidth().height(heightDp.dp))
        return
    }
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { frame ->
            frame.removeAllViews()
            runCatching { frame.addView(views.apply(frame.context, frame)) }
        },
        modifier = Modifier.fillMaxWidth().height(heightDp.dp),
    )
}
