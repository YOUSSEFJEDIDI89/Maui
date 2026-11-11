package com.maui.system.core

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Finds the games on the device.
 *
 * Two sources, both real:
 *  1. A signature list of published game packages (Free Fire, PUBG, Genshin,
 *     Roblox, Mobile Legends, COD, …).
 *  2. Games the user added by hand — any installed app can be added from the
 *     launcher, which is how unknown/region-locked titles are handled.
 */
object GameCatalog {

    /** Packages of the games we can recognise by name. */
    private val knownGamePrefixes = listOf(
        // Garena / Free Fire
        "com.dts.freefireth", "com.dts.freefiremax", "com.garena.game.kfire",
        "com.garena.game.cod", "com.garena.game.tdtm", "com.garena.game.kgvn",
        // Tencent / PUBG
        "com.tencent.ig", "com.tencent.tmgp.pubgmhd", "com.pubg.krmobile",
        "com.rekoo.pubgm", "com.tencent.tmgp.sgame",
        // Moonton / Mobile Legends
        "com.moonton.mobilelegends", "com.moonton.mobilelegends.survivor",
        // Riot / other
        "com.riotgames.league.wildrift", "com.riotgames.league.teamfighttactics",
        "com.supercell.clashofclans", "com.supercell.clashroyale", "com.supercell.brawlstars",
        "com.nintendo.zaka", "com.nintendo.zkap", "com.roblox.client",
        "com.mojang.minecraftpe", "com.ea.gp.fifamobile", "com.gameloft.anmp",
        "com.ea.gp.apexlegends", "com.gamedevltd.modernstrike",
        "com.gamedevltd.targetstrike", "com.axlebolt.standoff2",
        "com.madfingergames.legends", "com.gameloft.android.ANMP.GloftA9HM",
        "com.miHoYo.GenshinImpact", "com.miHoYo.Nap", "com.hoyoverse.Nap",
        "com.levelinfinite.hotta.gb", "com.levelinfinite.sgameGlobal",
        "com.gamart.higan", "com.netease.g104na", "com.netease.idv",
        "com.tencent.tmgp.sgamehg", "com.sdo.sg", "com.luna.game",
        "com.vng.pubgmobile", "com.vng.gplay", "com.gametaiwan.jk",
        "com.hkmanjuu.sgzhr", "com.ss.android.ugc.aw",
        "com.mojang.minecraftpe", "com.ustwo.monumentvalley2",
        "com.dxco.pandavszombies", "com.criticalforceentertainment.criticalops",
        "com.huawei.gamedata", "com.bandainamcoent.wwise.gp",
        "com.ea.gp.fifamobile", "com.gamedevltd.bfv",
        "com.gt.lava", "com.proximabeta", "com.gameloft.speeddrill",
        "com.netease.party", "com.gamedevltd.mf.u", "com.ss.android.car"
    )

    /** Free Fire and its variants get their own tuned profile. */
    val freeFirePackages = setOf(
        "com.dts.freefireth", "com.dts.freefiremax", "com.garena.game.kfire",
        "com.garena.game.cod", "com.vng.freefiremax", "com.dts.freefirehd"
    )

    /** Extra packages that are definitely games even if the prefix list misses them. */
    private val extraGameHints = listOf("freefire", "pubg", "genshin", "mobilelegends", "clashofclans", "roblox")

    fun isFreeFire(pkg: String): Boolean =
        pkg.lowercase() in freeFirePackages || pkg.lowercase().contains("freefire")

    fun isKnownTitle(pkg: String): Boolean {
        val p = pkg.lowercase()
        if (knownGamePrefixes.any { p.startsWith(it.lowercase()) }) return true
        return extraGameHints.any { p.contains(it) }
    }

    /** All installed apps that look like games, plus the user's own additions. */
    fun detect(context: Context, userPackages: Set<String> = emptySet()): List<GameEntry> {
        val pm = context.packageManager
        val user = userPackages.map { it.lowercase() }.toSet()
        val out = mutableListOf<GameEntry>()
        for (app in AppHelper.userApps(context)) {
            val pkg = app.packageName
            val known = isKnownTitle(pkg)
            val added = pkg.lowercase() in user
            if (!known && !added) continue
            val label = AppHelper.labelOf(context, pkg)
            out += GameEntry(
                packageName = pkg,
                label = label,
                knownTitle = known,
                freeFire = isFreeFire(pkg),
                profile = GameProfile.PERFORMANCE
            )
        }
        return out.sortedWith(compareByDescending<GameEntry> { it.freeFire }.thenBy { it.label.lowercase() })
    }

    /** Suggest games that are not yet added to the launcher. */
    fun suggestions(context: Context, known: Set<String>): List<GameEntry> {
        val pm = context.packageManager
        val out = mutableListOf<GameEntry>()
        for (app in AppHelper.userApps(context)) {
            val pkg = app.packageName
            if (pkg in known) continue
            if (!isKnownTitle(pkg)) continue
            out += GameEntry(
                packageName = pkg,
                label = AppHelper.labelOf(context, pkg),
                knownTitle = true,
                freeFire = isFreeFire(pkg),
                profile = GameProfile.PERFORMANCE
            )
        }
        return out
    }
}
