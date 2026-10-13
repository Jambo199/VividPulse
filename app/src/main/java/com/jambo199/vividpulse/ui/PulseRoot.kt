package com.jambo199.vividpulse.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jambo199.vividpulse.Graph
import com.jambo199.vividpulse.ui.components.LocalPageScope
import com.jambo199.vividpulse.ui.components.UpdateBanner
import com.jambo199.vividpulse.ui.components.WhatsNewSheet
import com.jambo199.vividpulse.update.UpdateState
import com.jambo199.vividpulse.ui.components.LocalSharedScope
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.device.CamerasScreen
import com.jambo199.vividpulse.ui.device.DeviceScreen
import com.jambo199.vividpulse.ui.device.DisplayScreen
import com.jambo199.vividpulse.ui.device.SensorsScreen
import com.jambo199.vividpulse.ui.games.GameDetailScreen
import com.jambo199.vividpulse.ui.games.GamesScreen
import com.jambo199.vividpulse.ui.games.GamesViewModel
import com.jambo199.vividpulse.ui.live.BatteryScreen
import com.jambo199.vividpulse.ui.live.CpuScreen
import com.jambo199.vividpulse.ui.live.LiveScreen
import com.jambo199.vividpulse.ui.live.MemoryScreen
import com.jambo199.vividpulse.ui.live.NetworkScreen
import com.jambo199.vividpulse.ui.live.ThermalScreen
import com.jambo199.vividpulse.ui.settings.SettingsScreen
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.tools.FpsScreen
import com.jambo199.vividpulse.ui.tools.FreeRamScreen
import com.jambo199.vividpulse.ui.tools.ScreenTestScreen
import com.jambo199.vividpulse.ui.tools.SpeedTestScreen
import com.jambo199.vividpulse.ui.tools.ToolsScreen
import com.jambo199.vividpulse.ui.tools.TouchTestScreen
import kotlinx.coroutines.flow.MutableStateFlow

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    LIVE("live", "Live", Icons.Rounded.MonitorHeart),
    DEVICE("device", "Device", Icons.Rounded.Smartphone),
    GAMES("games", "Games", Icons.Rounded.SportsEsports),
    TOOLS("tools", "Tools", Icons.Rounded.Handyman),
}

