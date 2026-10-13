package com.jambo199.vividpulse.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.BuildConfig
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.DetailPage
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.update.ChangelogEntry
import com.jambo199.vividpulse.update.Changelog
import com.jambo199.vividpulse.update.UpdateSize
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every version of the app, newest first, each marked as a major, minor or small update. */
@Composable
fun ChangelogScreen(back: () -> Unit) {
    // What came with the app straight away, then any newer versions from the release page.
    val entries by produceState<List<ChangelogEntry>?>(null) {
        value = Graph.updates.changelog(refresh = false).ifEmpty { null }
        value = Graph.updates.changelog(refresh = true)
    }
    val list = entries
    DetailPage(
        "Changelog",
        back,
        subtitle = list?.let { "${it.size} updates · you're on ${BuildConfig.VERSION_NAME}" },
    ) {
        when {
            list == null -> items(3) { ShimmerBox(Modifier.fillMaxWidth().height(150.dp)) }
            list.isEmpty() -> item {
                Text(
                    "The changelog isn't available in this build. It will be in the next update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            else -> {
                item { Summary(list, Modifier.entrance(0)) }
                list.forEachIndexed { i, e ->
                    item(key = e.code) {
                        // The newest few open; older ones show their first few changes.
                        Version(e, startOpen = i == 0 || e.code > BuildConfig.VERSION_CODE, Modifier.entrance(if (i < 8) i + 1 else 1))
                    }
                }
            }
        }
    }
}

/** How many of each kind of update, with the key to the colours. */
@Composable
private fun Summary(entries: List<ChangelogEntry>, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth(), accent = Accents.brand) {
        CardTitle(Icons.Rounded.History, "Every update so far", Accents.brand, trailing = entries.lastOrNull()?.date?.let { "since ${day(it)}" })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Changelog.counts(entries).forEach { (size, n) ->
                Column(Modifier.weight(1f)) {
                    Text("$n", style = MaterialTheme.typography.headlineMedium, color = accentFor(size).start)
                    Text(size.label.removeSuffix(" update") + if (n == 1) " update" else " updates", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Major updates bring new features, minor ones add to what's there, and small ones are fixes and tweaks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Version(e: ChangelogEntry, startOpen: Boolean, modifier: Modifier) {
    val accent = accentFor(e.size)
    val installed = e.code == BuildConfig.VERSION_CODE
    val newer = e.code > BuildConfig.VERSION_CODE
    var open by rememberSaveable(e.code) { mutableStateOf(startOpen) }
    val extra = e.changes.size - PREVIEW
    GlassCard(modifier.fillMaxWidth(), accent = if (installed || newer) accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent.brush))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.version, style = MaterialTheme.typography.titleLarge)
                e.date?.let { Text(day(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Pill(e.size.label, accent, filled = e.size != UpdateSize.SMALL)
        }
        if (installed || newer) {
            Spacer(Modifier.height(8.dp))
            Pill(if (installed) "Installed" else "Not installed yet", if (installed) Accents.battery else Accents.storage, filled = false)
        }
        if (e.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(e.title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        e.changes.take(PREVIEW).forEach { Change(it, accent) }
        if (extra > 0) {
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column { e.changes.drop(PREVIEW).forEach { Change(it, accent) } }
            }
            val turn by animateFloatAsState(if (open) 180f else 0f, spring(), label = "more")
            Row(
                Modifier.padding(top = 4.dp).clip(CircleShape).bouncyClick { open = !open }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (open) "Show less" else "$extra more " + if (extra == 1) "change" else "changes",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent.start,
                )
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = accent.start, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
        }
    }
}

@Composable
private fun Change(text: String, accent: Accent) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = accent.start, modifier = Modifier.padding(end = 10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The colour of each kind of update. */
fun accentFor(size: UpdateSize): Accent = when (size) {
    UpdateSize.MAJOR -> Accents.brand
    UpdateSize.MINOR -> Accents.download
    UpdateSize.SMALL -> Accents.battery
}

/** "2026-10-13" → "13 Oct 2026" */
private fun day(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) }.getOrDefault(iso)

private const val PREVIEW = 3
