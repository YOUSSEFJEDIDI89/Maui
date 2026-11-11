package com.maui.system.core

/**
 * Frame-rate measurement taken straight from SurfaceFlinger's frame timeline.
 *
 * This is the only accurate way to read another app's FPS on Android: the
 * compositor keeps the real present timestamps for every layer, and
 * `dumpsys SurfaceFlinger --latency <layer>` prints them. It needs root,
 * because the dump is a shell command.
 *
 * When root is not available Maui does NOT invent a number — it reports that
 * the real frame rate cannot be sampled and shows the metrics it can read.
 */
data class FpsReading(
    val fps: Int,
    val layer: String,
    val source: String,
    val ok: Boolean,
    val note: String = ""
) {
    companion object {
        fun unavailable(note: String) = FpsReading(0, "", "", false, note)
    }
}

object SurfaceFlingerProbe {

    /** True when frame timing can be read at all. */
    val available: Boolean get() = RootShell.isRooted

    /** All layers the compositor currently knows about. */
    fun layers(): List<String> {
        if (!RootShell.isRooted) return emptyList()
        val res = RootShell.runRoot("dumpsys SurfaceFlinger --list", 12_000)
        if (res.first != 0) return emptyList()
        return res.second.lines()
            .map { it.trim() }
            .filter { it.contains('#') && it.length > 3 }
    }

    /**
     * Finds the layer that belongs to a package. Game surfaces show up as
     * "SurfaceView[pkg/activity]#0(BLAST#123)" or "pkg/activity#0".
     */
    fun layerFor(packageName: String): String? {
        val all = layers()
        if (all.isEmpty()) return null
        val exact = all.filter { it.contains(packageName) }
        if (exact.isEmpty()) return null
        // Prefer an active BLAST/surfaceview layer for the package.
        return exact.firstOrNull { it.startsWith("SurfaceView[") && it.contains(packageName) }
            ?: exact.firstOrNull { it.contains("BLAST") }
            ?: exact.first()
    }

    /**
     * Reads the frame timeline of a layer and converts it to frames per second.
     */
    fun fpsForLayer(layer: String): FpsReading {
        if (!RootShell.isRooted) return FpsReading.unavailable("root required to read the frame timeline")
        val res = RootShell.runRoot("dumpsys SurfaceFlinger --latency '$layer'", 12_000)
        if (res.first != 0) {
            return FpsReading.unavailable("SurfaceFlinger refused the latency dump")
        }
        val lines = res.second.lines()
        if (lines.isEmpty()) return FpsReading.unavailable("no frame data for this layer")

        // First line is the display refresh period in nanoseconds.
        val refreshNanos = lines[0].trim().toLongOrNull() ?: 0L

        val timestamps = mutableListOf<Long>()
        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            // v2 prints "desired actual ready"; v1 prints a single value.
            val t = if (parts.size >= 2) parts[1].toLongOrNull() else parts[0].toLongOrNull()
            if (t != null && t > 0) timestamps += t
        }
        if (timestamps.size < 3) {
            return FpsReading.unavailable("not enough frame samples yet")
        }

        // Use the most recent second of samples.
        val fps = fpsFromTimestamps(timestamps)

        val expected = if (refreshNanos > 0) (1_000_000_000L / refreshNanos).toInt() else 0
        return FpsReading(
            fps = fps.coerceIn(0, 240),
            layer = layer,
            source = "SurfaceFlinger",
            ok = fps > 0,
            note = if (expected > 0) "display refresh $expected Hz" else ""
        )
    }

    /**
     * Converts SurfaceFlinger present timestamps into frames per second.
     * Only the last second of samples is used, which is what a live counter
     * should show.
     */
    internal fun fpsFromTimestamps(timestamps: List<Long>): Int {
        if (timestamps.size < 3) return 0
        val last = timestamps.last()
        val window = timestamps.filter { last - it <= 1_000_000_000L }
        if (window.size < 2) return 0
        val span = window.last() - window.first()
        val frames = window.size - 1
        return if (span > 0) ((frames.toLong() * 1_000_000_000L) / span).toInt() else 0
    }

    /** Parses the raw text of a `--latency` dump into present timestamps. */
    internal fun parseTimestamps(dump: String): List<Long> {
        val lines = dump.lines()
        if (lines.isEmpty()) return emptyList()
        val out = mutableListOf<Long>()
        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            val t = if (parts.size >= 2) parts[1].toLongOrNull() else parts[0].toLongOrNull()
            if (t != null && t > 0) out += t
        }
        return out
    }

    /** Convenience: find the game's layer and measure it. */
    fun fpsForPackage(packageName: String): FpsReading {
        val layer = layerFor(packageName)
            ?: return FpsReading.unavailable("the game's surface is not on screen yet")
        return fpsForLayer(layer)
    }
}
