package com.jambo199.vividpulse.ui.games

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.GameDetail
import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.ui.components.AnimatedNumber
import com.jambo199.vividpulse.ui.components.CardTitle
import com.jambo199.vividpulse.ui.components.ChartLine
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.InfoRow
import com.jambo199.vividpulse.ui.components.LiveChart
import com.jambo199.vividpulse.ui.components.LiveDot
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.RoundButton
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.theme.Accent
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import kotlinx.coroutines.delay

private const val PLAYERS_EVERY_MS = 15_000L

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GameDetailScreen(appId: Int, back: () -> Unit, open: (String) -> Unit = {}) {
    val context = LocalContext.current
    val known = remember(appId) { Graph.games.known(appId) }
    val detail by produceState<Result<GameDetail?>?>(null, appId) {
        value = runCatching { Graph.games.details(appId) }
    }
    // Live player count, checked every few seconds while the page is open.
    var players by remember { mutableStateOf(known?.playersNow?.let { listOf(it.toFloat()) } ?: emptyList()) }
    var sampledAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(appId) {
        while (true) {
            Graph.games.playersNow(appId)?.let {
                players = (players + it.toFloat()).takeLast(60)
                sampledAt = SystemClock.uptimeMillis()
            }
            delay(PLAYERS_EVERY_MS)
        }
    }
    val d = detail?.getOrNull()
    val watchList by Graph.watching.games.collectAsStateWithLifecycle()
    val watched = watchList.any { it.appId == appId }
    val name = d?.name ?: known?.name ?: ""
    val image = known?.imageUrl ?: d?.imageUrl ?: Steam.headerFallback(appId)
    fun openUrl(url: String) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box {
                    GameImage(image, appId, Modifier.fillMaxWidth().aspectRatio(460f / 215f))
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to MaterialTheme.colorScheme.background)),
                    )
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).entrance(0)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium)
                    val makers = d?.developers?.takeIf { it.isNotEmpty() }?.joinToString()
                    if (makers != null) Text(makers, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    PriceLine(d?.price ?: known?.price, d?.originalPrice ?: known?.originalPrice, d?.discountPercent ?: known?.discountPercent ?: 0)
                }
            }
            // Free games have nothing to compare.
            val free = d?.free == true || known?.priceCents == 0L
            if (name.isNotBlank() && !free) {
                item {
                    PriceCompareCard(
                        appId,
                        name,
                        d?.priceCents ?: known?.priceCents,
                        d?.price ?: known?.price,
                        open,
                        Modifier.padding(horizontal = 16.dp).entrance(1),
                    )
                }
            }
            if (watched && !free) {
                item {
                    PriceAlertCard(
                        appId,
                        d?.regularCents ?: d?.priceCents ?: known?.priceCents,
                        d?.currency,
                        Modifier.padding(horizontal = 16.dp).entrance(1),
                    )
                }
            }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().entrance(1), accent = Accents.games) {
                    CardTitle(Icons.Rounded.People, "Playing right now", Accents.games, trailing = "Steam")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(Accents.battery.start)
                        Spacer(Modifier.width(8.dp))
                        AnimatedNumber(
                            players.lastOrNull() ?: 0f,
                            { if (players.isEmpty()) "—" else Format.grouped(it.toLong()) },
                            MaterialTheme.typography.displaySmall,
                        )
                    }
                    known?.peakToday?.let {
                        Text("Peak today ${Format.grouped(it)}", style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (players.size >= 3) {
                        Spacer(Modifier.height(10.dp))
                        val low = (players.minOrNull() ?: 0f) * 0.995f
                        LiveChart(
                            listOf(ChartLine(players.map { it - low }, Accents.games)),
                            sampledAt,
                            PLAYERS_EVERY_MS,
                            Modifier.fillMaxWidth().height(90.dp),
                            visible = 20,
                        )
                    }
                }
            }
            when {
                d != null -> {
                    if (d.genres.isNotEmpty()) {
                        item {
                            FlowRow(
                                Modifier.padding(horizontal = 20.dp).entrance(2),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                d.genres.forEach { Pill(it, Accents.games, filled = false) }
                                d.metacritic?.let { Pill("Metacritic $it", metacriticAccent(it)) }
                            }
                        }
                    }
                    if (d.description.isNotBlank()) {
                        item {
                            Text(d.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp).entrance(3))
                        }
                    }
                    if (d.screenshots.isNotEmpty()) {
                        item {
                            val pager = rememberPagerState { d.screenshots.size }
                            HorizontalPager(
                                pager,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                pageSpacing = 10.dp,
                                modifier = Modifier.entrance(4),
                            ) { page ->
                                val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(d.screenshots[page]).crossfade(250).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .graphicsLayer {
                                            val s = 1f - 0.08f * kotlin.math.abs(offset).coerceAtMost(1f)
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black),
                                )
                            }
                        }
                    }
                    item {
                        GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().entrance(5)) {
                            d.releaseDate?.let { InfoRow("Released", it) }
                            if (d.publishers.isNotEmpty()) InfoRow("Publisher", d.publishers.joinToString())
                            InfoRow("Runs on", listOfNotNull("Windows".takeIf { d.windows }, "Mac".takeIf { d.mac }, "Linux / Steam Deck".takeIf { d.linux }).joinToString())
                            d.controller?.let { InfoRow("Controller", if (it == "full") "Full support" else "Partial support") }
                            d.reviews?.let { InfoRow("Recommendations", Format.grouped(it)) }
                        }
                    }
                }
                detail == null -> item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ShimmerBox(Modifier.fillMaxWidth().height(28.dp))
                        ShimmerBox(Modifier.fillMaxWidth().height(90.dp))
                        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    }
                }
                else -> item {
                    Text(
                        "Couldn't load the store page. Check your connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().entrance(6), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Open in Steam", Accents.games, Modifier.weight(1f)) { openUrl("https://store.steampowered.com/app/$appId") }
                    ActionButton("Player charts", Accents.download, Modifier.weight(1f)) { openUrl("https://steamcharts.com/app/$appId") }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", back, Modifier.statusBarsPadding().padding(12.dp))
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(Icons.Rounded.Share, "Share", {
                val count = players.lastOrNull()?.let { " – ${Format.grouped(it.toLong())} playing right now on Steam" }.orEmpty()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "$name$count\nhttps://store.steampowered.com/app/$appId")
                runCatching { context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
            val starScale by animateFloatAsState(if (watched) 1.15f else 1f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "star")
            RoundButton(
                if (watched) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (watched) "Stop watching" else "Watch this game",
                {
                    if (name.isNotBlank()) Graph.watching.toggle(Game(appId, name, d?.imageUrl ?: image, rank = 0))
                },
                Modifier.graphicsLayer {
                    scaleX = starScale
                    scaleY = starScale
                },
            )
        }
    }
}

@Composable
private fun PriceLine(price: String?, original: String?, discount: Int) {
    if (price == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(price, style = MaterialTheme.typography.titleLarge.merge(Numbers), color = if (discount > 0) Accents.battery.start else MaterialTheme.colorScheme.onSurface)
        if (discount > 0 && original != null) {
            Spacer(Modifier.width(10.dp))
            Text(original, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Pill("-$discount%", Accents.battery)
        }
    }
}

@Composable
private fun ActionButton(text: String, accent: Accent, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(accent.brush)
            .bouncyClick(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.height(16.dp))
    }
}

private fun metacriticAccent(score: Int): Accent = when {
    score >= 75 -> Accents.battery
    score >= 50 -> Accents.storage
    else -> Accents.thermal
}
