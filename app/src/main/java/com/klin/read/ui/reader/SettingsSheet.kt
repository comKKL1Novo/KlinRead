package com.klin.read.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.klin.read.data.PageTurnMode
import com.klin.read.data.ReaderPreferences
import com.klin.read.data.ReaderSettings
import com.klin.read.data.ReaderTheme
import com.klin.read.ui.design.RoundSlider
import com.klin.read.ui.design.SliderColors

/**
 * Reading settings sheet.
 *
 * Uses [RoundSlider] and the opaque reading palette. Material's slider was still
 * in use here after the round one was introduced, so this panel kept the old
 * capsule thumb.
 */
@Composable
fun SettingsSheet(
    settings: ReaderSettings,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMargin: (Float) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onPageTurn: (PageTurnMode) -> Unit
) {
    val palette = paletteFor(settings.theme)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 34.dp)
    ) {
        SheetSlider(
            label = "字号",
            valueLabel = "${settings.fontSizeSp.toInt()}",
            value = settings.fontSizeSp,
            range = ReaderPreferences.MIN_FONT..ReaderPreferences.MAX_FONT,
            palette = palette,
            onChange = onFontSize
        )

        SheetSlider(
            label = "行距",
            valueLabel = String.format("%.1f", settings.lineHeightMultiplier),
            value = settings.lineHeightMultiplier,
            range = ReaderPreferences.MIN_LINE_HEIGHT..ReaderPreferences.MAX_LINE_HEIGHT,
            palette = palette,
            onChange = onLineHeight
        )

        SheetSlider(
            label = "页边距",
            valueLabel = "${settings.marginDp.toInt()}",
            value = settings.marginDp,
            range = ReaderPreferences.MIN_MARGIN..ReaderPreferences.MAX_MARGIN,
            palette = palette,
            onChange = onMargin
        )

        SheetSection("翻页方式", palette) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageTurnMode.entries.forEach { mode ->
                    ChoiceChip(
                        label = mode.label(),
                        selected = mode == settings.pageTurn,
                        palette = palette,
                        onClick = { onPageTurn(mode) }
                    )
                }
            }
        }

        SheetSection("主题", palette) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    ChoiceChip(
                        label = theme.label(),
                        selected = theme == settings.theme,
                        palette = palette,
                        onClick = { onTheme(theme) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    palette: ReadingPalette,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = palette.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = valueLabel,
                color = palette.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(2.dp))
        RoundSlider(
            value = value,
            valueRange = range,
            onValueChange = onChange,
            // Track colours come from the reading palette, which is opaque; the
            // app palette would be wrong against a sepia or dark reading surface.
            colors = SliderColors(
                track = palette.divider,
                active = palette.text.copy(alpha = 0.75f),
                thumb = palette.text
            )
        )
    }
}

@Composable
private fun SheetSection(
    title: String,
    palette: ReadingPalette,
    content: @Composable () -> Unit
) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            color = palette.secondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    palette: ReadingPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.text else palette.divider.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) palette.background else palette.text.copy(alpha = 0.8f),
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            fontSize = 13.5.sp
        )
    }
}

fun PageTurnMode.label(): String = when (this) {
    PageTurnMode.SCROLL -> "上下"
    PageTurnMode.SLIDE -> "左右"
}

fun ReaderTheme.label(): String = when (this) {
    ReaderTheme.LIGHT -> "浅色"
    ReaderTheme.DARK -> "深色"
    ReaderTheme.SEPIA -> "护眼"
}
