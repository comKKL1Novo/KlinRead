package com.klin.read.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klin.read.ui.design.MinTouchTarget
import com.klin.read.ui.design.Radius
import com.klin.read.ui.design.RoundSlider
import com.klin.read.ui.design.Size
import com.klin.read.ui.design.SliderColors
import com.klin.read.ui.design.Space

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.persistPosition() }
    }

    // Count reading time while this screen is open. The loop is tied to the
    // composition, so leaving the reader stops the clock.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            viewModel.recordReadingMinute()
        }
    }

    when (val current = state) {
        is ReaderUiState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        is ReaderUiState.Failed -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                current.reason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        is ReaderUiState.Ready -> {
            val palette = paletteFor(current.settings.theme)

            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    // Gestures live on the container, not on an overlay above the
                    // text. Tapping only toggles the chrome; chapter changes come
                    // from swiping or the toolbar.
                    .readerTaps(onTap = { chromeVisible = !chromeVisible })
            ) {
                PagedChapter(
                    body = current.body,
                    settings = current.settings,
                    chapterTitle = current.chapter.title,
                    palette = palette,
                    startParagraph = current.paragraphIndex,
                    chapterIndex = current.chapterIndex,
                    chapterCount = current.book.chapters.size,
                    onParagraphVisible = viewModel::onParagraphVisible,
                    onPreviousChapter = viewModel::previousChapter,
                    onNextChapter = viewModel::nextChapter
                )

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ReaderTopBar(
                        title = current.chapter.title,
                        palette = palette,
                        onBack = onBack,
                        onToc = { showToc = true },
                        onSettings = { showSettings = true }
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ReaderControlBar(
                        chapterIndex = current.chapterIndex,
                        chapterCount = current.book.chapters.size,
                        palette = palette,
                        onPrevious = { viewModel.previousChapter() },
                        onNext = { viewModel.nextChapter() },
                        onToc = { showToc = true },
                        onSettings = { showSettings = true },
                        onSeek = { viewModel.goToChapter(it) }
                    )
                }

                if (showToc) {
                    ModalBottomSheet(
                        onDismissRequest = { showToc = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        // Sheets use the reading surface colours. Translucent
                        // tokens made the labels and tracks hard to see here.
                        containerColor = palette.background,
                        contentColor = palette.text,
                        scrimColor = Color.Black.copy(alpha = 0.35f),
                        dragHandle = { SheetHandle(palette.divider) }
                    ) {
                        TocSheet(
                            chapters = current.book.chapters.map { it.title },
                            currentIndex = current.chapterIndex,
                            palette = palette,
                            onSelect = {
                                viewModel.goToChapter(it)
                                showToc = false
                            }
                        )
                    }
                }

                if (showSettings) {
                    ModalBottomSheet(
                        onDismissRequest = { showSettings = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = palette.background,
                        contentColor = palette.text,
                        scrimColor = Color.Black.copy(alpha = 0.35f),
                        dragHandle = { SheetHandle(palette.divider) }
                    ) {
                        SettingsSheet(
                            settings = current.settings,
                            onFontSize = viewModel::setFontSize,
                            onLineHeight = viewModel::setLineHeight,
                            onMargin = viewModel::setMargin,
                            onTheme = viewModel::setTheme,
                            onPageTurn = viewModel::setPageTurn
                        )
                    }
                }
            }
        }
    }
}

/** Drag handle for the bottom sheets: a short centred bar. */
@Composable
private fun SheetHandle(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(Size.sheetHandleWidth)
                .height(Size.sheetHandleHeight)
                .clip(RoundedCornerShape(Size.sheetHandleHeight / 2))
                .background(color)
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    palette: ReadingPalette,
    onBack: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .statusBarsPadding()
            .padding(horizontal = Space.sm, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderIconButton(onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = palette.text)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = palette.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ReaderIconButton(onToc) {
            Icon(Icons.AutoMirrored.Filled.List, "目录", tint = palette.text)
        }
        ReaderIconButton(onSettings) {
            Icon(Icons.Filled.Settings, "阅读设置", tint = palette.text)
        }
    }
}

@Composable
private fun ReaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.button))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            // 44dp keeps these icon buttons on the skill's minimum touch target;
            // the visible glyph stays small because the padding absorbs the rest.
            .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .padding(Space.xs),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ReaderControlBar(
    chapterIndex: Int,
    chapterCount: Int,
    palette: ReadingPalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onSeek: (Int) -> Unit
) {
    // The slider shows the dragged value while the finger is down, then hands
    // control back once the chapter actually changes.
    var dragging by remember { mutableStateOf(false) }
    var localIndex by remember { mutableStateOf(chapterIndex.toFloat()) }
    val shown = if (dragging) localIndex else chapterIndex.toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = Space.md, vertical = Space.sm)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (chapterCount > 0) "${shown.toInt() + 1} / $chapterCount 章" else "—",
                color = palette.secondary,
                fontSize = 12.sp,
                letterSpacing = (-0.011).em
            )
            Text(
                text = "拖动跳章",
                color = palette.secondary,
                fontSize = 11.sp,
                letterSpacing = (-0.011).em
            )
        }

        RoundSlider(
            value = shown,
            valueRange = 0f..((chapterCount - 1).coerceAtLeast(1)).toFloat(),
            enabled = chapterCount > 1,
            onValueChange = {
                dragging = true
                localIndex = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(localIndex.toInt())
            },
            colors = SliderColors(
                track = palette.text.copy(alpha = 0.16f),
                active = palette.text.copy(alpha = 0.5f),
                thumb = palette.text
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderActionButton("上一章", palette, chapterIndex > 0, onPrevious)
            ReaderActionButton("目录", palette, true, onToc)
            ReaderActionButton("设置", palette, true, onSettings)
            ReaderActionButton("下一章", palette, chapterIndex < chapterCount - 1, onNext)
        }
    }
}

@Composable
private fun ReaderActionButton(
    label: String,
    palette: ReadingPalette,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.button))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = MinTouchTarget)
            .padding(horizontal = Space.md, vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) palette.text else palette.secondary.copy(alpha = 0.35f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.011).em,
            maxLines = 1
        )
    }
}

/**
 * Table of contents.
 *
 * Uses the reading palette rather than app colours: the reading surface may be
 * sepia or dark, and the sheet sits on it.
 */
@Composable
private fun TocSheet(
    chapters: List<String>,
    currentIndex: Int,
    palette: ReadingPalette,
    onSelect: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "目录",
                color = palette.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.022).em
            )
            Text(
                text = "共 ${chapters.size} 章",
                color = palette.secondary,
                fontSize = 12.sp,
                letterSpacing = (-0.011).em
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = Space.xl),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(chapters) { index, title ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .defaultMinSize(minHeight = MinTouchTarget)
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        color = palette.secondary,
                        fontSize = 12.sp,
                        letterSpacing = (-0.011).em,
                        modifier = Modifier.padding(end = Space.md)
                    )
                    Text(
                        text = title,
                        color = if (isCurrent) palette.text else palette.text.copy(alpha = 0.78f),
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp,
                        letterSpacing = (-0.011).em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrent) {
                        Text("正在读", color = palette.secondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
