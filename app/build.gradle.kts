plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Copies the repository's LICENSE and NOTICE into a generated res/raw, so the APK carries them
 * (Apache-2.0 section 4(d), CC BY 4.0 attribution) and the licences screen shows them. The root
 * files stay the only copy anyone edits.
 */
abstract class CopyLicenceNotices : DefaultTask() {
    // Only the names matter, not where the checkout lives, so the inputs hash the same anywhere.
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val raw = outputDir.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        // Resource names are lower case: LICENSE becomes R.raw.license, NOTICE R.raw.notice.
        sources.forEach { it.copyTo(File(raw, it.name.lowercase() + ".txt"), overwrite = true) }
    }
}

val copyLicenceNotices = tasks.register<CopyLicenceNotices>("copyLicenceNotices") {
    sources.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copyLicenceNotices, CopyLicenceNotices::outputDir)
    }
}

android {
    namespace = "net.matasar.keyboard"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.matasar.keyboard"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "2.0.0"
        // The commit a build came from, for the diagnostics on the settings screen. CI sets
        // HCBOARD_COMMIT (a pull request's head, not GitHub's merge commit); anything that is not
        // a full hash, including no variable at all, reads "local build". Checked, because it is
        // pasted into a Java string literal.
        val commit = providers.environmentVariable("HCBOARD_COMMIT").orNull
            ?.takeIf { it.matches(Regex("[0-9a-f]{40}")) } ?: "local build"
        buildConfigField("String", "COMMIT", "\"$commit\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // CI signs dev builds with a stable key from secrets, so a new build installs over the last
    // one. Locally, without the variables, the debug build keeps the default debug key.
    val devKeystore = System.getenv("HCBOARD_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    val releaseKeystore = System.getenv("HCBOARD_RELEASE_KEYSTORE")?.let(::file)?.takeIf { it.exists() }

    signingConfigs {
        if (devKeystore != null) {
            create("dev") {
                storeFile = devKeystore
                storePassword = System.getenv("HCBOARD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HCBOARD_KEY_ALIAS") ?: "dev"
                keyPassword = System.getenv("HCBOARD_KEY_PASSWORD")
            }
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("HCBOARD_RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HCBOARD_RELEASE_KEY_ALIAS") ?: "release"
                keyPassword = System.getenv("HCBOARD_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (devKeystore != null) signingConfig = signingConfigs.getByName("dev")
        }
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
