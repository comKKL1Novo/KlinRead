package com.klin.read.ui.design

/**
 * WCAG 2.1 relative-luminance contrast ratio between two ARGB colours.
 *
 * Takes longs rather than [androidx.compose.ui.graphics.Color] so unit tests can
 * call it on a plain JVM classpath. `Color`'s constructor lives in the Android
 * Compose artifact, which is not on this project's unit-test classpath -- a test
 * that touches `Color` fails to load at all, and a contrast test that cannot run
 * is worse than none because it looks like coverage. An earlier attempt at this
 * test file hit exactly that and silently tested nothing.
 *
 * Formula per WCAG 2.1: linearise each sRGB channel, then combine as
 * 0.2126 R + 0.7152 G + 0.0722 B. The 0.05 offsets model ambient flare.
 *
 * This lives in `ui.design` (rather than beside any one palette) because both the
 * app palette and the reading palette need it and they are tested separately.
 */
internal fun contrastRatio(argbA: Long, argbB: Long): Float {
    val la = relativeLuminance(argbA)
    val lb = relativeLuminance(argbB)
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** WCAG relative luminance of a packed 0xAARRGGBB colour. */
private fun relativeLuminance(argb: Long): Float {
    val r = linearize(((argb shr 16) and 0xFF).toInt() / 255f)
    val g = linearize(((argb shr 8) and 0xFF).toInt() / 255f)
    val b = linearize((argb and 0xFF).toInt() / 255f)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/** Undo the sRGB transfer function, per the WCAG definition. */
private fun linearize(channel: Float): Float =
    if (channel <= 0.03928f) {
        channel / 12.92f
    } else {
        Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }

/**
 * Flattens a translucent foreground over an opaque background.
 *
 * Needed because several palette values are alpha-composited labels; their real
 * contrast depends on what they land on, and measuring the un-composited value
 * would report a number the user never sees.
 */
internal fun compositeOver(foregroundArgb: Long, alpha: Float, backgroundArgb: Long): Long {
    fun channel(shift: Int): Long {
        val f = ((foregroundArgb shr shift) and 0xFF).toFloat()
        val b = ((backgroundArgb shr shift) and 0xFF).toFloat()
        return (f * alpha + b * (1f - alpha)).toLong().coerceIn(0, 255)
    }
    return 0xFF000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
