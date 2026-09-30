package com.klin.read.ui.shelf

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klin.read.data.BookEntity
import com.klin.read.data.ReadingStats
import com.klin.read.ui.design.EmptyHint
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Panel
import com.klin.read.ui.design.PrimaryButton
import com.klin.read.ui.design.ScreenTitle
import com.klin.read.ui.design.SectionLabel
import com.klin.read.ui.design.Space

/**
 * The shelf tab.
 *
 * Reading time sits at the top, the list in the middle, and the import action at
 * the bottom so it never covers a book.
 */
@Composable
fun ShelfScreen(
    viewModel: ShelfViewModel,
    onOpenBook: (Long) -> Unit
) {
    val c = LocalColors.current
    val books by viewModel.books.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // SAF picker. The app declares no storage permission because the picker grants
    // access per file.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.persistReadPermission(it)
            viewModel.import(it)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(
                when (it) {
                    is ShelfMessage.ImportFailed -> it.reason
                }
            )
            viewModel.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.lg,
                end = Space.lg,
                top = Space.xl,
                bottom = 130.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item { ScreenTitle("书架") }

            item {
                ReadingTimeCard(stats)
            }

            if (books.isEmpty()) {
                item {
                    EmptyHint(
                        title = "书架是空的",
                        detail = "点下面的「导入书籍」选择本机的电子书"
                    )
                }
            } else {
                item { SectionLabel("全部 ${books.size} 本") }
                items(books, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        progress = viewModel.progressFor(book),
                        onClick = { onOpenBook(book.id) },
                        onRemove = { viewModel.remove(book) }
                    )
                }
            }
        }

        // Import pinned to the bottom, above the navigation bar.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = 104.dp)
        ) {
            PrimaryButton(
                text = "导入书籍",
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 170.dp)
        )
    }
}

/** Reading-time summary with a daily-goal progress bar. */
@Composable
private fun ReadingTimeCard(stats: ReadingStats) {
    val c = LocalColors.current
    // A 30 minute day is the notional goal; it is only used to size the bar.
    val goalMinutes = 30
    val fraction = (stats.todayMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)

    Panel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "今日阅读",
                    color = c.inkMuted,
                    fontSize = 12.5.sp
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "${stats.todayMinutes}",
                        color = c.ink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " 分钟",
                        color = c.inkMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("累计 ${stats.totalMinutes} 分钟", color = c.inkMuted, fontSize = 12.sp)
                if (stats.streakDays > 0) {
                    Text(
                        text = "连续 ${stats.streakDays} 天",
                        color = c.inkFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        // Daily goal bar, styled like the sliders.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.surfaceMuted)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(c.accent)
            )
        }

        Spacer(Modifier.height(Space.md))
        Text(
            text = stats.encouragement,
            color = c.inkMuted,
            fontSize = 12.5.sp
        )
    }
}

@Composable
private fun BookRow(
    book: BookEntity,
    progress: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val c = LocalColors.current
    Panel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, top = Space.md, bottom = Space.md, end = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    color = c.ink,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(book.format)
                        if (book.charCount > 0) {
                            append(" · ")
                            append(formatCharCount(book.charCount))
                        }
                    },
                    color = c.inkMuted,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "移除",
                color = c.inkFaint,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(start = Space.md, top = 6.dp, bottom = 6.dp, end = 4.dp)
            )
        }

        // Reading progress, matching the slider track.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.md)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.surfaceMuted)
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(c.accent)
                )
            }
        }
    }
}

private fun formatCharCount(count: Int): String = when {
    count >= 10_000 -> "${count / 10_000} 万字"
    else -> "$count 字"
}
