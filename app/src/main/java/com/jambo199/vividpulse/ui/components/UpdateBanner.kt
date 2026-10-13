package com.jambo199.vividpulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.update.UpdateInfo
import com.jambo199.vividpulse.update.UpdateState
import com.jambo199.vividpulse.ui.settings.accentFor
import com.jambo199.vividpulse.update.UpdateSize
import com.jambo199.vividpulse.update.WhatsNew

/**
 * A floating card that offers a new version, shows the download and hands it to the installer.
 * "Later" puts it away until the app is next opened.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    snoozed: Int?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info: UpdateInfo? = when (state) {
        is UpdateState.Available -> state.info.takeUnless { it.versionCode == snoozed }
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    }
    AnimatedVisibility(
        visible = info != null,
        enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 300f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val version = info?.versionName.orEmpty()
        val (title, subtitle) = when (state) {
            is UpdateState.Downloading -> "Downloading $version" to "${(state.progress * 100).toInt()}% · it installs when done"
            is UpdateState.Ready -> "Ready to install $version" to "Tap Install, then confirm on the next screen"
            is UpdateState.Failed -> "Update didn't finish" to state.message
            else -> "${info?.updateSize?.label ?: "Update"} available · $version" to (info?.notes?.ifBlank { null } ?: "A new version of Vivid Pulse")
        }
        val action: String? = when (state) {
            is UpdateState.Downloading -> null
            is UpdateState.Ready -> "Install"
            is UpdateState.Failed -> "Retry"
            else -> "Update"
        }
        GlassCard(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            accent = Accents.battery,
            padding = 14.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.SystemUpdate, Accents.battery, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (state is UpdateState.Available) {
                    Text(
                        "Later",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape).bouncyClick(onClick = onLater).padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
                if (action != null) {
                    PillButton(action, Accents.battery, onUpdate)
                }
            }
            if (state is UpdateState.Downloading) {
                Spacer(Modifier.height(10.dp))
                BarMeter(state.progress, Accents.battery, Modifier.fillMaxWidth().height(8.dp), height = 6.dp)
            }
        }
    }
}

/** A small gradient button. */
@Composable
fun PillButton(text: String, accent: Accent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(accent.brush)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** "What's new" after an update (or from Settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(notes: WhatsNew, onSeeAll: (() -> Unit)? = null, onClose: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.AutoAwesome, Accents.brand, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("What's new", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Vivid Pulse ${notes.versionName}" + when (notes.alsoIncludes) {
                            0 -> ""
                            1 -> " · and the update before it"
                            else -> " · and the ${notes.alsoIncludes} updates before it"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            notes.size?.let { Pill(it.label, accentFor(it), filled = it != UpdateSize.SMALL) }
            Text(notes.title, style = MaterialTheme.typography.titleMedium)
            notes.changes.forEachIndexed { i, change ->
                Row(Modifier.entrance(i)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = Accents.brand.start, modifier = Modifier.padding(end = 10.dp))
                    Text(change, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Nice", Accents.brand, onClose, Modifier.fillMaxWidth())
            if (onSeeAll != null) {
                Text(
                    "See every update",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accents.brand.start,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).bouncyClick(onClick = onSeeAll).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
