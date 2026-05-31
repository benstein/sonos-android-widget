package com.superduper.sonoswidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SonosAppLauncherTest {
    @Test
    fun prefersS2SonosAppWhenAvailable() {
        val packageName = SonosAppLauncher.resolvePackage { candidate ->
            candidate == "com.sonos.acr" || candidate == "com.sonos.acr2"
        }

        assertEquals("com.sonos.acr2", packageName)
    }

    @Test
    fun fallsBackToS1SonosAppWhenS2IsUnavailable() {
        val packageName = SonosAppLauncher.resolvePackage { candidate ->
            candidate == "com.sonos.acr"
        }

        assertEquals("com.sonos.acr", packageName)
    }

    @Test
    fun returnsNullWhenNoSonosAppIsLaunchable() {
        val packageName = SonosAppLauncher.resolvePackage { false }

        assertNull(packageName)
    }
}
