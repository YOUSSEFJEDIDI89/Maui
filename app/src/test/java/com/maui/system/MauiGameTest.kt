package com.maui.system

import androidx.test.core.app.ApplicationProvider
import com.maui.system.core.GameCatalog
import com.maui.system.core.GameOptimizer
import com.maui.system.core.GameProfile
import com.maui.system.core.SurfaceFlingerProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real tests for the game-space engine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MauiGameTest {

    @Test
    fun free_fire_packages_are_recognised() {
        assertTrue(GameCatalog.isFreeFire("com.dts.freefireth"))
        assertTrue(GameCatalog.isFreeFire("com.dts.freefiremax"))
        assertTrue(GameCatalog.isFreeFire("com.garena.game.kfire"))
        assertFalse(GameCatalog.isFreeFire("com.example.notes"))
    }

    @Test
    fun known_titles_are_recognised() {
        assertTrue(GameCatalog.isKnownTitle("com.tencent.tmgp.pubgmhd"))
        assertTrue(GameCatalog.isKnownTitle("com.miHoYo.GenshinImpact"))
        assertTrue(GameCatalog.isKnownTitle("com.roblox.client"))
        assertFalse(GameCatalog.isKnownTitle("com.whatsapp"))
        assertFalse(GameCatalog.isKnownTitle("com.android.settings"))
    }

    @Test
    fun profiles_map_to_real_behaviour() {
        assertEquals(GameProfile.PERFORMANCE, GameProfile.fromKey("performance"))
        assertEquals(GameProfile.BALANCED, GameProfile.fromKey("balanced"))
        // Unknown keys fall back to the strongest profile, which is the safe default.
        assertEquals(GameProfile.PERFORMANCE, GameProfile.fromKey("unknown"))
        assertTrue(GameProfile.PERFORMANCE.boostsRefresh)
        assertFalse(GameProfile.SAVER.boostsRefresh)
        assertTrue(GameProfile.PERFORMANCE.killsAnimations)
        assertFalse(GameProfile.SAVER.killsAnimations)
    }

    @Test
    fun animation_scale_is_actually_set_to_zero() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val step = GameOptimizer.setAnimationsOff(ctx)
        assertTrue("animations step must succeed: ${step.detail}", step.applied)
        val value = android.provider.Settings.System.getFloat(
            ctx.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        assertEquals(0f, value, 0.001f)
    }

    @Test
    fun optimiser_reports_honestly_when_a_step_is_unavailable() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val report = GameOptimizer.optimize(ctx, "com.dts.freefireth", GameProfile.PERFORMANCE)
        assertTrue(report.steps.isNotEmpty())
        // Every step must carry a human readable detail — no silent failures.
        for (step in report.steps) {
            assertTrue("step ${step.title} has no detail", step.detail.isNotBlank())
        }
        // The memory step always runs and always explains itself. It only
        // counts as applied when there really was memory to reclaim.
        val memory = report.steps.firstOrNull { it.title == "Memory released" }
        assertTrue("the memory step must be reported", memory != null)
        assertTrue("the memory step must explain itself", !memory!!.detail.isBlank())
        assertTrue(
            "an applied memory step must quote the freed amount",
            !memory.applied || memory.detail.contains("freed")
        )
    }

    @Test
    fun surfaceflinger_timeline_is_parsed_into_fps() {
        val probe = SurfaceFlingerProbe
        // 60 frames, 16.6 ms apart -> ~60 fps
        val stamps = (0 until 60).map { it * 16_666_666L }
        val fps = probe.fpsFromTimestamps(stamps)
        assertTrue("expected ~60 fps, got $fps", fps in 58..61)

        // A single second at 30 fps
        val stamps30 = (0 until 30).map { it * 33_333_333L }
        val fps30 = probe.fpsFromTimestamps(stamps30)
        assertTrue("expected ~30 fps, got $fps30", fps30 in 28..31)

        // Too few samples -> no reading, never a fake number
        assertEquals(0, probe.fpsFromTimestamps(listOf(1L, 2L)))
        assertEquals(0, probe.fpsFromTimestamps(emptyList()))
    }

    @Test
    fun latency_dump_text_is_parsed() {
        val dump = """
            16666666
            1000000000 1000000000 1000000000
            1016666666 1016666666 1016666666
            1033333332 1033333332 1033333332
        """.trimIndent()
        val stamps = SurfaceFlingerProbe.parseTimestamps(dump)
        assertEquals(3, stamps.size)
        assertTrue(stamps[1] > stamps[0])
    }
}
