package com.spotter.core.fold

/**
 * What the phone is physically doing, as far as this app cares.
 *
 * Only three states, and the middle one is the product. Everything else is a fallback.
 */
enum class Posture {
    /** Flat, folded, or in a hand. Works, but the user is holding it. */
    HELD,

    /**
     * Half-open and standing on a surface — Samsung calls this Flex Mode.
     *
     * This is the one the app is built around. A spoken rehearsal needs the user's hands free and
     * the phone upright and pointed at their face, and a foldable half-open on a desk is the only
     * phone posture that does that without a stand propping it up. The top half faces them like a
     * person across a table; the bottom half lies flat under their hands like a notepad.
     */
    FLEX,

    /** Fully open and flat on a table. Big canvas, but nothing is facing the user. */
    FLAT,
}

/**
 * Deciding posture from sensor and hinge readings.
 *
 * Pure and Android-free so it can be tested at a desk, because the thing it describes cannot be:
 * reproducing "half-open at 100° resting on a table" in a unit test is impossible, and reproducing
 * it on an emulator is fiddly enough that it will not be done often. The arithmetic that decides
 * it should therefore be the part that is certain.
 */
object Postures {

    /**
     * Half-open covers a wide arc.
     *
     * People do not set a phone down at a tidy 90°. Watching how a Fold actually sits on a desk,
     * anything from about 70° to about 120° is stable and readable; below that it is closing and
     * above it is falling flat. Too narrow a band means the app drops out of its main mode because
     * someone nudged the table.
     */
    const val FLEX_MIN_DEGREES = 70f
    const val FLEX_MAX_DEGREES = 120f

    /**
     * How level "lying on a table" is, in gravity's z component.
     *
     * Gravity is ~9.81 m/s² on the axis pointing at the earth. Flat on a table puts nearly all of
     * it on z; held up puts most of it elsewhere. 8.0 is comfortably clear of both without being
     * so strict that a slightly uneven desk reads as "held".
     */
    const val FLAT_GRAVITY_Z = 8.0f

    /**
     * @param hingeDegrees   angle between the halves, or null when the device has no hinge
     * @param gravityZ       gravity's z component, m/s²
     * @param hasFoldingFeature whether the window layout reports an actual fold
     */
    fun of(hingeDegrees: Float?, gravityZ: Float, hasFoldingFeature: Boolean): Posture {
        // No fold means no Flex Mode, whatever the sensors say. A flat phone lying on a desk is
        // not half-open, and offering it the two-pane rehearsal layout would put the counterpart
        // face-down on the table.
        if (!hasFoldingFeature || hingeDegrees == null) {
            return if (gravityZ >= FLAT_GRAVITY_Z) Posture.FLAT else Posture.HELD
        }

        return when {
            hingeDegrees in FLEX_MIN_DEGREES..FLEX_MAX_DEGREES -> Posture.FLEX
            hingeDegrees > FLEX_MAX_DEGREES && gravityZ >= FLAT_GRAVITY_Z -> Posture.FLAT
            else -> Posture.HELD
        }
    }
}
