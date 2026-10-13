package com.jambo199.vividpulse.ui.games

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.jambo199.vividpulse.ui.components.RoundButton
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividpulse.games.Chart
import com.jambo199.vividpulse.games.EpicGame
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.LiveDot
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedPicture
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(open: (String) -> Unit) {
    val vm: GamesViewModel = viewModel()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val chartState = tab.chart?.let { vm.chart(it).collectAsStateWithLifecycle().value }
    val epicState by vm.epic.collectAsStateWithLifecycle()
    val steamFree by vm.chart(Chart.FREE_TO_KEEP).collectAsStateWithLifecycle()
    val dealSort by vm.dealSort.collectAsStateWithLifecycle()
    val asked by GamesViewModel.asked.collectAsStateWithLifecycle()
    LaunchedEffect(asked) { vm.takeAsked() }
    val listState = rememberLazyListState()
    // A new list, or a new order, starts from its top rather than somewhere down the old one.
    LaunchedEffect(tab, dealSort) {
        if (listState.firstVisibleItemIndex > 4) listState.scrollToItem(0)
    }
    val refreshing = chartState?.loading ?: epicState.loading
    var searching by rememberSaveable { mutableStateOf(false) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val watching by vm.watching.collectAsStateWithLifecycle()
    val watchPlayers by vm.watchPlayers.collectAsStateWithLifecycle()
    BackHandler(enabled = searching) {
        searching = false
        vm.search("")
    }

    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.games, Accents.upload, Accents.download), Modifier.fillMaxWidth().height(380.dp))
        PullToRefreshBox(
            isRefreshing = refreshing && (chartState?.items ?: epicState.items) != null,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth().entrance(0), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Games", style = MaterialTheme.typography.headlineLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val o = online
                            if (o != null) {
                                LiveDot(Accents.battery.start)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${Format.compact(o.first)} on Steam · ${Format.compact(o.second)} playing",
                                    style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("What PC gamers are playing and buying", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    RoundButton(
                        if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                        if (searching) "Close search" else "Search games",
                        {
                            searching = !searching
                            if (!searching) vm.search("")
                        },
                    )
                    }
                }
                if (searching) {
                    item { SearchField(query, vm::search) }
                    val r = results
                    val found = r.items
                    when {
                        query.isBlank() -> item { Hint("Type a game's name to see its live player count, price and page.") }
                        found != null && found.isEmpty() -> item { Hint("No games called \"$query\" on Steam") }
                        found != null -> itemsIndexed(found, key = { _, g -> "search-${g.appId}" }) { i, g ->
                            GameRow(g, GamesTab.TOP_SELLERS, false, Modifier.entrance(i), place = null) { open(Routes.game(g.appId)) }
                        }
                        r.error != null -> item { ErrorCard(r.error) { vm.search(query) } }
                        else -> items(4) { ShimmerBox(Modifier.fillMaxWidth().height(80.dp)) }
                    }
                } else {
                if (watching.isNotEmpty()) {
                    item { YourGames(watching, watchPlayers, Modifier.entrance(1)) { open(Routes.game(it)) } }
                }
                item { TabPicker(tab, Modifier.entrance(1)) { vm.select(it) } }
                item {
                    val at = chartState?.fetchedAt ?: epicState.fetchedAt
                    val error = chartState?.error ?: epicState.error.takeIf { tab.chart == null }
                    Text(
                        when {
                            error != null && at != null -> "$error · showing ${ago(at)}'s list"
                            at != null -> "Updated ${ago(at)} · pull down to refresh"
                            else -> " "
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (tab == GamesTab.DEALS) {
                    item { SortPicker(dealSort, Modifier.entrance(2)) { vm.sortDeals(it) } }
                }
                val chart = tab.chart
                if (chart != null) {
                    val s = chartState ?: ListState()
                    val games = s.items?.let { if (tab == GamesTab.DEALS) dealSort.apply(it) else it }
                    when {
                        games != null -> {
                            val history = games.any { it.lastWeekRank != null }
                            val heroes = tab != GamesTab.DEALS
                            itemsIndexed(games, key = { _, g -> "${tab.name}-${g.appId}" }) { i, g ->
                                // Cards further down appear as you reach them, without waiting their turn.
                                val modifier = Modifier.animateItem().entrance(if (i < 10) i + 2 else 2)
                                if (i < 3 && heroes) {
                                    HeroGame(g, tab, history, i + 1, modifier) { open(Routes.game(g.appId)) }
                                } else {
                                    GameRow(g, tab, history, modifier, place = i + 1) { open(Routes.game(g.appId)) }
                                }
                            }
                            if (s.canLoadMore) {
                                item(key = "more-${tab.name}") {
                                    if (s.error != null && !s.loadingMore) {
                                        Hint("Couldn't load more · tap to try again", Modifier.clickable { vm.loadMore(chart) })
                                    } else {
                                        // Reaching the end of the list loads the next page.
                                        LaunchedEffect(games.size) { vm.loadMore(chart) }
                                        ShimmerBox(Modifier.fillMaxWidth().height(80.dp))
                                    }
                                }
                            } else if (games.size > 20) {
                                item(key = "end-${tab.name}") { Hint(endOfList(tab, games.size)) }
                            }
                        }
                        s.error != null -> item { ErrorCard(s.error) { vm.refresh() } }
                        else -> items(6) { i -> ShimmerBox(Modifier.fillMaxWidth().height(if (i < 3) 190.dp else 80.dp)) }
                    }
                } else {
                    item { Heading("FREE ON EPIC") }
                    val games = epicState.items
                    when {
                        games != null && games.isEmpty() -> item { Hint("Epic isn't giving anything away right now.") }
                        games != null -> itemsIndexed(games, key = { _, g -> g.url }) { i, g -> EpicCard(g, Modifier.entrance(i + 2)) }
                        epicState.error != null -> item { ErrorCard(epicState.error ?: "") { vm.refresh() } }
                        else -> items(2) { ShimmerBox(Modifier.fillMaxWidth().height(260.dp)) }
                    }
                    item { Heading("FREE TO KEEP ON STEAM") }
                    val steam = steamFree.items
                    when {
                        steam != null && steam.isEmpty() ->
                            item { Hint("Nothing on Steam is free to keep right now. When a paid game is given away, it shows up here.") }
                        steam != null -> itemsIndexed(steam, key = { _, g -> "free-${g.appId}" }) { i, g ->
                            GameRow(g, tab, false, Modifier.entrance(i + 4), place = null) { open(Routes.game(g.appId)) }
                        }
                        steamFree.error != null -> item { ErrorCard(steamFree.error ?: "") { vm.refresh() } }
                        else -> items(2) { ShimmerBox(Modifier.fillMaxWidth().height(80.dp)) }
                    }
                }
                }
                item {
                    Text(
                        "Charts from Steam and the Epic Games Store, refreshed when you open them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPicker(selected: GamesTab, modifier: Modifier, onSelect: (GamesTab) -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamesTab.entries.forEach { t ->
            val on = t == selected
            val glow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "chip")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(LocalPulseColors.current.glass)
                    .background(Accents.games.brush, alpha = glow)
                    .bouncyClick { onSelect(t) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HeroGame(g: Game, tab: GamesTab, history: Boolean, place: Int, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .bouncyClick(onClick = onClick),
    ) {
        GameImage(g.imageUrl, g.appId, Modifier.fillMaxWidth().aspectRatio(460f / 215f))
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Text(
            "#$place",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
            when {
                history -> RankChange(g)
                g.discountPercent > 0 -> Pill("-${g.discountPercent}%", Accents.battery)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(g.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subline(g, tab), style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun GameRow(g: Game, tab: GamesTab, history: Boolean, modifier: Modifier, place: Int?, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (place != null) {
                Text(
                    "$place",
                    style = (if (place < 100) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall).merge(Numbers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(38.dp).padding(start = 4.dp),
                )
            }
            GameImage(g.imageUrl, g.appId, Modifier.width(118.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tab == GamesTab.DEALS || tab == GamesTab.FREE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (tab == GamesTab.FREE) "Free to keep" else g.price ?: "",
                            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                            fontWeight = FontWeight.Bold,
                            color = Accents.battery.start,
                        )
                        g.originalPrice?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val more = dealLine(g)
                    if (more.isNotEmpty()) {
                        Text(more, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(subline(g, tab), style = MaterialTheme.typography.bodySmall.merge(Numbers), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tab == GamesTab.UPCOMING || tab == GamesTab.TOP_SELLERS) {
                        g.reviewLabel?.let { Text(reviews(g), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            if (g.discountPercent > 0) Pill("-${g.discountPercent}%", Accents.battery) else if (history) RankChange(g)
        }
    }
}

@Composable
private fun RankChange(g: Game) {
    val change = g.rankChange
    when {
        change == null -> Pill("NEW", Accents.upload)
        change > 0 -> Pill("▲$change", Accents.battery)
        change < 0 -> Pill("▼${-change}", Accents.thermal)
        else -> Unit
    }
}

private fun subline(g: Game, tab: GamesTab): String = when (tab) {
    GamesTab.MOST_PLAYED -> listOfNotNull(
        g.playersNow?.let { "${Format.compact(it)} playing" },
        g.peakToday?.let { "peak ${Format.compact(it)}" },
    ).joinToString(" · ")
    GamesTab.UPCOMING -> listOfNotNull(g.release ?: "Coming soon", g.price?.let { "pre-order $it" }).joinToString(" · ")
    else -> listOfNotNull(
        g.price,
        g.discountPercent.takeIf { it > 0 }?.let { "-$it%" },
        g.weeksOnChart?.takeIf { it > 1 }?.let { "$it weeks" },
    ).joinToString(" · ")
}

/** Under a deal's price: when it ends, how many are playing, what reviewers think. */
private fun dealLine(g: Game): String = listOfNotNull(
    g.dealEnds?.let { "Ends ${endsIn(it * 1000)}" },
    g.playersNow?.takeIf { it > 0 }?.let { "${Format.compact(it)} playing" },
    g.reviewLabel?.takeIf { g.reviewCount != null },
).joinToString(" · ")

/** "Very Positive · 92% of 12K" */
private fun reviews(g: Game): String = listOfNotNull(
    g.reviewLabel,
    g.reviewPercent?.let { p -> g.reviewCount?.let { "$p% of ${Format.compact(it)}" } },
).joinToString(" · ")

private fun endOfList(tab: GamesTab, n: Int): String = when (tab) {
    GamesTab.MOST_PLAYED -> "Steam's top $n by players right now"
    GamesTab.TOP_SELLERS -> "This week's top $n sellers on Steam"
    else -> "That's all $n"
}

@Composable
fun GameImage(url: String, appId: Int, modifier: Modifier, shared: Boolean = true) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(250).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // Only one picture per game on screen may fly to its page.
        modifier = (if (shared) modifier.sharedPicture("game-$appId") else modifier).background(LocalPulseColors.current.track),
    )
}

@Composable
private fun EpicCard(g: EpicGame, modifier: Modifier) {
    val context = LocalContext.current
    GlassCard(
        modifier.fillMaxWidth(),
        accent = if (g.freeNow) Accents.battery else Accents.games,
        padding = 0.dp,
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context).data(g.imageUrl).crossfade(250).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(LocalPulseColors.current.track),
            )
            Row(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Pill(if (g.freeNow) "FREE NOW" else "COMING SOON", if (g.freeNow) Accents.battery else Accents.games)
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(g.title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ends = g.endsAt
                val starts = g.startsAt
                Text(
                    when {
                        g.freeNow && ends != null -> "Free until ${day(ends)}"
                        !g.freeNow && starts != null -> "Free from ${day(starts)}"
                        else -> "Free"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (g.freeNow) Accents.battery.start else Accents.games.start,
                )
                g.originalPrice?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (g.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(g.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The search box: a glowing pill that takes the keyboard straight away. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = LocalPulseColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.glass)
            .border(1.dp, Brush.horizontalGradient(listOf(Accents.games.start, Accents.games.end)), CircleShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = Accents.games.start)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search Steam", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Accents.games.start),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(horizontal = 6.dp, vertical = 8.dp))
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/** How to order the deals: smaller chips under the lists. */
@Composable
private fun SortPicker(selected: DealSort, modifier: Modifier, onSelect: (DealSort) -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sort", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
        DealSort.entries.forEach { d ->
            val on = d == selected
            val glow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "sort")
            Box(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, Accents.battery.start.copy(alpha = 0.6f * (1f - glow)), CircleShape)
                    .background(Accents.battery.brush, alpha = glow)
                    .bouncyClick { onSelect(d) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(d.label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** Starred games with their live player counts, in a row you can swipe. */
@Composable
private fun YourGames(games: List<Game>, players: Map<Int, Long>, modifier: Modifier, onOpen: (Int) -> Unit) {
    Column(modifier) {
        Text("YOUR GAMES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(games, key = { it.appId }) { g ->
                Column(
                    Modifier
                        .width(168.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LocalPulseColors.current.glass)
                        .bouncyClick { onOpen(g.appId) },
                ) {
                    GameImage(g.imageUrl, g.appId, Modifier.fillMaxWidth().aspectRatio(460f / 215f), shared = false)
                    Column(Modifier.padding(10.dp)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(Accents.battery.start)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                players[g.appId]?.let { "${Format.compact(it)} playing" } ?: "—",
                                style = MaterialTheme.typography.bodySmall.merge(Numbers),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.thermal, onClick = retry) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Text("Tap to try again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ago(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        .replace("0 minutes ago", "just now").lowercase(Locale.getDefault())

private fun endsIn(ms: Long): String {
    // "In 3 days" reads as "in 3 days"; a date like "Oct 23, 2026" keeps its capitals.
    val text = DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS).toString()
    return if (text.startsWith("In ")) text.replaceFirstChar { it.lowercase() } else text
}

private fun day(ms: Long): String = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
