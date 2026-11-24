package com.maui.system

import androidx.test.core.app.ApplicationProvider
import com.maui.system.core.FrameMetrics
import com.maui.system.core.GameCatalog
import com.maui.system.core.GameProfile
import com.maui.system.core.GameSessionStore
import com.maui.system.core.GameTools
import com.maui.system.core.VfpsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The tests of the VFPS engine.
 *
 * They check the maths and the honest reporting — not the marketing.
 */
@RunWith(RobolectricTestRunner::class)
class MauiVfpsTest {

    // ------------------------------------------------------------- metrics

    @Test
    fun a_steady_sixty_fps_stream_has_no_jank() {
        val frames = List(120) { 16.67 }
        val stats = FrameMetrics.stats(frames, 60)
        assertEquals(60, stats.fps)
        assertEquals(0f, stats.jankPercent, 0.01f)
        assertTrue(stats.smooth)
    }

    @Test
    fun a_stutter_shows_up_as_jank_and_a_low_one_percent() {
        val frames = MutableList(100) { 16.7 } + listOf(120.0, 140.0, 150.0)
        val stats = FrameMetrics.stats(frames, 60)
        // Three dropped frames out of 103 must show up in the jank share.
        assertTrue("the stutter must show as jank", stats.jankPercent > 2f)
        assertTrue("the 1% low must drop below the average", stats.low1Percent < stats.fps)
        assertTrue(stats.worstFrameMs > 100f)
    }

    @Test
    fun the_frame_budget_follows_the_target_rate() {
        assertEquals(16.67f, FrameMetrics.budgetOf(60), 0.01f)
        assertEquals(8.33f, FrameMetrics.budgetOf(120), 0.01f)
        assertEquals(0f, FrameMetrics.budgetOf(0), 0.001f)
    }

    @Test
    fun timestamps_become_frame_durations() {
        val stamps = listOf(0L, 16_666_666L, 33_333_332L, 50_000_000L)
        val durations = FrameMetrics.durationsMs(stamps)
        assertEquals(3, durations.size)
        assertEquals(16.67, durations[0], 0.01)
    }

    @Test
    fun broken_timestamps_are_dropped() {
        val stamps = listOf(0L, 16_666_666L, 16_666_666L, 33_333_332L)
        assertEquals(2, FrameMetrics.durationsMs(stamps).size)
    }

    @Test
    fun the_panel_ceiling_is_derived_from_the_intervals() {
        val frames = List(60) { 8.33 }   // a 120 Hz panel
        assertEquals(120, FrameMetrics.vfpsFromIntervals(frames))
    }

    @Test
    fun the_pacing_score_rewards_an_even_stream() {
        val even = FrameMetrics.stats(List(100) { 16.7 }, 60)
        val uneven = FrameMetrics.stats(List(50) { 8.0 } + List(50) { 25.0 }, 60)
        assertTrue(
            "an even stream must score higher",
            FrameMetrics.pacingScore(even) > FrameMetrics.pacingScore(uneven)
        )
    }

    @Test
    fun the_ceiling_usage_is_reported_against_the_panel() {
        assertEquals(50, FrameMetrics.ceilingUsage(60, 120))
        assertEquals(100, FrameMetrics.ceilingUsage(120, 120))
        assertEquals(0, FrameMetrics.ceilingUsage(0, 120))
    }

    @Test
    fun at_ceiling_is_true_only_near_the_top() {
        assertTrue(FrameMetrics.atCeiling(119, 120))
        assertTrue(FrameMetrics.atCeiling(120, 120))
        assertTrue(!FrameMetrics.atCeiling(90, 120))
    }

    @Test
    fun frames_are_classified_against_the_budget() {
        assertEquals(FrameMetrics.FrameState.FAST, FrameMetrics.classify(10.0, 16.67f))
        assertEquals(FrameMetrics.FrameState.ON_TIME, FrameMetrics.classify(16.0, 16.67f))
        assertEquals(FrameMetrics.FrameState.SLOW, FrameMetrics.classify(22.0, 16.67f))
        assertEquals(FrameMetrics.FrameState.JANK, FrameMetrics.classify(40.0, 16.67f))
    }

    @Test
    fun the_window_keeps_the_newest_samples() {
        val window = FrameMetrics.Window(10)
        repeat(50) { window.add(16.0) }
        assertEquals(10, window.size())
        assertEquals(10, window.snapshot().size)
    }

    // --------------------------------------------------------------- engine

    @Test
    fun the_engine_reports_the_panel_ceiling() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ceiling = VfpsEngine.panelCeiling(ctx)
        assertTrue("the test device must report a refresh rate", ceiling > 0)
        assertTrue("no phone refreshes faster than 480 Hz", ceiling <= 480)
    }

    @Test
    fun an_unmeasured_game_never_gets_a_number() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val snapshot = VfpsEngine.measure(ctx, "com.example.not.running")
        assertTrue("without a frame timeline the rate stays unknown", !snapshot.measured)
        assertEquals("--", snapshot.headline)
        assertTrue(snapshot.note.isNotEmpty())
    }

    // -------------------------------------------------------------- session

    @Test
    fun a_session_is_measured_and_stored() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = GameSessionStore.get(ctx)
        store.start("com.test.game", "Test Game", GameProfile.PERFORMANCE.key, 80)
        store.sample(58, 1.5f, 41f)
        store.sample(62, 0.5f, 43f)
        val finished = store.finish(70, 1024L)
        assertTrue(finished != null)
        assertEquals("Test Game", finished!!.gameName)
        assertEquals(10, finished.batteryUsed)
        assertEquals(43f, finished.peakTempC, 0.01f)
        assertTrue(finished.avgFps in 57..62)
        assertTrue(store.history().isNotEmpty())
    }

    // --------------------------------------------------------------- tools

    @Test
    fun free_fire_builds_are_recognised() {
        assertTrue(GameCatalog.isFreeFire("com.dts.freefireth"))
        assertTrue(GameCatalog.isFreeFire("com.dts.freefiremax"))
        assertTrue(GameCatalog.isFreeFire("com.garena.game.kfire"))
        assertTrue(!GameCatalog.isFreeFire("com.tencent.ig"))
    }

    @Test
    fun the_process_list_never_contains_maui_itself() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val list = GameTools.runningProcesses(ctx)
        assertTrue(list.none { it.packageName == ctx.packageName })
    }

    @Test
    fun the_thermal_level_has_a_readable_name() {
        assertTrue(GameTools.thermalName(0).isNotEmpty())
        assertTrue(GameTools.thermalName(5).isNotEmpty())
    }
}
