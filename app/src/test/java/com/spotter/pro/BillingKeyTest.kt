package com.spotter.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one billing rule that cannot be checked by running the app.
 *
 * A Test Store key in a release build does not degrade — the RevenueCat SDK crashes on launch, and
 * their own docs say such apps are rejected in review. Finding that out by shipping is the entire
 * scenario these tests exist to prevent, and it is unreproducible on a desk, which is why the
 * decision is a pure function.
 */
class BillingKeyTest {

    @Test
    fun `a test key works in debug and is refused in release`() {
        assertTrue("development would be impossible otherwise",
            BillingKey.isUsable("test_abc123", isDebugBuild = true))
        assertFalse("this is the crash-on-launch case",
            BillingKey.isUsable("test_abc123", isDebugBuild = false))
    }

    @Test
    fun `a play key is usable in both`() {
        assertEquals(KeyKind.PLAY, BillingKey.kind("goog_abc123"))
        assertTrue(BillingKey.isUsable("goog_abc123", isDebugBuild = false))
        assertTrue(BillingKey.isUsable("goog_abc123", isDebugBuild = true))
    }

    @Test
    fun `no key means billing is off, not broken`() {
        // Anyone can clone this repo without credentials. The app must still run.
        assertEquals(KeyKind.MISSING, BillingKey.kind(""))
        assertFalse(BillingKey.isUsable("", isDebugBuild = true))
        assertFalse(BillingKey.isUsable("", isDebugBuild = false))
    }

    @Test
    fun `a galaxy key is refused rather than quietly accepted`() {
        // The Galaxy seller application was rejected — an individual cannot sell paid content
        // there — so a galx_ key can never be valid here. Accepting it would let a
        // misconfiguration look like it had worked.
        assertEquals(KeyKind.OTHER, BillingKey.kind("galx_abc123"))
        assertFalse(BillingKey.isUsable("galx_abc123", isDebugBuild = false))
    }

    @Test
    fun `an unrecognised key is refused`() {
        assertEquals(KeyKind.OTHER, BillingKey.kind("amzn_abc123"))
        assertFalse(BillingKey.isUsable("amzn_abc123", isDebugBuild = true))
    }

    @Test
    fun `a test key in release says why loudly`() {
        val warning = BillingKey.warning("test_abc", isDebugBuild = false)
        assertNotNull(warning)
        assertTrue("the warning should name the consequence", warning!!.contains("crash"))
    }

    @Test
    fun `a debug test key warns that it earns nothing`() {
        assertNotNull(BillingKey.warning("test_abc", isDebugBuild = true))
    }

    @Test
    fun `a correct setup says nothing`() {
        // Warnings that fire on the happy path are warnings people stop reading.
        assertNull(BillingKey.warning("goog_abc", isDebugBuild = false))
    }
}
