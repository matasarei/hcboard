package net.matasar.keyboard

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.matasar.keyboard.macro.Block
import net.matasar.keyboard.macro.Macro
import net.matasar.keyboard.macro.MacroStore
import net.matasar.keyboard.macro.MacrosActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Deleting a macro from the list asks first: Cancel keeps it, Delete removes it. */
@RunWith(AndroidJUnit4::class)
class MacrosScreenTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = MacroStore(context)
    private lateinit var before: List<Macro>

    @Before
    fun seed() = runBlocking {
        before = store.macros.first()
        assertTrue(store.save(listOf(Macro(id = "confirm-test", name = MACRO, blocks = listOf(Block.TypeText("hello"))))))
    }

    @After
    fun restore() {
        runBlocking { store.save(before) }
    }

    @Test
    fun deleteAsksFirst() {
        context.startActivity(
            Intent(context, MacrosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertNotNull("the macro is not listed", device.wait(Until.findObject(By.text(MACRO)), 5_000))

        device.findObject(By.desc("Delete")).click()
        assertNotNull("no confirmation", device.wait(Until.findObject(By.text("Delete $MACRO?")), 3_000))
        assertEquals("deleted before it was confirmed", 1, runBlocking { store.macros.first() }.size)
        device.findObject(By.text("Cancel")).click()
        assertTrue("the dialog stayed", device.wait(Until.gone(By.text("Delete $MACRO?")), 3_000))
        assertEquals("Cancel deleted it", 1, runBlocking { store.macros.first() }.size)

        device.findObject(By.desc("Delete")).click()
        assertNotNull("no confirmation", device.wait(Until.findObject(By.text("Delete $MACRO?")), 3_000))
        device.findObject(By.text("Delete")).click()
        assertTrue("the macro is still listed", device.wait(Until.gone(By.text(MACRO)), 3_000))
        assertTrue("the store still has it", runBlocking { store.macros.first() }.isEmpty())
    }

    private companion object {
        const val MACRO = "Confirm test"
    }
}
