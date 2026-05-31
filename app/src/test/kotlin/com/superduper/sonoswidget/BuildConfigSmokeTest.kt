package com.superduper.sonoswidget

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigSmokeTest {
    @Test
    fun exposesExpectedApplicationId() {
        assertEquals("com.superduper.sonoswidget", BuildConfig.APPLICATION_ID)
    }
}
