package com.ezymusy.app.core.bluetooth

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsTargetTest {
    private val buds = BtDevice("Galaxy Buds2", "AA:BB:CC:DD:EE:FF")

    @Test
    fun `matches by address even when the name differs`() {
        assertTrue(isTarget(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Renamed", "aa:bb:cc:dd:ee:ff", buds))
    }

    @Test
    fun `falls back to the name when the address is hidden`() {
        assertTrue(isTarget(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2", "", buds))
    }

    @Test
    fun `call-only audio and other devices don't count`() {
        assertFalse(isTarget(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Galaxy Buds2", buds.address, buds))
        assertFalse(isTarget(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Car", "11:22:33:44:55:66", buds))
    }

    @Test
    fun `same model name on another device is not a match`() {
        assertFalse(isTarget(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Galaxy Buds2", "11:22:33:44:55:66", buds))
    }
}
