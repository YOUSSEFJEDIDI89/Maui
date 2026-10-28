package com.maui.system.scanner

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.maui.system.core.AppHelper
import com.maui.system.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The real scanner.
 *
 * It walks the storage the user actually granted us and reports genuine
 * reclaimable bytes: app caches, thumbnails, temp/log leftovers, empty files,
 * hidden leftovers, big files and byte-identical duplicates.
 */
class StorageScanner(private val context: Context) {

    companion object {
        const val BIG_FILE_THRESHOLD = 100L * 1024 * 1024
        const val MAX_DEPTH = 5
        const val MAX_FILES_PER_ROOT = 20_000
    }

    data class Roots(
        val appPrivate: List<File>,
        val shared: List<File>,
        val hasAllFilesAccess: Boolean
    )

    fun resolveRoots(): Roots {
        val appPrivate = mutableListOf<File>()
        context.cacheDir?.let { appPrivate.add(it) }
        context.externalCacheDir?.let { appPrivate.add(it) }
        context.filesDir?.let { appPrivate.add(File(it, "maui_tmp")) }

        val shared = mutableListOf<File>()
        val ext = Environment.getExternalStorageDirectory()
        val hasAll = hasAllFilesAccess()

        if (ext != null && hasAll) {
            shared.add(File(ext, "Android/data"))
            shared.add(File(ext, ".thumbnails"))
            shared.add(File(ext, "DCIM/.thumbnails"))
            shared.add(File(ext, "Pictures/.thumbnails"))
            shared.add(File(ext, "Download"))
            shared.add(File(ext, "Documents"))
            shared.add(File(ext, "tmp"))
            shared.add(File(ext, "log"))
            shared.add(File(ext, "cache"))
            shared.add(File(ext, ".cache"))
            shared.add(File(ext, "Movies"))
            shared.add(File(ext, "Music"))
            shared.add(File(ext, "Android/media"))
        }
        return Roots(appPrivate, shared, hasAll)
    }

