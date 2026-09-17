package net.matasar.keyboard.autofill

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import org.xmlpull.v1.XmlPullParser

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

    private fun read(key: String) = runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()

    /** Every component that names the manager, the preferred one first. */
    private fun managerComponents(): List<ComponentName> = managerComponentCandidates(
        primaryCredentialProvider = read(CREDENTIAL_SERVICE_PRIMARY_SETTING),
        autofillService = read(AUTOFILL_SERVICE_SETTING),
        credentialProviders = read(CREDENTIAL_SERVICE_SETTING),
    ).mapNotNull { ComponentName.unflattenFromString(it) }

    private fun currentComponent(): ComponentName? = managerComponents().firstOrNull()

    /**
     * The components of the manager the sheet names, and no other: when the preferred one has
     * nothing to open, another component of the same app may, but "Open Enpass" must never open
     * a different app that happens to be next in the list.
     */
    private fun labelledManagerComponents(): List<ComponentName> {
        val all = managerComponents()
        val labelled = all.firstOrNull()?.packageName ?: return emptyList()
        return all.filter { it.packageName == labelled }
    }

    /** The service's own label ("Google", "Enpass"), falling back to the app's. */
    override fun currentManagerLabel(): String? {
        val component = currentComponent() ?: return null
        val pm = context.packageManager
        return runCatching { pm.getServiceInfo(component, 0).loadLabel(pm).toString() }.getOrNull()
            ?: runCatching { pm.getApplicationInfo(component.packageName, 0).loadLabel(pm).toString() }.getOrNull()
    }

    /**
     * Opens the manager so the user can copy a password and paste it back, which is also the only
     * way to fill a terminal: Android never offers autofill there. It never does nothing — a
     * manager with no screen of its own to open lands the user on Android's password settings.
     */
    override fun openManager() {
        // A screen that resolves can still refuse a keyboard (a permission, a disabled component),
        // which only the attempt reveals; the manager's next component gets its turn then.
        val opened = labelledManagerComponents()
            .mapNotNull(::openIntentFor)
            .any { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
        if (!opened) changeManager()
    }

    /**
     * What opening [component]'s manager means: its app, when the app has a launcher; otherwise
     * the settings screen its service declares. A manager built into the system keeps its vault
     * there — Google's lives in Play services, which has no launcher at all.
     */
    private fun openIntentFor(component: ComponentName): Intent? =
        context.packageManager.getLaunchIntentForPackage(component.packageName)
            ?: settingsActivityOf(component)?.let { Intent(Intent.ACTION_MAIN).setComponent(it) }

    /** The settings activity [component] names in its autofill or credential-provider meta-data. */
    private fun settingsActivityOf(component: ComponentName): ComponentName? {
        val pm = context.packageManager
        val service = runCatching { pm.getServiceInfo(component, PackageManager.GET_META_DATA) }.getOrNull() ?: return null
        for (name in SERVICE_META_DATA) {
            val parser = runCatching { service.loadXmlMetaData(pm, name) }.getOrNull() ?: continue
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    // By resource id, as the framework reads it: an optimised APK strips the
                    // attribute names from its compiled XML and a lookup by name finds nothing.
                    val activity = (0 until parser.attributeCount)
                        .firstOrNull { parser.getAttributeNameResource(it) == android.R.attr.settingsActivity }
                        ?.let(parser::getAttributeValue)
                        ?: break
                    return ComponentName(component.packageName, qualifiedClassName(component.packageName, activity))
                }
            } catch (_: Exception) {
                // A manager's malformed meta-data is not ours to fix; fall through to the next.
            } finally {
                parser.close()
            }
        }
        return null
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

        /** Where an autofill service and a credential provider each declare their settings screen. */
        val SERVICE_META_DATA = listOf("android.autofill", "android.credentials.provider")
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
): String? = managerComponentCandidates(primaryCredentialProvider, autofillService, credentialProviders).firstOrNull()

/**
 * Every usable component in the order [pickManagerComponent] prefers them, without repeats: when
 * the preferred one has nothing to open, the next is tried. Pure, for tests.
 */
fun managerComponentCandidates(
    primaryCredentialProvider: String?,
    autofillService: String?,
    credentialProviders: String?,
): List<String> =
    (listOf(primaryCredentialProvider, autofillService) + credentialProviders.orEmpty().split(':'))
        .filterNotNull()
        .map { it.trim() }
        .filter { it.contains('/') && !it.startsWith("PLACEHOLDER", ignoreCase = true) }
        .distinct()

/** A manifest class name as a full one: `.ui.Settings` belongs to [packageName]. */
fun qualifiedClassName(packageName: String, className: String): String =
    if (className.startsWith('.')) packageName + className else className
