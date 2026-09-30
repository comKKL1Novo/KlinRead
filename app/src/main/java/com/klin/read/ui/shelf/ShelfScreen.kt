package com.klin.read.ui.shelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.klin.read.data.BookEntity
import com.klin.read.data.ReadingStats
import com.klin.read.ui.design.Chip
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
import java.io.File

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
    val visible by viewModel.visibleBooks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedKey by viewModel.selected.collectAsStateWithLifecycle()
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
                    is ShelfMessage.Notice -> it.text
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
                // Category filters with counts. Horizontally scrollable so a long
                // list of custom categories never wraps or clips.
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        contentPadding = PaddingValues(vertical = Space.xs)
                    ) {
                        items(categories, key = { it.key }) { category ->
                            Chip(
                                label = "${category.label} ${category.count}",
                                selected = category.key == selectedKey,
                                onClick = { viewModel.select(category.key) }
                            )
                        }
                    }
                }

                if (visible.isEmpty()) {
                    item {
                        EmptyHint(
                            title = "这个分类还没有书",
                            detail = "换一个分类看看，或导入新的电子书"
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            progress = viewModel.progressFor(book),
                            onClick = { onOpenBook(book.id) },
                            onRemove = { viewModel.remove(book) },
                            onToggleFinished = { viewModel.toggleFinished(book) },
                            onSetCategory = { name -> viewModel.setCategory(book, name) },
                            existingCategories = categories
                                .filter { it.key.startsWith("cat:") }
                                .map { it.label }
                        )
                    }
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

/**
 * One book, as a cover thumbnail plus its details.
 *
 * The cover is drawn from the cached file when extraction succeeded, and from a
 * generated gradient otherwise — TXT and UMD never have one, so the placeholder
 * is the normal case rather than an error state.
 */
@Composable
private fun BookRow(
    book: BookEntity,
    progress: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleFinished: () -> Unit,
    onSetCategory: (String?) -> Unit,
    existingCategories: List<String>
) {
    val c = LocalColors.current
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        BookActionsSheet(
            book = book,
            existingCategories = existingCategories,
            onDismiss = { showMenu = false },
            onToggleFinished = {
                showMenu = false
                onToggleFinished()
            },
            onSetCategory = { name ->
                showMenu = false
                onSetCategory(name)
            },
            onRemove = {
                showMenu = false
                onRemove()
            }
        )
    }

    Panel(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press opens the actions sheet; a tap would otherwise conflict
            // with opening the book.
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.md),
            verticalAlignment = Alignment.Top
        ) {
            BookCover(book = book, modifier = Modifier.size(width = 62.dp, height = 88.dp))

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.md, top = 2.dp)
            ) {
                Text(
                    text = book.title,
                    color = c.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
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
                    modifier = Modifier.padding(top = 5.dp)
                )

                if (!book.category.isNullOrBlank()) {
                    Text(
                        text = book.category!!,
                        color = c.inkFaint,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(Space.sm))

                // Reading progress, matching the slider track.
                Box(
                    Modifier
                        .fillMaxWidth()
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
    }
}

/**
 * Long-press actions for one book: rename its category, flip the finished flag,
 * or remove it.
 *
 * A bottom sheet rather than a dropdown, so the category list has room to grow
 * and the destructive action is separated from the everyday ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookActionsSheet(
    book: BookEntity,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onToggleFinished: () -> Unit,
    onSetCategory: (String?) -> Unit,
    onRemove: () -> Unit
) {
    val c = LocalColors.current
    var newCategory by remember { mutableStateOf("") }
    var showCategoryInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xl)) {
            Text(
                text = book.title,
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(Space.lg))

            ListRow(
                title = if (book.isFinished) "标记为未读完" else "标记为读完",
                subtitle = if (book.isFinished) "从「读完」中移出" else "加入「读完」分类",
                onClick = onToggleFinished
            )
            Hairline()

            SectionLabel("分类")
            if (existingCategories.isEmpty()) {
                Text(
                    text = "还没有分类，新建一个试试",
                    color = c.inkFaint,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(bottom = Space.sm)
                ) {
                    item {
                        Chip(
                            label = "未分类",
                            selected = book.category.isNullOrBlank(),
                            onClick = { onSetCategory(null) }
                        )
                    }
                    items(existingCategories, key = { it }) { name ->
                        Chip(
                            label = name,
                            selected = book.category == name,
                            onClick = { onSetCategory(name) }
                        )
                    }
                }
            }

            if (showCategoryInput) {
                FlatTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it },
                    placeholder = "分类名称，如「小说」",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.sm))
                PrimaryButton(
                    text = "创建并加入",
                    onClick = {
                        if (newCategory.isNotBlank()) onSetCategory(newCategory.trim())
                    },
                    enabled = newCategory.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                QuietButton(
                    text = "新建分类",
                    onClick = { showCategoryInput = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(Space.lg))
            Hairline()
            ListRow(title = "从书架移除", onClick = onRemove)
        }
    }
}

/**
 * The cover thumbnail.
 *
 * When a cached cover exists it is loaded through Coil; otherwise a gradient with
 * the title's first character is drawn. A finished book gets a corner badge.
 */
@Composable
private fun BookCover(book: BookEntity, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        val coverPath = book.coverPath
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GeneratedCover(title = book.title, modifier = Modifier.fillMaxSize())
        }

        // "已读完" marker, pinned to the top-right of the cover.
        if (book.isFinished) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "读完",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatCharCount(count: Int): String = when {
    count >= 10_000 -> "${count / 10_000} 万字"
    else -> "$count 字"
}
