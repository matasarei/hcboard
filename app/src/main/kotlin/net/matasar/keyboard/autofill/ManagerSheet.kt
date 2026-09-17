package net.matasar.keyboard.autofill

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.autofill.AutofillService
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.core.net.toUri
import org.xmlpull.v1.XmlPullParser

/** One password manager the sheet can name and open. */
data class ManagerApp(
    val packageName: String,
    /** Its service's own name ("Google", "Enpass"), or the app's. */
    val label: String,
    /** Whether opening it reaches the manager itself, rather than Android's settings for it. */
    val opensItself: Boolean,
)

/** What the key button's sheet can do; the service implements it with real intents. */
interface AutofillActions {
    /** The password managers to offer, preferred first; empty when none is installed. */
    fun managers(): List<ManagerApp>
    fun openManager(manager: ManagerApp)
    /** Opens the fill screen; the password the manager fills there is typed into this field. */
    fun fillPassword()
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

class AndroidAutofillActions(
    private val context: Context,
    private val onFillPassword: () -> Unit,
) : AutofillActions {

    override fun fillPassword() = onFillPassword()

    /**
     * Every installed autofill and credential-provider service. Android 14+ keeps the user's
     * preferred provider in a setting no ordinary app may read, so the sheet asks which apps
     * offer the services instead; the manifest's `<queries>` makes exactly those apps visible.
     */
    private fun managerServices(): List<ComponentName> {
        val pm = context.packageManager
        return SERVICE_ACTIONS.flatMap { action ->
            @Suppress("DEPRECATION") // the flags overload is API 33+; the int one serves every version
            runCatching { pm.queryIntentServices(Intent(action), 0) }.getOrDefault(emptyList())
        }.map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    }

    /** The package of the autofill service Android reports: public, unlike the provider setting. */
    private fun preferredPackage(): String? = runCatching {
        context.getSystemService(AutofillManager::class.java)?.autofillServiceComponentName?.packageName
    }.getOrNull()

    override fun managers(): List<ManagerApp> {
        val services = managerServices()
        return managersToOffer(services.map { it.packageName }, preferredPackage(), context.packageName).map { pkg ->
            val own = services.filter { it.packageName == pkg }
            ManagerApp(pkg, labelOf(pkg, own), own.any { openIntentFor(it) != null })
        }.sortedBy { it.label.lowercase() }
    }

    /** The first service's own label ("Google", "Enpass"), falling back to the app's. */
    private fun labelOf(packageName: String, services: List<ComponentName>): String {
        val pm = context.packageManager
        return services.firstNotNullOfOrNull { runCatching { pm.getServiceInfo(it, 0).loadLabel(pm).toString() }.getOrNull() }
            ?: runCatching { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() }.getOrNull()
            ?: packageName
    }

    /**
     * Opens [manager] so the user can copy a password and paste it back. It never does nothing:
     * a manager with no screen of its own to open lands the user on Android's password settings.
     * Only that manager's own services are tried, so "Open Enpass" never opens another app.
     */
    override fun openManager(manager: ManagerApp) {
        // A screen that resolves can still refuse a keyboard (a permission, a disabled component),
        // which only the attempt reveals; the manager's next service gets its turn then.
        val opened = managerServices()
            .filter { it.packageName == manager.packageName }
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
                    val settings = ComponentName(component.packageName, qualifiedClassName(component.packageName, activity))
                    // Only the system may start a private one (Google's), so it is not a way in.
                    if (runCatching { pm.getActivityInfo(settings, 0).exported }.getOrDefault(false)) return settings
                    break
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
        /**
         * The two kinds of service a password manager offers; the second is
         * `CredentialProviderService.SERVICE_INTERFACE` by value, which only exists from API 34.
         */
        val SERVICE_ACTIONS = listOf(AutofillService.SERVICE_INTERFACE, "android.service.credentials.CredentialProviderService")

        /** Where an autofill service and a credential provider each declare their settings screen. */
        val SERVICE_META_DATA = listOf("android.autofill", "android.credentials.provider")
    }
}

/** The platform's own credential-manager proxy: it stands in for a provider, it is not one. */
const val CREDENTIAL_MANAGER_PROXY = "com.android.credentialmanager"

/**
 * The password managers the sheet offers, by package. [installed] is every package offering an
 * autofill or credential service, in any order and with repeats; [preferredPackage] is the
 * package of the autofill service Android reports. When that names one of [installed], it is the
 * user's choice and the only one offered. Otherwise every installed manager is, because Android
 * 14+ keeps the preferred credential provider in a setting no ordinary app may read, and the one
 * it can read may hold just a placeholder. Pure, for tests.
 */
fun managersToOffer(installed: Collection<String>, preferredPackage: String?, ownPackage: String): List<String> {
    val managers = installed.filter { it != ownPackage && it != CREDENTIAL_MANAGER_PROXY }.distinct().sorted()
    return if (preferredPackage != null && preferredPackage in managers) listOf(preferredPackage) else managers
}

/** A manifest class name as a full one: `.ui.Settings` belongs to [packageName]. */
fun qualifiedClassName(packageName: String, className: String): String =
    if (className.startsWith('.')) packageName + className else className
