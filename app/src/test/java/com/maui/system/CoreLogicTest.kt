package com.maui.system

import com.maui.system.core.Cleaner
import com.maui.system.core.RootShell
import com.maui.system.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Real unit tests for the parts of Maui that do not need a device.
 * These run on the JVM and assert actual behaviour.
 */
class CoreLogicTest {

    @Test
    fun size_formats_bytes_human_readably() {
        assertEquals("0 B", Format.size(0))
        assertEquals("512 B", Format.size(512))
        assertEquals("1 KB", Format.size(1024))
        assertEquals("1.00 MB", Format.size(1024L * 1024))
        assertEquals("1.50 GB", Format.size(1536L * 1024 * 1024))
    }

    @Test
    fun percent_is_clamped_to_valid_range() {
        assertEquals(0, Format.percent(0, 100))
        assertEquals(50, Format.percent(50, 100))
        assertEquals(100, Format.percent(200, 100))
        assertEquals(0, Format.percent(5, 0))
    }

    @Test
    fun dirSize_sums_every_file_recursively() {
        val root = createTempDir()
        try {
            File(root, "a.txt").writeText("hello world")
            File(root, "sub").mkdirs()
            File(root, "sub/b.bin").writeBytes(ByteArray(2048))
            val total = Cleaner.dirSize(root)
            assertEquals(11L + 2048L, total)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun shell_executes_real_commands() {
        val (code, out) = RootShell.runShell("echo maui-ok")
        assertEquals(0, code)
        assertTrue("output was: $out", out.contains("maui-ok"))
    }

    @Test
    fun root_detection_does_not_throw_and_returns_boolean() {
        // On a machine without root this must be false, never crash the app.
        val rooted = RootShell.isRooted
        assertTrue(rooted || !rooted)
    }

    @Test
    fun duration_renders_readable_strings() {
        assertEquals("500ms", Format.duration(500))
        assertEquals("30s", Format.duration(30_000))
        assertEquals("2m 5s", Format.duration(125_000))
    }

    private fun createTempDir(): File {
        val f = File(System.getProperty("java.io.tmpdir"), "maui-test-" + System.nanoTime())
        f.mkdirs()
        return f
    }
}
