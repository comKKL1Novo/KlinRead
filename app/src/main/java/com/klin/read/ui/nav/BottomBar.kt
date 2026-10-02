package com.klin.read.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Motion
import com.klin.read.ui.design.Size
import com.klin.read.ui.design.Space

enum class HomeTab(val label: String) {
    SHELF("书架"),
    MUSIC("音乐"),
    SETTINGS("设置"),
    ABOUT("作者");

    val icon: ImageVector
        get() = when (this) {
            SHELF -> Icons.AutoMirrored.Filled.MenuBook
            MUSIC -> Icons.Filled.LibraryMusic
            SETTINGS -> Icons.Filled.Settings
            ABOUT -> Icons.Filled.Info
        }
}

/**
 * Floating pill navigation bar, on the skill's glass material.
 *
 * The skill's navigation-bar blueprint: a sticky, elevated surface carrying the
 * glass material (translucent fill + blur + 0.5px hairline). The bar is inset from
 * the screen edges and fully rounded, so the app background shows around it --
 * that is what makes it read as a floating control rather than part of the chrome.
 *
 * The blur half of the material is not reproducible: `Modifier.blur` needs API 31+
 * and would silently no-op below that, so the fill carries the effect alone. The
 * tokens come from [AppColors] rather than local literals so the bar cannot drift
 * from the cards it floats above.
 */
@Composable
fun BottomBar(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val pill: Shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 4.dp,
                    shape = pill,
                    ambientColor = c.glassShadow,
                    spotColor = c.glassShadow
                )
                .clip(pill)
                .background(c.glassFill)
                .border(0.5.dp, c.glassBorder, pill)
                .padding(horizontal = Space.sm, vertical = Space.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                NavTabItem(
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
private fun NavTabItem(
    tab: HomeTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val tint by animateColorAsState(
        targetValue = if (selected) c.ink else c.inkFaint,
        animationSpec = tween(Motion.STANDARD_MS, easing = Motion.standard),
        label = "tabTint"
    )
    // Spring motion: the icon springs into place rather than easing flatly.
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = Motion.spatial(),
        label = "tabScale"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = Motion.effects(),
        label = "pillAlpha"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = Size.navIndicatorWidth, height = Size.navIndicatorHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(c.accent.copy(alpha = 0.14f * pillAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier
                    .size(Size.navIcon)
                    .scale(scale)
            )
        }
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 1,
            modifier = Modifier.padding(top = Space.xs)
        )
    }
}
