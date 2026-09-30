package com.klin.read.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klin.read.ui.design.LocalColors

enum class HomeTab(val label: String) {
    SHELF("书架"),
    MUSIC("音乐"),
    SETTINGS("设置"),
    ABOUT("作者");

    val icon: ImageVector
        get() = when (this) {
            SHELF -> Icons.Filled.MenuBook
            MUSIC -> Icons.Filled.LibraryMusic
            SETTINGS -> Icons.Filled.Settings
            ABOUT -> Icons.Filled.Info
        }
}

/**
 * Floating pill navigation bar with a frosted-glass treatment.
 *
 * The bar is inset from the screen edges and fully rounded, so the app background
 * shows around it -- that is what makes it read as a floating control rather than
 * part of the chrome. The glass look comes from a translucent fill, a brighter
 * 1dp rim and a soft sheen; a real backdrop blur needs API 31+, so the fill is
 * tuned to work over the flat background at any version.
 */
@Composable
fun BottomBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val dark = c.canvas.luminance() < 0.5f

    // Glass tokens, derived from the palette so the bar follows light/dark.
    val glassTop = if (dark) Color(0x2EFFFFFF) else Color(0xCCFFFFFF)
    val glassBottom = if (dark) Color(0x14FFFFFF) else Color(0x99FFFFFF)
    val rim = if (dark) Color(0x33FFFFFF) else Color(0xD9FFFFFF)
    val shadow = Color.Black.copy(alpha = if (dark) 0.45f else 0.12f)

    val pill: Shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(pill)
                .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
                .border(1.dp, rim, pill)
                .drawBehind {
                    // Sheen: a brighter band across the top inner edge.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (dark) 0.10f else 0.35f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.55f
                        )
                    )
                    // Faint drop shadow underneath, drawn inside the pill so no
                    // extra layer is needed.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, shadow),
                            startY = size.height * 0.7f,
                            endY = size.height
                        )
                    )
                }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                GlassTabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GlassTabItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val tint by animateColorAsState(
        targetValue = if (selected) c.ink else c.inkFaint,
        label = "tabTint"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        label = "tabScale"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        label = "pillAlpha"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(c.ink.copy(alpha = 0.10f * pillAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .size(19.dp)
                    .scale(scale)
            )
        }
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Perceived brightness, used to pick the glass tint. */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
