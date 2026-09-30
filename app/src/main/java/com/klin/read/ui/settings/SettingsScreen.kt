package com.klin.read.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klin.read.ui.design.Chip
import com.klin.read.ui.design.Hairline
import com.klin.read.ui.design.ListRow
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Panel
import com.klin.read.ui.design.RoundSlider
import com.klin.read.ui.design.ScreenTitle
import com.klin.read.ui.design.SectionLabel
import com.klin.read.ui.design.Space

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    /** Brightness being dragged, held by the host so the window dims live. */
    liveBrightness: Float? = null,
    onBrightnessPreview: (Float) -> Unit = {}
) {
    val c = LocalColors.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.lg,
            end = Space.lg,
            top = Space.xl,
            bottom = 140.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item {
            ScreenTitle("设置")
            Text(
                text = "阅读偏好和外观",
                color = c.inkMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item {
            // Read inside the item: a value captured outside a LazyColumn item is
            // not re-read when it changes, so the slider would look stuck.
            val live = viewModel.state.collectAsStateWithLifecycle().value

            SectionLabel("阅读")
            Panel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                BrightnessRow(
                    value = liveBrightness ?: live.brightness,
                    // Report upward immediately so the window dims while the
                    // finger is still moving, then let the ViewModel persist.
                    onPreview = { value ->
                        onBrightnessPreview(value)
                        viewModel.previewBrightness(value)
                    },
                    onCommit = { value ->
                        onBrightnessPreview(value)
                        viewModel.commitBrightness(value)
                    }
                )
                Hairline()
                Column(Modifier.padding(Space.lg)) {
                    Text("阅读主题", color = c.ink, fontSize = 15.sp)
                    Row(
                        Modifier.padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        ThemeChoice.entries.forEach { choice ->
                            Chip(
                                label = choice.label(),
                                selected = live.theme == choice,
                                onClick = { viewModel.setTheme(choice) }
                            )
                        }
                    }
                }
            }
        }

        item {
            val appearance = viewModel.state.collectAsStateWithLifecycle().value

            SectionLabel("外观")
            Panel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                ListRow(
                    title = "深色",
                    subtitle = "整个应用使用深色配色",
                    trailing = {
                        Switch(
                            checked = appearance.darkGlass,
                            onCheckedChange = viewModel::setDarkGlass,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = c.accentInk,
                                checkedTrackColor = c.accent
                            )
                        )
                    }
                )
            }
        }

        item {
            SectionLabel("关于")
            Panel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                ListRow(title = "版本", subtitle = "0.0.1")
                Hairline()
                ListRow(
                    title = "支持的格式",
                    subtitle = "TXT、EPUB、FB2、HTML、MOBI/AZW、UMD"
                )
            }
        }

        item {
            Text(
                text = "BETA 测试版",
                color = c.inkFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.lg, bottom = Space.xs),
                textAlign = TextAlign.Center
            )
        }

        item {
            Text(
                text = "本应用不联网、不上传。书架和音乐都只读取你自己导入的文件。",
                color = c.inkFaint,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = Space.sm, start = Space.xs)
            )
        }
    }
}

/**
 * Brightness control.
 *
 * Adjusts this app's window only; see [ReaderSettings.brightness] for why the
 * system setting is not touched.
 */
@Composable
private fun BrightnessRow(
    value: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit
) {
    val c = LocalColors.current
    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("亮度", color = c.ink, fontSize = 15.sp)
            Text("${(value * 100).toInt()}%", color = c.inkMuted, fontSize = 13.sp)
        }
        RoundSlider(
            value = value,
            valueRange = 0.05f..1f,
            onValueChange = onPreview,
            onValueChangeFinished = onCommit
        )
    }
}
