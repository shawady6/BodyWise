package com.shawady.bodywise.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun versionComparison() {
        assertTrue(versionIsNewer("v0.3.0", "0.2.7"))
        assertTrue(versionIsNewer("v0.2.10", "0.2.9"))
        assertTrue(versionIsNewer("1.0", "0.9.9"))
        assertFalse(versionIsNewer("v0.2.5", "0.2.5"))
        assertFalse(versionIsNewer("v0.2.4", "0.2.5"))
    }
}