/** Where each page lives. */
object Routes {
    const val CPU = "cpu"
    const val MEMORY = "memory"
    const val BATTERY = "battery"
    const val NETWORK = "network"
    const val THERMAL = "thermal"
    const val DISPLAY = "display"
    const val CAMERAS = "cameras"
    const val SENSORS = "sensors"
    const val SPEED = "speedtest"
    const val SCREEN = "screentest"
    const val TOUCH = "touchtest"
    const val FPS = "fps"
    const val SETTINGS = "settings"
    const val FREE_RAM = "freeram"
    fun game(appId: Int) = "game/$appId"
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PulseRoot(requests: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    // Pages asked for from outside (launcher shortcuts).
    LaunchedEffect(Unit) {
        requests.collect { r ->
            if (r == null) return@collect
            requests.value = null
            // "games/deals" or "games/deals/discount": the Games page on that list, in that order.
            if (r.startsWith("games/") && GamesViewModel.ask(r.removePrefix("games/"))) {
                nav.navigate(Tab.GAMES.route) {
                    popUpTo(Tab.LIVE.route)
                    launchSingleTop = true
                }
            } else if (isTab(r)) {
                nav.navigate(r) {
                    popUpTo(Tab.LIVE.route)
                    launchSingleTop = true
                }
            } else {
                nav.navigate(r) { launchSingleTop = true }
            }
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tab = Tab.entries.firstOrNull { it.route == route }
    var lastTab by remember { mutableStateOf(Tab.LIVE) }
    LaunchedEffect(tab) { if (tab != null) lastTab = tab }
    val update by Graph.updates.state.collectAsStateWithLifecycle()
    val snoozed by Graph.updates.snoozed.collectAsStateWithLifecycle()
    // Once, the first time the app opens after an update it installed.
    var whatsNew by remember { mutableStateOf(Graph.updates.takeWhatsNew()) }
    val back: () -> Unit = { nav.popBackStack() }
    val open: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = Tab.LIVE.route,
                    enterTransition = { enter() },
                    exitTransition = { exit() },
                    popEnterTransition = { popEnter() },
                    popExitTransition = { popExit() },
                ) {
                    page(Tab.LIVE.route) { LiveScreen(open) }
                    page(Tab.DEVICE.route) { DeviceScreen(open) }
                    page(Tab.GAMES.route) { GamesScreen(open) }
                    page(Tab.TOOLS.route) { ToolsScreen(open) }
                    page(Routes.CPU) { CpuScreen(back) }
                    page(Routes.MEMORY) { MemoryScreen(back, open) }
                    page(Routes.BATTERY) { BatteryScreen(back) }
                    page(Routes.NETWORK) { NetworkScreen(back, open) }
                    page(Routes.THERMAL) { ThermalScreen(back) }
                    page(Routes.DISPLAY) { DisplayScreen(back, open) }
                    page(Routes.CAMERAS) { CamerasScreen(back) }
                    page(Routes.SENSORS) { SensorsScreen(back) }
                    page(Routes.SPEED) { SpeedTestScreen(back) }
                    page(Routes.SCREEN) { ScreenTestScreen(back) }
                    page(Routes.TOUCH) { TouchTestScreen(back) }
                    page(Routes.FPS) { FpsScreen(back) }
                    page(Routes.SETTINGS) { SettingsScreen(back) }
                    page(Routes.FREE_RAM) { FreeRamScreen(back) }
                    // Opens and starts straight away (used by the automated test).
                    page(Routes.FREE_RAM + "/start") { FreeRamScreen(back, autoStart = true) }
                    composable(
                        "game/{appId}",
                        arguments = listOf(navArgument("appId") { type = NavType.IntType }),
                    ) { e ->
                        CompositionLocalProvider(LocalPageScope provides this) {
                            GameDetailScreen(e.arguments?.getInt("appId") ?: 0, back)
                        }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            UpdateBanner(
                state = if (tab != null) update else UpdateState.Idle,
                snoozed = snoozed,
                onUpdate = { Graph.updates.download() },
                onLater = { Graph.updates.snooze() },
                modifier = Modifier.padding(bottom = 10.dp),
            )
            AnimatedVisibility(
                visible = tab != null,
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
            ) {
                BottomBar(tab ?: lastTab) { t ->
                    if (t != tab) {
                        nav.navigate(t.route) {
                            popUpTo(Tab.LIVE.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        }
    }
    }
    whatsNew?.let { WhatsNewSheet(it) { whatsNew = null } }
}

private fun NavGraphBuilder.page(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { e ->
        CompositionLocalProvider(LocalPageScope provides this) { content(e) }
    }
}

private fun isTab(route: String?) = Tab.entries.any { it.route == route }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTab(targetState.destination.route) && isTab(initialState.destination.route)) {
        fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320), initialScale = 0.96f)
    } else {
        fadeIn(tween(300, delayMillis = 40)) + scaleIn(spring(dampingRatio = 0.9f, stiffness = 300f), initialScale = 0.92f)
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    fadeOut(tween(180)) + scaleOut(tween(300), targetScale = 1.03f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    fadeIn(tween(280, delayMillis = 40)) + scaleIn(tween(320), initialScale = 1.03f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    fadeOut(tween(200)) + scaleOut(tween(300), targetScale = 0.92f)

/** The floating tab bar; the chosen tab grows into a glowing pill with its name. */
@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = LocalPulseColors.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(colors.glass)
            .border(1.dp, colors.glassBorder, CircleShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val glow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "tab")
            Row(
                Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(Accents.brand.brush, alpha = glow)
                    .semantics { contentDescription = t.label }
                    .bouncyClick { onSelect(t) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    t.icon,
                    contentDescription = null,
                    tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        val s = 1f + 0.08f * glow
                        scaleX = s
                        scaleY = s
                    },
                )
                AnimatedVisibility(
                    visible = on,
                    enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 500f)) + fadeIn(),
                    exit = shrinkHorizontally(spring(dampingRatio = 0.9f, stiffness = 600f)) + fadeOut(tween(100)),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Space at the bottom of tab pages so the last item clears the floating bar. */
val TabBottomPadding = 112.dp
