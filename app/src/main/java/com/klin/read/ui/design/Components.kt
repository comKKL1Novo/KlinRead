package com.klin.read.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Wraps a clickable so it presses down to 0.96, per the skill's active state.
 *
 * The skill also specifies `scale(1.02)` on hover; that is reachable on a phone
 * with a pointer, but applying a pointer-only effect needs a hoverable modifier
 * and is out of scope for this pass. The press state is the one that matters on a
 * touch-first device.
 */
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.pop(),
        label = "press"
    )
    return scale
}

/**
 * A raised surface panel.
 *
 * The skill requires every elevated surface to carry BOTH an rgba fill and a
 * blur ("Does the background have BOTH rgba transparency and backdrop-filter?").
 * Compose has no `backdrop-filter`; `Modifier.blur` blurs a composable's own
 * content and needs API 31+, so applying it here would either make the text
 * unreadable or silently no-op on older devices.
 *
 * The material is therefore expressed with the parts that ARE portable:
 *
 *   - a translucent fill ([AppColors.glassFill]),
 *   - a 0.5dp hairline border ([AppColors.glassBorder]),
 *   - a soft drop shadow for depth ([AppColors.glassShadow]),
 *
 * which is the same visual language minus the blur, and degrades predictably on
 * API 24. The tint is drawn from the theme's surface, so the seeded pink identity
 * survives.
 *
 * The default radius is the skill's card value (20dp); callers passing
 * `contentPadding = 0.dp` are using this as a bare list container and keep the
 * radius either way.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.card,
    filled: Boolean = true,
    contentPadding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .then(
                if (filled) {
                    Modifier
                        .shadow(
                            elevation = 2.dp,
                            shape = shape,
                            ambientColor = c.glassShadow,
                            spotColor = c.glassShadow
                        )
                        .clip(shape)
                        .background(c.glassFill)
                        // 0.5dp is the skill's hairline; the skill's own checklist
                        // exempts 1px borders from the 8pt rule.
                        .border(0.5.dp, c.glassBorder, shape)
                } else {
                    Modifier.clip(shape)
                }
            )
            .padding(contentPadding),
        content = content
    )
}

/**
 * Section label: small, spaced, muted.
 *
 * Uses 0.06em tracking rather than a hardcoded sp value, so it scales with the
 * font instead of drifting from it.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.06.em,
        modifier = modifier.padding(start = Space.xs, bottom = Space.sm, top = Space.md)
    )
}

/**
 * Screen title.
 *
 * The style already carries the skill's -0.022em headline tracking via
 * [AppTypography]; the previous hardcoded `(-0.5).sp` overrode it with a value
 * that does not scale with font size.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Hairline divider, on the skill's 0.5dp rather than a full pixel. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LocalColors.current.glassBorder)
    )
}

/**
 * Text input.
 *
 * Follows the skill's search-input spec: a compact field on a translucent
 * recessed fill rather than an opaque one. The 4.5:1 contrast requirement is why
 * the placeholder uses `inkFaint` (a theme role) rather than a fixed grey -- a
 * hardcoded grey is what made an earlier error message invisible on a dark
 * background.
 */
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
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.button))
            .background(c.fieldFill)
            .border(0.5.dp, c.glassBorder, RoundedCornerShape(Radius.button))
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.padding(end = Space.sm))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.copy(
                    color = c.ink,
                    fontSize = 15.sp,
                    letterSpacing = (-0.011).em
                ),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    color = c.inkFaint,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Primary button.
 *
 * The skill's blueprint exactly: height 44, horizontal padding 24, radius 12, and
 * a drop shadow. Presses to 0.96.
 *
 * The previous version was a full pill (`999.dp`). The skill reserves the pill
 * for chips and uses a 12px radius for buttons, so the two no longer look alike.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val c = LocalColors.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
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
    val shape = RoundedCornerShape(Radius.button)
    Box(
        modifier = modifier
            .scale(scale)
            .then(
                if (enabled) {
                    Modifier.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        ambientColor = c.glassShadow,
                        spotColor = c.glassShadow
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(horizontal = Space.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.011).em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Secondary button: translucent fill, same geometry as the primary. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val c = LocalColors.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val shape = RoundedCornerShape(Radius.button)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(c.fieldFill)
            .border(0.5.dp, c.glassBorder, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(horizontal = Space.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) c.ink else c.inkFaint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Standard list row.
 *
 * Minimum height is the skill's 44px touch target; the previous 16dp vertical
 * padding gave a comfortable row but no guaranteed floor for a one-line row.
 */
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
            .defaultMinSize(minHeight = MinTouchTarget)
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
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.padding(end = Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = c.ink,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = c.inkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs)
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * Filter chip.
 *
 * Stays a pill -- that is the one shape the skill explicitly reserves for
 * tags/badges -- but the press state and the minimum height now match the rest of
 * the system.
 */
@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.accent else c.fieldFill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) c.accentInk else c.inkMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 1
        )
    }
}

/** Empty-state block. */
@Composable
fun EmptyHint(title: String, detail: String, modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = c.inkMuted,
            fontSize = 15.sp,
            letterSpacing = (-0.011).em
        )
        Text(
            detail,
            color = c.inkFaint,
            fontSize = 12.5.sp,
            letterSpacing = (-0.011).em,
            modifier = Modifier.padding(top = Space.sm)
        )
    }
}
