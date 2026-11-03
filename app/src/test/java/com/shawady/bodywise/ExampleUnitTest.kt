package com.shawady.bodywise

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun appIdIsStable() {
        assertEquals("com.shawady.bodywise", BuildConfig.APPLICATION_ID)
    }
}
