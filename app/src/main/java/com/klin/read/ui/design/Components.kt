package com.klin.read.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A flat surface panel.
 *
 * No gradient, no blur, no glow: a solid fill and generous padding. This is the
 * main change from the earlier glass treatment.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    filled: Boolean = true,
    contentPadding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (filled) c.surface else Color.Transparent)
            .padding(contentPadding),
        content = content
    )
}

/** Section label: small, spaced, muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Text(
        text = text,
        color = c.inkFaint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = Space.xs, bottom = Space.sm, top = Space.md)
    )
}

/** Screen title, sized like a heading rather than a banner. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Text(
        text = text,
        color = c.ink,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

/** Hairline divider. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.divider)
    )
}

/** Text input on a flat surface. */
@Composable
fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    val c = LocalColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceMuted)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.padding(end = 8.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(color = c.ink, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(placeholder, color = c.inkFaint, fontSize = 15.sp)
            }
        }
    }
}

/** Primary button: solid accent, no gradient. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val c = LocalColors.current
    val bg = when {
        !enabled -> c.surfaceMuted
        destructive -> c.danger
        else -> c.accent
    }
    val fg = when {
        !enabled -> c.inkFaint
        destructive -> Color.White
        else -> c.accentInk
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Secondary button: muted fill only. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceMuted)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) c.ink else c.inkFaint, fontSize = 15.sp)
    }
}

/** Standard list row. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val c = LocalColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Space.lg, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.padding(end = 14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = c.inkMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Pill-shaped filter chip. */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.accent else c.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) c.accentInk else c.inkMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** Empty-state block. */
@Composable
fun EmptyHint(title: String, detail: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = c.inkMuted, fontSize = 15.sp)
        Text(
            detail,
            color = c.inkFaint,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
