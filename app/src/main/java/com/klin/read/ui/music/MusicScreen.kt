package com.klin.read.ui.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klin.read.data.Track
import com.klin.read.ui.design.EmptyHint
import com.klin.read.ui.design.FlatTextField
import com.klin.read.ui.design.Hairline
import com.klin.read.ui.design.ListRow
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Panel
import com.klin.read.ui.design.PrimaryButton
import com.klin.read.ui.design.QuietButton
import com.klin.read.ui.design.ScreenTitle
import com.klin.read.ui.design.SectionLabel
import com.klin.read.ui.design.Space

/**
 * Music tab.
 *
 * Only music the user imported appears here, and the search box filters that same
 * list. There is no catalogue, no streaming and no network access.
 */
@Composable
fun MusicScreen(viewModel: MusicViewModel) {
    val c = LocalColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.import(it) }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val visible = viewModel.filtered(state.tracks)

    Box(Modifier.fillMaxSize()) {
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
                ScreenTitle("音乐")
                Text(
                    text = "只播放你自己导入的音轨",
                    color = c.inkMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = Space.md)
                )
            }

            item {
                FlatTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = "搜索已导入的音乐"
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(top = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    PrimaryButton(
                        text = "导入音乐",
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f)
                    )
                    if (isPlaying) {
                        QuietButton(
                            text = "停止",
                            onClick = { viewModel.stop() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Panel(modifier = Modifier.padding(top = Space.sm)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("读书时自动播放", color = c.ink, fontSize = 15.sp)
                            Text(
                                text = "打开书籍后自动开始播放当前音轨",
                                color = c.inkMuted,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Switch(
                            checked = state.autoPlayInReader,
                            onCheckedChange = viewModel::setAutoPlayAsync
                        )
                    }
                    Hairline(Modifier.padding(vertical = Space.md))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("单曲循环", color = c.ink, fontSize = 15.sp)
                        Switch(
                            checked = state.loop,
                            onCheckedChange = viewModel::setLoop
                        )
                    }
                }
            }

            if (state.tracks.isEmpty()) {
                item {
                    EmptyHint(
                        title = "还没有音乐",
                        detail = "点「导入音乐」选择本机的音频文件"
                    )
                }
            } else if (visible.isEmpty()) {
                item {
                    EmptyHint(
                        title = "没有匹配的音乐",
                        detail = "「$query」在你导入的 ${state.tracks.size} 首里找不到"
                    )
                }
            } else {
                item { SectionLabel("已导入 ${visible.size} 首") }
                items(visible, key = { it.uri }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.uri == state.currentUri,
                        isPlaying = isPlaying && track.uri == state.currentUri,
                        onToggle = { viewModel.toggleCurrent(track) },
                        onRemove = { viewModel.remove(track) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp)
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val c = LocalColors.current
    Panel(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        ListRow(
            title = track.title,
            subtitle = when {
                isPlaying -> "正在播放"
                isCurrent -> "已选中"
                else -> "点击播放"
            },
            onClick = onToggle,
            leading = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isCurrent) c.accent else c.surfaceMuted)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPlaying) "❚❚" else "▶",
                        color = if (isCurrent) c.accentInk else c.inkMuted,
                        fontSize = 11.sp
                    )
                }
            },
            trailing = {
                Text(
                    text = "移除",
                    color = c.inkFaint,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRemove)
                        // Material's minimum touch target: the label is small, so
                        // the tap area is widened to 48dp rather than relying on
                        // the text bounds alone.
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .wrapContentSize(Alignment.Center)
                )
            }
        )
    }
}