    fun hasAllFilesAccess(): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    } catch (t: Throwable) {
        false
    }

    suspend fun scan(
        includeHidden: Boolean = true,
        includeDuplicates: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): ScanResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val items = mutableListOf<JunkItem>()
        val roots = resolveRoots()

        // 1) App private caches — always ours, always deletable.
        for (r in roots.appPrivate) {
            if (!r.exists()) continue
            onProgress(r.path)
            items += collectJunk(r, JunkCategory.APP_CACHE, depth = 4)
        }

        // 2) Shared storage targets.
        for (r in roots.shared) {
            if (!r.exists() || !r.canRead()) continue
            onProgress(r.path)
            when {
                r.name.contains("thumbnail", true) ->
                    items += collectJunk(r, JunkCategory.THUMBNAILS, depth = 3)
                r.name == "Download" -> {
                    items += collectJunk(r, JunkCategory.TEMP_FILES, depth = 2)
                    items += collectInstallers(r)
                }
                else -> items += collectJunk(r, JunkCategory.BROWSER_CACHE, depth = 3)
            }
        }

        // 3) Hidden leftovers across the shared tree.
        if (includeHidden && roots.hasAllFilesAccess) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null) {
                onProgress("hidden scan")
                items += findHidden(ext, depth = 4)
            }
        }

        // 4) Big files in the shared tree.
        if (roots.hasAllFilesAccess) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null) {
                onProgress("big files")
                items += findBigFiles(ext, depth = 4)
            }
        }

        // 5) Duplicates (byte-identical) inside shared storage.
        if (includeDuplicates && roots.hasAllFilesAccess) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext != null) {
                onProgress("duplicates")
                try {
                    items += findDuplicates(ext)
                } catch (t: Throwable) {
                    errors.add("duplicate scan: ${t.message}")
                }
            }
        }

        // 6) Root-assisted pass: adb temp, stuck .apk leftovers, app caches.
        if (RootShell.isRooted) {
            onProgress("root pass")
            items += rootScan(errors)
        }

        val merged = merge(items)
        ScanResult(
            items = merged,
            scannedRoots = (roots.appPrivate + roots.shared).map { it.path },
            totalBytes = merged.filter { it.deletable }.sumOf { it.sizeBytes },
            durationMs = System.currentTimeMillis() - start,
            errors = errors
        )
    }

    /** Walks a tree and files away files matching known junk patterns. */
    private fun collectJunk(root: File, category: JunkCategory, depth: Int): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        walk(root, depth, 0) { file ->
            when {
                file.isDirectory && isEmptyDir(file) ->
                    out += JunkItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = 0,
                        category = JunkCategory.EMPTY,
                        isDirectory = true,
                        lastModified = file.lastModified()
                    )
                file.isFile -> {
                    val cat = classify(file)
                    if (cat != null) {
                        out += JunkItem(
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            category = cat,
                            lastModified = file.lastModified(),
                            hidden = file.name.startsWith(".")
                        )
                    }
                }
            }
            true
        }
        return out
    }

    private fun classify(file: File): JunkCategory? {
        val n = file.name.lowercase()
        val ext = file.extension.lowercase()
        return when {
            n.endsWith(".log") || n.endsWith(".logs") -> JunkCategory.LOGS
            n.endsWith(".tmp") || n.endsWith(".temp") || n.endsWith(".part") ||
                n.endsWith(".crdownload") || n.endsWith(".download") ||
                n.endsWith(".partial") -> JunkCategory.TEMP_FILES
            n.endsWith(".bak") || n.endsWith(".old") || n.endsWith(".orig") -> JunkCategory.TEMP_FILES
            n == ".ds_store" || n == "thumbs.db" || n == "desktop.ini" -> JunkCategory.TEMP_FILES
            n.contains("cache") && (n.endsWith(".tmp") || n.endsWith(".bin")) -> JunkCategory.APP_CACHE
            else -> null
        }
    }

    private fun collectInstallers(dir: File): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        val kids = dir.listFiles() ?: return out
        for (f in kids) {
            if (f.isFile && (f.extension.lowercase() == "apk" || f.extension.lowercase() == "apks")) {
                out += JunkItem(
                    path = f.absolutePath,
                    name = f.name,
                    sizeBytes = f.length(),
                    category = JunkCategory.INSTALLERS,
                    lastModified = f.lastModified()
                )
            }
        }
        return out
    }

    private fun findHidden(root: File, depth: Int): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        walk(root, depth, 0) { file ->
            if (file.name.startsWith(".") && file.name != "." && file.name != "..") {
                val size = if (file.isDirectory) dirSize(file, 3) else file.length()
                out += JunkItem(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = size,
                    category = JunkCategory.HIDDEN,
                    isDirectory = file.isDirectory,
                    lastModified = file.lastModified(),
                    hidden = true
                )
                false // don't descend into a hidden folder twice
            } else {
                true
            }
        }
        return out
    }

    private fun findBigFiles(root: File, depth: Int): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        walk(root, depth, 0) { file ->
            if (file.isFile && file.length() >= BIG_FILE_THRESHOLD) {
                out += JunkItem(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = file.length(),
                    category = JunkCategory.BIG_FILES,
                    lastModified = file.lastModified()
                )
            }
            true
        }
        return out.sortedByDescending { it.sizeBytes }.take(200)
    }

    /**
     * Byte-identical duplicates: group by size, then confirm with a hash.
     * The first file of each group is kept, the rest are flagged.
     */
    private fun findDuplicates(root: File): List<JunkItem> {
        val candidates = mutableListOf<File>()
        walk(root, 4, 0) { file ->
            if (file.isFile && file.length() > 0 && file.length() < 512L * 1024 * 1024) {
                candidates += file
            }
            candidates.size < MAX_FILES_PER_ROOT
        }
        val bySize = candidates.groupBy { it.length() }.filterValues { it.size > 1 }
        val out = mutableListOf<JunkItem>()
        for ((_, group) in bySize) {
            val byHash = group.groupBy { hash(it) }
            for ((h, same) in byHash) {
                if (h.isEmpty() || same.size < 2) continue
                val sorted = same.sortedBy { it.lastModified() }
                val keeper = sorted.first()
                for (dup in sorted.drop(1)) {
                    out += JunkItem(
                        path = dup.absolutePath,
                        name = dup.name,
                        sizeBytes = dup.length(),
                        category = JunkCategory.DUPLICATES,
                        lastModified = dup.lastModified(),
                        duplicateOf = keeper.absolutePath
                    )
                }
            }
        }
        return out
    }

    /** Root pass — real caches of other apps, adb temp files, stuck installs. */
    private fun rootScan(errors: MutableList<String>): List<JunkItem> {
        val out = mutableListOf<JunkItem>()
        try {
            val res = RootShell.runRoot(
                "du -sk /data/data/*/cache /data/user/0/*/cache /data/local/tmp 2>/dev/null | head -400",
                timeoutMs = 25_000
            )
            if (res.first == 0) {
                for (line in res.second.lines()) {
                    val p = line.trim().split(Regex("\\s+"))
                    if (p.size < 2) continue
                    val kb = p[0].toLongOrNull() ?: continue
                    val path = p[1]
                    if (!path.contains("cache", true)) continue
                    out += JunkItem(
                        path = path,
                        name = path.substringAfterLast('/'),
                        sizeBytes = kb * 1024,
                        category = JunkCategory.APP_CACHE,
                        isDirectory = true
                    )
                }
            }
        } catch (t: Throwable) {
            errors.add("root cache scan failed: ${t.message}")
        }
        return out
    }

    private fun hash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        ""
    }

    private fun isEmptyDir(f: File): Boolean {
        if (!f.isDirectory) return false
        val kids = f.listFiles() ?: return true
        return kids.isEmpty()
    }

    fun dirSize(dir: File, maxDepth: Int): Long {
        var total = 0L
        walk(dir, maxDepth, 0) { file ->
            if (file.isFile) total += file.length()
            true
        }
        return total
    }

    /** Depth-limited, symlink-safe directory walk. */
    private inline fun walk(root: File, maxDepth: Int, level: Int, action: (File) -> Boolean) {
        if (level > maxDepth) return
        if (!root.exists()) return
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to level)
        var count = 0
        while (stack.isNotEmpty()) {
            val (dir, depth) = stack.removeLast()
            val kids = try { dir.listFiles() ?: continue } catch (t: Throwable) { continue }
            for (f in kids) {
                if (count++ > MAX_FILES_PER_ROOT) return
                if (!action(f)) continue
                if (f.isDirectory && !f.isHidden) {
                    if (depth < maxDepth) stack.addLast(f to depth + 1)
                }
            }
        }
    }

    private fun merge(items: List<JunkItem>): List<JunkItem> {
        val map = LinkedHashMap<String, JunkItem>()
        for (i in items) {
            val prev = map[i.path]
            if (prev == null) {
                map[i.path] = i
            } else if (prev.category == JunkCategory.APP_CACHE && i.category != JunkCategory.APP_CACHE) {
                map[i.path] = i
            } else if (i.sizeBytes > prev.sizeBytes) {
                map[i.path] = i.copy(sizeBytes = i.sizeBytes)
            }
        }
        return map.values.sortedByDescending { it.sizeBytes }
    }

    fun freeSpace(): Long = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (t: Throwable) {
        0
    }
}
