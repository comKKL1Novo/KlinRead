package com.klin.read.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Generated cover for books that carry no cover art.
 *
 * TXT, HTML and UMD files have no image to extract, and that is most of a
 * Chinese-language shelf. Rather than show a blank rectangle, the title's first
 * character is drawn over a two-tone gradient.
 *
 * The gradient pair is picked from a fixed palette by hashing the title, so the
 * same book always gets the same colours across restarts and devices.
 */
private val PLACEHOLDER_PAIRS = listOf(
    Color(0xFFFF9BD2) to Color(0xFFD93A88), // icon pink
    Color(0xFF9BB8FF) to Color(0xFF4A5FD9),
    Color(0xFF9BE8C4) to Color(0xFF2A9D6E),
    Color(0xFFFFD79B) to Color(0xFFD98A2B),
    Color(0xFFD5A6FF) to Color(0xFF7C3ACD),
    Color(0xFF9BE0F5) to Color(0xFF2A7E9D),
)

@Composable
fun GeneratedCover(
    title: String,
    modifier: Modifier = Modifier
) {
    val pair = PLACEHOLDER_PAIRS[
        (title.hashCode().let { if (it < 0) -it else it }) % PLACEHOLDER_PAIRS.size
    ]
    val initial = title.trim().firstOrNull()?.toString() ?: "书"

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Diagonal gradient so the card does not read as a flat swatch.
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(pair.first, pair.second),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }
        Text(
            text = initial,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
