import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

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
        sources.forEach { it.copyTo(File(raw, resourceName(it.name)), overwrite = true) }
    }

    /**
     * A resource name a file name can become: lower case, letters, digits and `_` only, and one
     * `.txt`. LICENSE becomes R.raw.license, BSD-3-Clause.txt becomes R.raw.bsd_3_clause.
     */
    private fun resourceName(fileName: String): String =
        fileName.removeSuffix(".txt").lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("") + ".txt"
}

/**
 * Writes the list of libraries inside the APK and the licence each one declares, grouped by
 * licence, for the licences screen to show. The result is committed (res/raw/dependencies.txt),
 * the way the word lists are: reading POMs means resolving the dependency graph, and a build
 * should not pay for that. `checkDependencyLicences` re-runs it against the committed file, so a
 * dependency that comes, goes or changes its licence cannot slip by unlisted; a licence this
 * build has never seen stops it.
 */
abstract class GenerateDependencyLicences : DefaultTask() {
    /** "group:artifact:version" for every module on the runtime classpath. */
    @get:Input
    abstract val coordinates: ListProperty<String>

    /** Their POMs, where the licence is declared. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val poms: ConfigurableFileCollection

    /** Where the list goes: the committed resource, or a scratch copy when checking. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /** The committed list to compare against; set on the check task only. */
    @get:InputFile
    @get:Optional
    abstract val expected: RegularFileProperty

    @TaskAction
    fun generate() {
        val byName = pomsByName()
        val licences = coordinates.get().associateWith { licenceOf(it, byName) }
        val unknown = licences.filterValues { it == null }.keys
        if (unknown.isNotEmpty()) {
            throw GradleException(
                "No licence found for ${unknown.size} module(s), so the licences screen would not list them: " +
                    unknown.sorted().joinToString(),
            )
        }
        val strange = licences.filterValues { it !in KNOWN_LICENCES }
        if (strange.isNotEmpty()) {
            throw GradleException(
                "Unknown licence on ${strange.keys.sorted().joinToString()}: ${strange.values.filterNotNull().distinct().sorted().joinToString()}. " +
                    "Add it to the licences screen (licences/, KNOWN_LICENCES) before shipping it.",
            )
        }
        val listed = summary(licences.mapValues { it.value!! })
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(listed + "\n")
        val committed = expected.orNull?.asFile ?: return
        if (committed.readText().trim() != listed.trim()) {
            throw GradleException(
                "${committed.path} no longer matches the dependencies: run ./gradlew :app:generateDependencyLicences and commit the result.",
            )
        }
    }

    /**
     * The POMs by file name, which is how [licenceOf] looks one up. Two modules from different
     * groups can publish the same artifact name and version; one would then answer for the
     * other's licence, so that stops the build instead.
     */
    private fun pomsByName(): Map<String, File> {
        val byName = poms.files.groupBy { it.name }
        val clashes = byName.filterValues { it.size > 1 }
        if (clashes.isNotEmpty()) {
            throw GradleException(
                "Two POMs share a file name, so a licence could be read from the wrong module: " +
                    clashes.values.flatten().joinToString { it.path },
            )
        }
        return byName.mapValues { it.value.single() }
    }

    /**
     * The first licence the module's POM names. A POM may leave it to two other places, and both
     * are followed: an -android or -jvm variant inherits from its base module (most of Jetpack
     * Compose), and a POM with a parent inherits from the parent (Guava's ListenableFuture).
     */
    private fun licenceOf(coordinate: String, poms: Map<String, File>): String? {
        val (_, artifact, version) = coordinate.split(":")
        val base = artifact.removeSuffix("-android").removeSuffix("-jvm")
        for (name in listOf("$artifact-$version.pom", "$base-$version.pom")) {
            licenceIn(poms[name] ?: continue, poms)?.let { return it }
        }
        return null
    }

    /** [pom]'s own licence, or its parent's, following the chain while a parent is at hand. */
    private fun licenceIn(pom: File, poms: Map<String, File>, depth: Int = 0): String? {
        val text = pom.readText()
        LICENCE_NAME.find(text)?.let { return normalise(it.groupValues[1].trim()) }
        if (depth >= MAX_PARENTS) return null
        val parent = PARENT.find(text) ?: return null
        val (artifact, version) = parent.destructured
        return licenceIn(poms["$artifact-$version.pom"] ?: return null, poms, depth + 1)
    }

    /**
     * One licence, one spelling: the POMs carry three for Apache-2.0 alone ("The Apache Software
     * License, Version 2.0", "Apache-2.0", "The Apache License, Version 2.0"). The version is
     * part of what is matched: Apache-1.1 is a different licence, and naming it 2.0 on the screen
     * would say something untrue. Anything unrecognised is left as the POM wrote it, for the
     * known-licence check to refuse.
     */
    private fun normalise(name: String): String = when {
        name.contains("Apache", ignoreCase = true) && name.contains("2.0") -> "Apache License 2.0"
        name.contains("BSD-3", ignoreCase = true) || name.contains("BSD 3", ignoreCase = true) -> "BSD 3-Clause"
        else -> name
    }

