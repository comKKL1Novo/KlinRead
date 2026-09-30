package com.klin.read.ui.reader

import androidx.compose.ui.graphics.Color
import com.klin.read.data.ReaderTheme

/**
 * Reading-surface colours.
 *
 * Kept separate from the app palette because the reading surface follows the
 * user's chosen theme even when the rest of the app uses the system theme.
 */
data class ReadingPalette(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color
)

fun paletteFor(theme: ReaderTheme): ReadingPalette = when (theme) {
    ReaderTheme.LIGHT -> ReadingPalette(
        background = Color(0xFFFCFBF9),
        text = Color(0xFF1C1B19),
        secondary = Color(0xFF8A857E),
        divider = Color(0xFFE8E5E0)
    )
    ReaderTheme.DARK -> ReadingPalette(
        background = Color(0xFF141414),
        text = Color(0xFFC9C5BF),
        secondary = Color(0xFF7A756E),
        divider = Color(0xFF2C2C2C)
    )
    ReaderTheme.SEPIA -> ReadingPalette(
        background = Color(0xFFF2E8D5),
        text = Color(0xFF43382A),
        secondary = Color(0xFF8C7E68),
        divider = Color(0xFFE0D3BA)
    )
}
