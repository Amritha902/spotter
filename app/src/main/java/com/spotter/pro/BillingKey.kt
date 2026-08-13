package com.spotter.pro

/** Which store a RevenueCat public key belongs to, read from its prefix. */
enum class KeyKind { MISSING, TEST, PLAY, OTHER }

/**
 * Decides whether a RevenueCat key may be used.
 *
 * **This exists because the SDK crashes rather than degrades.** RevenueCat's own validator carries
 * the warning verbatim: *"The Test Store is for development only. Never use a Test Store API key in
 * production. Our SDK will crash if using it in production... Apps submitted with a Test Store API
 * key will be rejected during App Review."*
 *
 * A Test Store key is exactly what sits in `local.properties` during development, which makes
 * shipping a release build with one in it the single most likely billing mistake in this project.
 * The failure is not a paywall that does not work; it is the app crashing on launch for every user,
 * and a store rejection before that.
 *
 * So the rule is enforced here rather than trusted to a checklist: a test key is accepted in debug
 * builds and refused in release ones. Refused means **billing is simply not configured** — every
 * coaching feature still runs, and the lifter is told they are not subscribed. Locking a feature is
 * a bad outcome; crashing on a gym floor mid-set is a much worse one.
 *
 * Pure and Android-free so the decision is unit-testable, like the pose geometry. The thing being
 * guarded cannot be reproduced on a desk, which is precisely why the rule deciding it should be.
 */
object BillingKey {

    fun kind(key: String): KeyKind = when {
        key.isBlank() -> KeyKind.MISSING
        key.startsWith(TEST_PREFIX) -> KeyKind.TEST
        key.startsWith(PLAY_PREFIX) -> KeyKind.PLAY
        else -> KeyKind.OTHER
    }

    /**
     * Whether to configure billing at all.
     *
     * Only Play is accepted, which is a narrowing rather than an oversight. The Galaxy Store seller
     * application was rejected on 2026-08-12 — an individual cannot sell paid content there — so a
     * `galx_` key can never be valid for this project, and quietly accepting one would let a
     * misconfiguration look like it worked.
     */
    fun isUsable(key: String, isDebugBuild: Boolean): Boolean = when (kind(key)) {
        KeyKind.PLAY -> true
        KeyKind.TEST -> isDebugBuild
        KeyKind.MISSING, KeyKind.OTHER -> false
    }

    /** Non-null when there is something the developer needs to be told at configure time. */
    fun warning(key: String, isDebugBuild: Boolean): String? = when {
        kind(key) == KeyKind.TEST && isDebugBuild ->
            "Test Store key: purchases are simulated and earn nothing. A release build with this " +
                "key would crash on launch, so billing is disabled there instead."
        kind(key) == KeyKind.TEST ->
            "Refusing a Test Store key in a release build — the SDK would crash. Billing is off."
        kind(key) == KeyKind.OTHER ->
            "Unrecognised RevenueCat key. Play keys start with '$PLAY_PREFIX'. Billing is off."
        else -> null
    }

    private const val TEST_PREFIX = "test_"
    private const val PLAY_PREFIX = "goog_"
}
