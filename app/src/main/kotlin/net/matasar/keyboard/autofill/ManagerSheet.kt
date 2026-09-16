package net.matasar.keyboard.autofill

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** What the key button's sheet can do; the service implements it with real intents. */
interface AutofillActions {
    /** The user-facing name of the preferred autofill service, or null if none or unreadable. */
    fun currentManagerLabel(): String?
    fun openManager()
    fun changeManager()
}

/**
 * Android lets the user pick one preferred autofill service (and, from Android 14, extra
 * credential providers) in system settings; a keyboard can only send them there.
 */
fun settingsActionFor(sdkInt: Int): String =
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ACTION_CREDENTIAL_PROVIDER
    else Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE

/** `Settings.ACTION_CREDENTIAL_PROVIDER` by value, so the constant compiles below API 34. */
const val ACTION_CREDENTIAL_PROVIDER = "android.settings.CREDENTIAL_PROVIDER"

/** The intent that opens the provider chooser for [packageName]'s request. */
fun changeManagerIntent(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent =
    Intent(settingsActionFor(sdkInt))
        .setData(Uri.parse("package:$packageName"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class AndroidAutofillActions(private val context: Context) : AutofillActions {

    private fun currentComponent(): ComponentName? =
        runCatching { Settings.Secure.getString(context.contentResolver, AUTOFILL_SERVICE_SETTING) }
            .getOrNull()
            ?.let { ComponentName.unflattenFromString(it) }

    /** The service's own label ("Google", "Enpass"), falling back to the app's. */
    override fun currentManagerLabel(): String? {
        val component = currentComponent() ?: return null
        val pm = context.packageManager
        return runCatching { pm.getServiceInfo(component, 0).loadLabel(pm).toString() }.getOrNull()
            ?: runCatching { pm.getApplicationInfo(component.packageName, 0).loadLabel(pm).toString() }.getOrNull()
    }

    override fun openManager() {
        val component = currentComponent() ?: return
        val launch = context.packageManager.getLaunchIntentForPackage(component.packageName) ?: return
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun changeManager() {
        runCatching { context.startActivity(changeManagerIntent(context.packageName)) }
    }

    private companion object {
        /** `Settings.Secure.AUTOFILL_SERVICE`, which is not in the public API. */
        const val AUTOFILL_SERVICE_SETTING = "autofill_service"
    }
}
