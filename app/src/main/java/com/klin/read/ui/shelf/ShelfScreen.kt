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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
// The grid's `items` and the LazyRow's `items` have the same name but different
// receiver scopes; the grid one is aliased so both can be used in this file.
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.klin.read.data.BookEntity
import com.klin.read.data.ReadingStats
import com.klin.read.ui.design.Chip
import com.klin.read.ui.design.Clearance
import com.klin.read.ui.design.EmptyHint
import com.klin.read.ui.design.FlatTextField
import com.klin.read.ui.design.Hairline
import com.klin.read.ui.design.ListRow
import com.klin.read.ui.design.LocalColors
import com.klin.read.ui.design.Panel
import com.klin.read.ui.design.PrimaryButton
import com.klin.read.ui.design.QuietButton
import com.klin.read.ui.design.Radius
import com.klin.read.ui.design.ScreenTitle
import com.klin.read.ui.design.SectionLabel
import com.klin.read.ui.design.Size
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

    // Which book's long-press actions sheet is open, if any.
    //
    // Hoisted to the screen rather than kept inside each card: the sheet needs the
    // full list of custom categories, which a card does not have, and holding it
    // here means at most one sheet can exist. When it lived inside BookRow the same
    // state was duplicated per row, so a fast scroll could leave two sheets
    // remembering themselves as open.
    var actionsFor by remember { mutableStateOf<BookEntity?>(null) }

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
        LazyVerticalGrid(
            // Fixed 3 columns rather than an adaptive width. On a phone this is the
            // Apple Books arrangement, and it keeps covers the same size down the
            // whole grid -- an adaptive count would give a wider tablet 4 or 5
            // columns and make the covers subtly different sizes per row, which is
            // exactly the misalignment the 8pt work was meant to remove.
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.lg,
                end = Space.lg,
                top = Space.xl,
                bottom = Clearance.listBottom
            ),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            // Full-width header rows span all three columns.
            item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle("书架") }

            item(span = { GridItemSpan(maxLineSpan) }) {
                KnownIssueBanner()
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                ReadingTimeCard(stats)
            }

            if (books.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(
                        title = "书架是空的",
                        detail = "点下面的「导入书籍」选择本机的电子书"
                    )
                }
            } else {
                // Category filters with counts. Horizontally scrollable so a long
                // list of custom categories never wraps or clips.
                item(span = { GridItemSpan(maxLineSpan) }) {
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyHint(
                            title = "这个分类还没有书",
                            detail = "换一个分类看看，或导入新的电子书"
                        )
                    }
                } else {
                    gridItems(visible, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            progress = viewModel.progressFor(book),
                            onClick = { onOpenBook(book.id) },
                            // The card owns the long-press gesture; the sheet is
                            // still hoisted here because it needs the whole
                            // category list, which a card does not have.
                            onLongClick = { actionsFor = book }
                        )
                    }
                }
            }
        }

        // The actions sheet for the long-pressed book.
        val target = actionsFor
        if (target != null) {
            BookActionsSheet(
                book = target,
                existingCategories = categories
                    .filter { it.key.startsWith("cat:") }
                    .map { it.label },
                onDismiss = { actionsFor = null },
                onToggleFinished = {
                    actionsFor = null
                    viewModel.toggleFinished(target)
                },
                onSetCategory = { name ->
                    actionsFor = null
                    viewModel.setCategory(target, name)
                },
                onRemove = {
                    actionsFor = null
                    viewModel.remove(target)
                }
            )
        }

        // Import pinned to the bottom, above the navigation bar.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Clearance.bottomBar)
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
                .padding(bottom = Clearance.shelfBottom)
        )
    }
}

/**
 * Notice about a defect that is known and not yet fixed.
 *
 * Shown on the shelf rather than buried in Settings because it describes
 * behaviour the reader will hit while using the shelf itself: EPUB reading
 * progress is recorded against the wrong position, so the per-book progress bar
 * and the 未读/在读/读完 classification can disagree with what was actually read.
 * Saying so plainly is better than letting it look like the app is guessing.
 *
 * Delete this composable and its call site once the position handling is fixed.
 */
@Composable
private fun KnownIssueBanner() {
    val c = LocalColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.button))
            .background(c.surfaceMuted)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "!",
            color = c.danger,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = Space.sm)
        )
        Column {
            Text(
                text = "EPUB 阅读进度识别有误",
                color = c.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.011).em
            )
            Text(
                text = "进度条和「未读 / 在读 / 读完」可能与你实际读到的地方不符，下个版本修复。",
                color = c.inkMuted,
                fontSize = 11.5.sp,
                letterSpacing = (-0.011).em,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
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
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = Space.xs)
                ) {
                    Text(
                        text = "${stats.todayMinutes}",
                        color = c.ink,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " 分钟",
                        color = c.inkMuted,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = Space.xs)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "累计 ${stats.totalMinutes} 分钟",
                    color = c.inkMuted,
                    style = MaterialTheme.typography.labelMedium
                )
                if (stats.streakDays > 0) {
                    Text(
                        text = "连续 ${stats.streakDays} 天",
                        color = c.inkFaint,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = Space.xs)
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        // Daily goal bar, styled like the sliders.
        Box(
            Modifier
                .fillMaxWidth()
                .height(Size.progressBarHeight)
                .clip(RoundedCornerShape(Size.progressBarHeight / 2))
                .background(c.surfaceMuted)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(Size.progressBarHeight)
                    .background(c.accent)
            )
        }

        Spacer(Modifier.height(Space.md))
        Text(
            text = stats.encouragement,
            color = c.inkMuted,
            fontSize = 12.5.sp,
            letterSpacing = (-0.011).em
        )
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
                letterSpacing = (-0.022).em,
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
                    letterSpacing = (-0.011).em,
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


private fun formatCharCount(count: Int): String = when {
    count >= 10_000 -> "${count / 10_000} 万字"
    else -> "$count 字"
}
