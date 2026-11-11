package com.maui.system.core

/**
 * Performance profiles for a game, the same way dedicated game-space apps work:
 * a profile is a named bundle of concrete system changes applied when the
 * game is launched, and every step reports whether it actually took effect.
 */
enum class GameProfile(
    val key: String,
    val title: String,
    val blurb: String
) {
    PERFORMANCE(
        "performance",
        "Performance",
        "Max refresh rate, zero animations, background apps killed, game kept out of doze"
    ),
    BALANCED(
        "balanced",
        "Balanced",
        "Zero animations and background cleanup, but the display keeps its natural refresh"
    ),
    SAVER(
        "saver",
        "Battery saver",
        "Only background cleanup — animations and refresh rate stay untouched"
    );

    /** True when this profile is allowed to raise the display refresh rate. */
    val boostsRefresh: Boolean get() = this == PERFORMANCE

    /** True when this profile is allowed to disable animations. */
    val killsAnimations: Boolean get() = this != SAVER

    companion object {
        fun fromKey(k: String?): GameProfile = entries.firstOrNull { it.key == k } ?: PERFORMANCE
    }
}

/** A game Maui knows how to boost. */
data class GameEntry(
    val packageName: String,
    val label: String,
    val knownTitle: Boolean,
    val freeFire: Boolean,
    val profile: GameProfile
)
