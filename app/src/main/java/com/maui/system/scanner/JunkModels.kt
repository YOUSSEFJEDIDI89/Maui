package com.maui.system.scanner

enum class JunkCategory(val key: String) {
    APP_CACHE("app_cache"),
    THUMBNAILS("thumbnails"),
    TEMP_FILES("temp"),
    LOGS("logs"),
    EMPTY("empty"),
    HIDDEN("hidden"),
    BIG_FILES("big"),
    DUPLICATES("duplicates"),
    INSTALLERS("installers"),
    ADB_TEMP("adb"),
    BROWSER_CACHE("browser");

    companion object {
        fun fromKey(k: String): JunkCategory = entries.firstOrNull { it.key == k } ?: APP_CACHE
    }
}

/**
 * One deletable thing found on disk.
 * [deletable] is false when the file is protected (e.g. system-owned) — we
 * still show it so the user knows it exists.
 */
data class JunkItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val category: JunkCategory,
    val isDirectory: Boolean = false,
    val lastModified: Long = 0L,
    val deletable: Boolean = true,
    val duplicateOf: String? = null,
    val hidden: Boolean = false
) {
    val key: String get() = "$path|${category.key}"
}

data class ScanResult(
    val items: List<JunkItem>,
    val scannedRoots: List<String>,
    val totalBytes: Long = 0L,
    val durationMs: Long = 0L,
    val errors: List<String> = emptyList()
) {
    val totalItems: Int get() = items.size

    fun byCategory(): Map<JunkCategory, List<JunkItem>> = items.groupBy { it.category }

    fun categorySize(c: JunkCategory): Long = items.filter { it.category == c }.sumOf { it.sizeBytes }

    val categories: List<JunkCategory> get() = JunkCategory.entries.filter { c -> items.any { it.category == c } }
}