    /**
     * Licence, then what brings it in: a family of modules where there are several (androidx.*),
     * and the module itself where there are one or two, so a single odd dependency is named.
     */
    private fun summary(licences: Map<String, String>): String = buildString {
        for (licence in licences.values.distinct().sorted()) {
            append(licence).append("\n\n")
            val modules = licences.filterValues { it == licence }.keys
            val families = modules.groupBy { family(it) }
            for ((family, members) in families.toSortedMap()) {
                if (members.size >= FAMILY_MINIMUM) {
                    append("- ").append(describe(family)).append(", ").append(members.size).append(" modules\n")
                } else {
                    members.sorted().forEach { append("- ").append(it.substringBeforeLast(":")).append("\n") }
                }
            }
            append("\n")
        }
    }.trimEnd()

    /** androidx.compose.ui belongs to androidx; org.jetbrains.kotlinx to org.jetbrains. */
    private fun family(coordinate: String): String {
        val group = coordinate.substringBefore(":")
        return if (group.startsWith("androidx.")) "androidx" else group.split(".").take(2).joinToString(".")
    }

    private fun describe(family: String): String =
        DESCRIPTIONS[family]?.let { "$family.* ($it)" } ?: "$family.*"

    private companion object {
        val LICENCE_NAME = Regex("<licenses>.*?<name>([^<]+)</name>", RegexOption.DOT_MATCHES_ALL)

        val PARENT = Regex("<parent>.*?<artifactId>([^<]+)</artifactId>.*?<version>([^<]+)</version>", RegexOption.DOT_MATCHES_ALL)

        /** A POM inheriting through more parents than this is not something this build has. */
        const val MAX_PARENTS = 5

        /** Every licence the screen carries the text of (res/raw, from licences/ and the root). */
        val KNOWN_LICENCES = setOf("Apache License 2.0", "BSD 3-Clause")

        /** Fewer modules than this under one licence are named one by one rather than as a family. */
        const val FAMILY_MINIMUM = 3

        val DESCRIPTIONS = mapOf(
            "androidx" to "Jetpack and Jetpack Compose",
            "org.jetbrains" to "Kotlin and kotlinx",
        )
    }
}

/** The POM files of whatever [components] selects, or nothing when a module publishes none. */
fun pomFiles(components: ArtifactResolutionQuery.() -> ArtifactResolutionQuery): List<File> =
    dependencies.createArtifactResolutionQuery()
        .components()
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()
        .resolvedComponents
        .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
        .filterIsInstance<ResolvedArtifactResult>()
        .map { it.file }

/** The committed list the licences screen shows. */
val dependencyLicencesFile = layout.projectDirectory.file("src/main/res/raw/dependencies.txt")

fun GenerateDependencyLicences.readTheClasspath() {
    val runtimeClasspath = configurations.named("releaseRuntimeClasspath")
    val modules = runtimeClasspath.map { classpath ->
        classpath.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .sortedBy { it.displayName }
    }
    coordinates.set(modules.map { list -> list.map { "${it.group}:${it.module}:${it.version}" } })
    poms.from(
        modules.map { list ->
            val own = pomFiles { forComponents(list) }
            // A POM that names no licence may inherit one: fetch the parents too, one round deep,
            // which is as far as this build's dependencies go (Guava's ListenableFuture).
            val parents = own.mapNotNull { pom ->
                val block = Regex("<parent>(.*?)</parent>", RegexOption.DOT_MATCHES_ALL).find(pom.readText())?.groupValues?.get(1) ?: return@mapNotNull null
                // Read each field on its own: a POM may write them in any order.
                fun field(name: String) = Regex("<$name>([^<]+)</$name>").find(block)?.groupValues?.get(1)?.trim()
                val (group, artifact, version) = listOf("groupId", "artifactId", "version").map { field(it) ?: return@mapNotNull null }
                Triple(group, artifact, version)
            }.distinct()
            own + parents.flatMap { (group, artifact, version) -> pomFiles { forModule(group, artifact, version) } }
        },
    )
}

val dependencyLicences = tasks.register<GenerateDependencyLicences>("generateDependencyLicences") {
    description = "Writes src/main/res/raw/dependencies.txt from the resolved dependencies' POMs."
    readTheClasspath()
    outputFile.set(dependencyLicencesFile)
}

tasks.register<GenerateDependencyLicences>("checkDependencyLicences") {
    description = "Fails when src/main/res/raw/dependencies.txt no longer matches the dependencies."
    readTheClasspath()
    outputFile.set(layout.buildDirectory.file("tmp/dependencies-licences/dependencies.txt"))
    expected.set(dependencyLicencesFile)
}

val copyLicenceNotices = tasks.register<CopyLicenceNotices>("copyLicenceNotices") {
    sources.from(
        rootProject.file("LICENSE"),
        rootProject.file("NOTICE"),
        // The Protocol Buffers copy inside androidx.datastore, the one dependency that is not
        // Apache-2.0; taken verbatim from the artifact's own META-INF.
        rootProject.file("licences/BSD-3-Clause.txt"),
    )
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
        versionCode = 4
        versionName = "3.0.0"
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
