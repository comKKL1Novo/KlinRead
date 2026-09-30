package com.klin.read.ui.shelf

import com.klin.read.data.BookEntity

/**
 * A shelf filter entry.
 *
 * The row always starts with 全部, then the built-in states, then whatever custom
 * categories the user has created. Every entry carries a live count so the chip
 * can show it without the screen re-deriving one per render.
 */
data class ShelfCategory(
    val key: String,
    val label: String,
    val count: Int
) {
    companion object {
        const val ALL = "__all__"
        const val UNREAD = "__unread__"
        const val READING = "__reading__"
        const val FINISHED = "__finished__"
        const val UNCATEGORIZED = "__uncategorized__"

        /**
         * Built-in filters, in display order. Custom categories follow these.
         *
         * [started] maps a book id to whether it has been opened at all, because
         * "未读" means never started — not merely unfinished. That fact lives in
         * ReaderPreferences, not in the book row, so the caller supplies it.
         */
        fun builtIn(
            books: List<BookEntity>,
            started: (BookEntity) -> Boolean
        ): List<ShelfCategory> = listOf(
            ShelfCategory(ALL, "全部", books.size),
            ShelfCategory(
                UNREAD,
                "未读",
                books.count { !it.isFinished && !started(it) }
            ),
            ShelfCategory(
                READING,
                "在读",
                books.count { !it.isFinished && started(it) }
            ),
            ShelfCategory(FINISHED, "读完", books.count { it.isFinished }),
            ShelfCategory(
                UNCATEGORIZED,
                "未分类",
                books.count { it.category.isNullOrBlank() }
            )
        )

        /** Custom categories present on the shelf, with counts. */
        fun custom(books: List<BookEntity>): List<ShelfCategory> =
            books.mapNotNull { it.category?.takeIf { name -> name.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .map { (name, count) -> ShelfCategory("cat:$name", name, count) }
                .sortedBy { it.label }
    }
}

/** Applies [category] to [books], preserving the shelf's own ordering. */
fun List<BookEntity>.filterBy(
    category: ShelfCategory,
    started: (BookEntity) -> Boolean
): List<BookEntity> = when (category.key) {
    ShelfCategory.ALL -> this
    ShelfCategory.FINISHED -> filter { it.isFinished }
    ShelfCategory.UNCATEGORIZED -> filter { it.category.isNullOrBlank() }
    ShelfCategory.UNREAD -> filter { !it.isFinished && !started(it) }
    ShelfCategory.READING -> filter { !it.isFinished && started(it) }
    else -> {
        val name = category.key.removePrefix("cat:")
        filter { it.category == name }
    }
}
