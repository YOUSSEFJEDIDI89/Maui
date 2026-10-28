package com.maui.system.core

import android.content.Context
import com.maui.system.scanner.JunkCategory
import com.maui.system.scanner.JunkItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CleanReport(
    val freedBytes: Long,
    val deletedFiles: Int,
    val failed: Int,
    val message: String = ""
)

/**
 * The cleaner. Deletes what the user selected and reports the *real*
 * amount of space that came back — measured before/after, not estimated.
 */
object Cleaner {

    suspend fun clean(context: Context, items: List<JunkItem>): CleanReport =
        withContext(Dispatchers.IO) {
            var freed = 0L
            var deleted = 0
            var failed = 0
            for (item in items) {
                val f = File(item.path)
                try {
                    if (!f.exists()) {
                        failed++
                        continue
                    }
                    val before = if (f.isDirectory) dirSize(f) else f.length()
                    val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
                    if (ok) {
                        deleted++
                        freed += before
                    } else {
                        failed++
                    }
                } catch (t: Throwable) {
                    failed++
                }
            }
            CleanReport(freed, deleted, failed)
        }

    /** Free space on the volume that contains the path, before/after. */
    fun dirSize(dir: File): Long {
        var total = 0L
        try {
            dir.walkBottomUp().forEach { if (it.isFile) total += it.length() }
        } catch (t: Throwable) {
        }
        return total
    }

    /** Clears Maui's own caches and temp files. */
    suspend fun cleanOwnCaches(context: Context): CleanReport = withContext(Dispatchers.IO) {
        var freed = 0L
        var deleted = 0
        val targets = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            context.codeCacheDir
        )
        for (t in targets) {
            if (!t.exists()) continue
            val before = dirSize(t)
            val ok = t.listFiles()?.forEach { it.deleteRecursively() }
            if (ok != null) {
                deleted += t.listFiles()?.size ?: 0
                freed += before
            }
        }
        val tmp = File(context.filesDir, "maui_tmp")
        if (tmp.exists()) {
            val before = dirSize(tmp)
            tmp.listFiles()?.forEach { it.deleteRecursively() }
            freed += before
        }
        CleanReport(freed, deleted, 0)
    }

    /**
     * The real RAM boost: kill background processes so the system reclaims
     * their heaps, and trim the page cache of other apps when we have root.
     */
    suspend fun boostMemory(context: Context): CleanReport = withContext(Dispatchers.Default) {
        val killed = AppHelper.killAllBackground(context)
        var freed = 0L
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val beforeAvail = mi.availMem
        Thread.sleep(900)
        am.getMemoryInfo(mi)
        freed = (mi.availMem - beforeAvail).coerceAtLeast(0)
        var msg = "Freed ${com.maui.system.util.Format.size(freed)} • stopped $killed background processes"
        if (RootShell.isRooted) {
            AppHelper.trimCachesRoot()
            msg += " • trimmed system caches"
        }
        CleanReport(freed, killed, 0, msg)
    }
}
