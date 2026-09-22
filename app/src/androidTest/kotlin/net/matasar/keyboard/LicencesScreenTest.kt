package net.matasar.keyboard

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.matasar.keyboard.settings.SettingsActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The licences the APK carries are one button away in settings, and say what they must. */
@RunWith(AndroidJUnit4::class)
class LicencesScreenTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun settingsOpensTheNoticeAndTheLicence() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startActivity(
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        // The button is the last thing on a long screen.
        var button = device.wait(Until.findObject(By.text("Open-source licences")), 3_000)
        repeat(12) {
            if (button != null) return@repeat
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
            device.waitForIdle()
            button = device.findObject(By.text("Open-source licences"))
        }
        assertNotNull("no Open-source licences button in settings", button)
        // A swipe flings on after the finger lifts: tap once the page has stopped moving.
        device.waitForIdle()
        SystemClock.sleep(500)
        device.findObject(By.text("Open-source licences"))!!.click()
        assertTrue(
            "the licences screen did not open",
            waitUntil(5_000) { device.executeShellCommand("dumpsys window").contains("LicencesActivity") },
        )
        // The NOTICE comes first: its third-party attributions, as the root file has them.
        assertTrue("the NOTICE is not shown", device.wait(Until.hasObject(By.textContains("FlorisBoard")), 5_000))
        assertTrue("the Helium314 word-list attribution is not shown", device.hasObject(By.textContains("Creative Commons Attribution 4.0")))
        assertTrue("the Apache License text is not shown", device.hasObject(By.textContains("Apache License")))
        // The generated list of what is inside the APK, and the one library that is not Apache-2.0.
        // Further down the page: only what is laid out is in the tree, so scroll to each.
        assertTrue("the libraries are not listed", scrollTo("androidx.* (Jetpack and Jetpack Compose)"))
        assertTrue("the BSD-3-Clause library is not named", scrollTo("datastore-preferences-external-protobuf"))
        assertTrue("the BSD-3-Clause text is not shown", scrollTo("Redistribution and use in source and binary forms"))
    }

    /** Swipes down the page until [text] shows, or gives up; the screen is several pages long. */
    private fun scrollTo(text: String): Boolean {
        repeat(30) {
            if (device.hasObject(By.textContains(text))) return true
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 15)
            device.waitForIdle()
        }
        return device.hasObject(By.textContains(text))
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
