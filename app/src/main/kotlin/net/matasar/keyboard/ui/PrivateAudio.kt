package net.matasar.keyboard.ui

import android.annotation.SuppressLint
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether sound goes only to the user's own ears now: wired or Bluetooth headphones, a headset or
 * a hearing aid is connected. Follows devices being plugged in and taken out while it is shown.
 * TalkBack speaks a password's characters only then (see spokenKey).
 */
@Composable
fun rememberPrivateAudio(): Boolean {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    var inEars by remember(audio) { mutableStateOf(audio != null && hasPrivateOutput(audio)) }
    DisposableEffect(audio) {
        if (audio == null) return@DisposableEffect onDispose {}
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { inEars = hasPrivateOutput(audio) }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { inEars = hasPrivateOutput(audio) }
        }
        // A null handler delivers on this thread's looper: the main one, where composition runs.
        audio.registerAudioDeviceCallback(callback, null)
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return inEars
}

private fun hasPrivateOutput(audio: AudioManager): Boolean =
    audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isPrivateOutput(it.type) }

/** Output device types only the listener hears. Newer ones are plain ints, so they are safe to name below their API. */
@SuppressLint("InlinedApi")
internal fun isPrivateOutput(type: Int): Boolean = type in setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
)
