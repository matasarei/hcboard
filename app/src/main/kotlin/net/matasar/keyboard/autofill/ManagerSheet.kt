package net.matasar.keyboard.autofill

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

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
        .setData("package:$packageName".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

class AndroidAutofillActions(private val context: Context) : AutofillActions {

    private fun currentComponent(): ComponentName? {
        fun read(key: String) = runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()
        return pickManagerComponent(
            primaryCredentialProvider = read(CREDENTIAL_SERVICE_PRIMARY_SETTING),
            autofillService = read(AUTOFILL_SERVICE_SETTING),
            credentialProviders = read(CREDENTIAL_SERVICE_SETTING),
        )?.let { ComponentName.unflattenFromString(it) }
    }

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
        /** `Settings.Secure.AUTOFILL_SERVICE`, not in the public API. */
        const val AUTOFILL_SERVICE_SETTING = "autofill_service"

        /** Android 14+: the preferred Credential Manager provider, one flattened component. */
        const val CREDENTIAL_SERVICE_PRIMARY_SETTING = "credential_service_primary"

        /** Android 14+: every enabled credential provider, colon-separated. */
        const val CREDENTIAL_SERVICE_SETTING = "credential_service"
    }
}

/**
 * The manager Android will ask first. On Android 14+ the "preferred service" in Settings is a
 * Credential Manager provider and `autofill_service` may be empty, so that setting is read
 * first, then the classic autofill service, then the first enabled provider. Pure, for tests.
 */
fun pickManagerComponent(
    primaryCredentialProvider: String?,
    autofillService: String?,
    credentialProviders: String?,
): String? {
    val candidates = listOf(primaryCredentialProvider, autofillService) +
        credentialProviders.orEmpty().split(':')
    return candidates
        .filterNotNull()
        .map { it.trim() }
        .firstOrNull { it.contains('/') && !it.startsWith("PLACEHOLDER", ignoreCase = true) }
}
