package com.littlehelper.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun reminderVibrationPattern_hasThreePulses() {
        val pattern = NotificationHelper.REMINDER_VIBRATION_PATTERN
        assertEquals(6, pattern.size)
        assertEquals(0L, pattern[0])
    }

    @Test
    fun channelId_usesV5WithSoundChannel() {
        assertEquals("little_helper_reminders_v5", NotificationHelper.CHANNEL_ID)
    }
}
