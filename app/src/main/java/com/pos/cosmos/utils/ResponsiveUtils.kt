package com.pos.cosmos.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive scaling utilities for handling different screen sizes.
 * Only scales down for small/low-resolution phones.
 * Tablets and high-resolution phones remain unchanged.
 */

// Screen width thresholds (in dp)
private const val SMALL_PHONE_WIDTH = 380  // Small phones (low resolution)
private const val MEDIUM_PHONE_WIDTH = 420 // Medium phones
private const val LARGE_PHONE_WIDTH = 600  // Large phones / small tablets

/**
 * Data class holding all responsive dimensions
 */
data class ResponsiveDimensions(
    val scaleFactor: Float,
    val isSmallScreen: Boolean,
    val screenWidthDp: Int
) {
    // Font sizes - scaled versions
    val fontTiny: TextUnit get() = (9f * scaleFactor).sp
    val fontSmall: TextUnit get() = (10f * scaleFactor).sp
    val fontCaption: TextUnit get() = (11f * scaleFactor).sp
    val fontBody2: TextUnit get() = (12f * scaleFactor).sp
    val fontBody: TextUnit get() = (13f * scaleFactor).sp
    val fontSubtitle: TextUnit get() = (14f * scaleFactor).sp
    val fontTitle: TextUnit get() = (16f * scaleFactor).sp
    val fontHeadline: TextUnit get() = (18f * scaleFactor).sp
    val fontLargeTitle: TextUnit get() = (20f * scaleFactor).sp
    val fontDisplay: TextUnit get() = (24f * scaleFactor).sp
    val fontDisplayLarge: TextUnit get() = (28f * scaleFactor).sp
    val fontDisplayXLarge: TextUnit get() = (32f * scaleFactor).sp

    // Icon sizes - scaled versions
    val iconTiny: Dp get() = (12f * scaleFactor).dp
    val iconSmall: Dp get() = (16f * scaleFactor).dp
    val iconMedium: Dp get() = (20f * scaleFactor).dp
    val iconNormal: Dp get() = (24f * scaleFactor).dp
    val iconLarge: Dp get() = (28f * scaleFactor).dp
    val iconXLarge: Dp get() = (32f * scaleFactor).dp

    // Padding/spacing - scaled versions
    val spacingTiny: Dp get() = (4f * scaleFactor).dp
    val spacingSmall: Dp get() = (8f * scaleFactor).dp
    val spacingMedium: Dp get() = (12f * scaleFactor).dp
    val spacingNormal: Dp get() = (16f * scaleFactor).dp
    val spacingLarge: Dp get() = (20f * scaleFactor).dp
    val spacingXLarge: Dp get() = (24f * scaleFactor).dp

    // Component heights - scaled versions
    val buttonHeight: Dp get() = (48f * scaleFactor).dp
    val inputFieldHeight: Dp get() = (48f * scaleFactor).dp
    val listItemHeight: Dp get() = (56f * scaleFactor).dp

    // Helper functions for custom scaling
    fun scaledSp(size: Float): TextUnit = (size * scaleFactor).sp
    fun scaledDp(size: Float): Dp = (size * scaleFactor).dp
}

/**
 * CompositionLocal for accessing responsive dimensions throughout the app
 */
val LocalResponsiveDimensions = compositionLocalOf {
    ResponsiveDimensions(1f, false, 400)
}

/**
 * Calculate responsive dimensions based on screen configuration
 */
@Composable
fun rememberResponsiveDimensions(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    return remember(screenWidthDp) {
        val scaleFactor = when {
            // Small phones - scale down significantly
            screenWidthDp < SMALL_PHONE_WIDTH -> 0.70f
            // Medium phones (like 407dp) - scale down
            screenWidthDp < MEDIUM_PHONE_WIDTH -> 0.80f
            // Large phones but not tablets - slight scale down
            screenWidthDp < LARGE_PHONE_WIDTH -> 0.90f
            // Tablets and large devices - no scaling
            else -> 1f
        }

        val isSmallScreen = screenWidthDp < MEDIUM_PHONE_WIDTH

        ResponsiveDimensions(
            scaleFactor = scaleFactor,
            isSmallScreen = isSmallScreen,
            screenWidthDp = screenWidthDp
        )
    }
}

/**
 * Provider composable to wrap content with responsive dimensions
 */
@Composable
fun ResponsiveProvider(content: @Composable () -> Unit) {
    val dimensions = rememberResponsiveDimensions()
    CompositionLocalProvider(LocalResponsiveDimensions provides dimensions) {
        content()
    }
}

/**
 * Extension functions for easy inline scaling
 */
@Composable
fun Float.scaledSp(): TextUnit {
    val dimensions = LocalResponsiveDimensions.current
    return (this * dimensions.scaleFactor).sp
}

@Composable
fun Int.scaledSp(): TextUnit {
    val dimensions = LocalResponsiveDimensions.current
    return (this * dimensions.scaleFactor).sp
}

@Composable
fun Float.scaledDp(): Dp {
    val dimensions = LocalResponsiveDimensions.current
    return (this * dimensions.scaleFactor).dp
}

@Composable
fun Int.scaledDp(): Dp {
    val dimensions = LocalResponsiveDimensions.current
    return (this * dimensions.scaleFactor).dp
}
