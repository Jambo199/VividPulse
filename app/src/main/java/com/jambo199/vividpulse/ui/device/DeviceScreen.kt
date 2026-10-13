package com.jambo199.vividpulse.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.system.DisplayReader
import com.jambo199.vividpulse.system.DisplaySpec
import com.jambo199.vividpulse.system.MemInfo
import com.jambo199.vividpulse.system.SpecSection
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.RoundButton
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedCard
import com.jambo199.vividpulse.ui.rememberSpecs
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun DeviceScreen(open: (String) -> Unit) {
    val context = LocalContext.current
    val specs = rememberSpecs()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val display by produceState<DisplaySpec?>(null) { value = withContext(Dispatchers.IO) { DisplayReader.read(context) } }
    val storageTotal = live?.storage?.total ?: 0L
    val sections by produceState<List<SpecSection>?>(null, specs, display, storageTotal) {
        val s = specs ?: return@produceState
        value = withContext(Dispatchers.IO) { s.sections(display?.rows().orEmpty(), storageTotal) }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.games, Accents.brand, Accents.memory), Modifier.fillMaxWidth().height(420.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().entrance(0), verticalAlignment = Alignment.CenterVertically) {
                    Text("Device", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    sections?.let { list ->
                        RoundButton(Icons.Rounded.Share, "Share specs", { shareSpecs(context, specs?.name.orEmpty(), list) })
                        Spacer(Modifier.width(8.dp))
                    }
                    RoundButton(Icons.Rounded.Settings, "Settings", { open(Routes.SETTINGS) })
                }
            }
            item { Hero(specs?.name, specs?.summary, display, storageTotal, specs?.ramBytes ?: 0L, Modifier.entrance(1)) }
            item {
                Row(Modifier.fillMaxWidth().entrance(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinkCard("Display", display?.let { "${it.refreshRates.lastOrNull()?.toInt() ?: 60} Hz" } ?: " ", Icons.Rounded.Smartphone, Accents.cpu, Modifier.weight(1f).sharedCard("card-display")) { open(Routes.DISPLAY) }
                    LinkCard("Cameras", "Lenses", Icons.Rounded.CameraAlt, Accents.upload, Modifier.weight(1f).sharedCard("card-cameras")) { open(Routes.CAMERAS) }
                    LinkCard("Sensors", "Live", Icons.Rounded.Sensors, Accents.battery, Modifier.weight(1f).sharedCard("card-sensors")) { open(Routes.SENSORS) }
                }
            }
            val list = sections
            if (list == null) {
                items(3) { ShimmerBox(Modifier.fillMaxWidth().height(180.dp)) }
            } else {
                list.forEachIndexed { i, section ->
                    item(key = section.title) { SectionCard(section, Modifier.entrance(i + 3)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(name: String?, summary: String?, display: DisplaySpec?, storage: Long, ram: Long, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth(), accent = Accents.brand) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhoneBack(Modifier.width(78.dp).height(150.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name ?: " ",
                    style = MaterialTheme.typography.headlineMedium.copy(brush = Brush.linearGradient(listOf(Accents.brand.start, Accents.brand.end))),
                )
                Spacer(Modifier.height(4.dp))
                Text(summary ?: " ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ram > 0) Pill("${MemInfo.marketedRamGb(ram)} GB RAM", Accents.memory)
            if (storage > 0) Pill("${MemInfo.marketedStorageGb(storage)} GB", Accents.storage)
            display?.inches?.let { Pill(String.format(Locale.US, "%.2f\"", it), Accents.cpu) }
            display?.refreshRates?.lastOrNull()?.let { Pill("${it.toInt()} Hz", Accents.download) }
            Graph.monitor.battery.designCapacityMah?.let { Pill("$it mAh", Accents.battery) }
        }
    }
}

/** A simple drawing of a phone's back with a big round camera, vivo X-series style. */
@Composable
private fun PhoneBack(modifier: Modifier) {
    val colors = LocalPulseColors.current
    Canvas(modifier) {
        val r = CornerRadius(size.width * 0.2f)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF2B2F45), Color(0xFF151827))), cornerRadius = r)
        drawRoundRect(colors.glassBorder, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        val c = Offset(size.width / 2, size.height * 0.27f)
        val ring = size.width * 0.36f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3F5C), Color(0xFF0E1020)), center = c, radius = ring), ring, c)
        drawCircle(Brush.sweepGradient(listOf(Accents.brand.start, Accents.brand.end, Accents.brand.start), center = c), ring, c, style = Stroke(2.dp.toPx()))
        val lens = ring * 0.32f
        listOf(Offset(-0.42f, -0.36f), Offset(0.42f, -0.36f), Offset(0f, 0.42f)).forEach { o ->
            val p = Offset(c.x + o.x * ring, c.y + o.y * ring)
            drawCircle(Color(0xFF05060C), lens, p)
            drawCircle(Color(0xFF283050), lens * 0.55f, p)
            drawCircle(Color.White.copy(alpha = 0.5f), lens * 0.14f, Offset(p.x - lens * 0.25f, p.y - lens * 0.25f))
        }
        drawRoundRect(colors.track, Offset(size.width * 0.3f, size.height * 0.86f), Size(size.width * 0.4f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

@Composable
private fun LinkCard(title: String, note: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Accent, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier, accent = accent, onClick = onClick, padding = 14.dp) {
        com.jambo199.vividpulse.ui.components.IconBadge(icon, accent, size = 36.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(section: SpecSection, modifier: Modifier) {
    val context = LocalContext.current
    val (icon, accent) = when (section.title) {
        "Phone" -> Icons.Rounded.PhoneAndroid to Accents.brand
        "Software" -> Icons.Rounded.Info to Accents.games
        "Processor" -> Icons.Rounded.Smartphone to Accents.cpu
        "Display" -> Icons.Rounded.Smartphone to Accents.download
        else -> Icons.Rounded.Sensors to Accents.battery
    }
    GlassCard(modifier.fillMaxWidth()) {
        CardTitle(icon, section.title, accent)
        Spacer(Modifier.height(4.dp))
        section.rows.forEach { row ->
            Box(
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { copy(context, row.label, row.value) },
                ),
            ) {
                InfoRow(row.label, row.value)
            }
        }
    }
}

private fun copy(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
}

private fun shareSpecs(context: Context, name: String, sections: List<SpecSection>) {
    val text = buildString {
        appendLine(name)
        sections.forEach { s ->
            appendLine()
            appendLine(s.title.uppercase(Locale.US))
            s.rows.forEach { appendLine("${it.label}: ${it.value}") }
        }
        appendLine()
        append("From Vivid Pulse")
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Share specs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
