package com.jambo199.vividpulse.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared-element transitions between pages: the scope of the whole app… */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** …and of the page being shown. */
val LocalPageScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Makes this element fly to (and from) the element with the same [key] on the next page, e.g. a
 * dashboard card growing into the page it opens.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCard(key: String, shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    return with(shared) {
        this@sharedCard.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = page,
            boundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 340f) },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Like [sharedCard] but for one element (a picture, an icon) that keeps its look. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPicture(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val page = LocalPageScope.current ?: return this
    return with(shared) {
        this@sharedPicture.sharedElement(
            state = rememberSharedContentState(key),
            animatedVisibilityScope = page,
            boundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = 340f) },
        )
    }
}

/** Clickable that shrinks a little under the finger and springs back, with a light tap feel. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Fades and slides in when first shown, [index] steps after the first, for a staggered entrance. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    // Remembered across scrolling, so a card scrolled back into view doesn't play it again.
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        delay(index.coerceAtMost(10) * 45L)
        played = true
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}
