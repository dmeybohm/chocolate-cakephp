import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.net.URI
import java.security.MessageDigest

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intellijPlatform) // IntelliJ Platform Gradle Plugin 2.x
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
//    implementation(libs.annotations)

    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaUltimate, properties("platformVersion"))

        // Plugin Dependencies - PHP plugin from JetBrains Marketplace
        // Parse platformPlugins property (format: "com.jetbrains.php:233.15026.12")
        plugins(properties("platformPlugins").get()
            .split(',').map(String::trim).filter(String::isNotEmpty))

        // Required for instrumentation
        instrumentationTools()

        // Required for testing
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
    }

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    jvmToolchain(17)
}

// Configure IntelliJ Platform Gradle Plugin 2.x - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2023.2")
        }
        freeArgs = listOf(
            "-mute",
            "TemplateWordInPluginId"
        )
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover.xmlReport {
    onCheck = true
}

// Configure UI tests plugin - read more: https://github.com/JetBrains/intellij-ui-test-robot
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false"
                    )
                }
            }
            plugins {
                robotServerPlugin()
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    compileKotlin {
        kotlinOptions.jvmTarget = properties("kotlinJvmTarget").get()
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = properties("kotlinJvmTarget").get()
    }

    // Run tests against the real CakePHP source with -PcakeVendor=real, or the hand-written stubs by default
    val cakeVendor = properties("cakeVendor").getOrElse("stub")
    val cakeReleases = mapOf(
        5 to ("5.4.3" to "72618190ab60cd1d15ef3404cc4d4750bc01ccd070f9edf7868801b6b9f3b9f8"),
        4 to ("4.6.5" to "1d63f746514db6cabab084737a6d29f3853129e19a356ca525e21b9043240436"),
    )
    val fetchCakeVendors = cakeReleases.map { (major, release) ->
        register<FetchCakePhpVendor>("fetchCake${major}Vendor") {
            cakeVersion = release.first
            sha256 = release.second
            downloadDir = gradle.gradleUserHomeDir.resolve("caches/chocolate-cakephp")
            outputDir = layout.buildDirectory.dir("cakephp-vendor/$major")
        }
    }

    test {
        useJUnitPlatform()
        systemProperty("chocolateCakePHP.vendor", cakeVendor)
        if (cakeVendor == "real") {
            cakeReleases.keys.zip(fetchCakeVendors).forEach { (major, fetch) ->
                dependsOn(fetch)
                systemProperty(
                    "chocolateCakePHP.vendor.cake$major",
                    fetch.get().outputDir.get().asFile.absolutePath
                )
            }
        }
        // Support 'setUp' like 'BasePlatformTestCase::setUp' as valid test structure
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // Note: patchPluginXml is now configured via intellijPlatform.pluginConfiguration block
    // We need to add the description and changeNotes to that block
    patchPluginXml {
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with (it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }


    // Note: signPlugin and publishPlugin are now configured via intellijPlatform.signing and intellijPlatform.publishing blocks
    publishPlugin {
        dependsOn("patchChangelog")
    }
}

/**
 * A pinned release of the CakePHP framework source, unpacked for use as a test library.
 */
abstract class FetchCakePhpVendor @Inject constructor(
    private val archives: ArchiveOperations,
    private val files: FileSystemOperations,
) : DefaultTask() {
    @get:Input abstract val cakeVersion: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Internal abstract val downloadDir: Property<File>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val version = cakeVersion.get()
        val zip = downloadDir.get().resolve("cakephp-$version.zip")
        if (!zip.isFile || sha256Of(zip) != sha256.get()) {
            zip.parentFile.mkdirs()
            val url = URI("https://codeload.github.com/cakephp/cakephp/zip/refs/tags/$version").toURL()
            url.openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
            val actual = sha256Of(zip)
            if (actual != sha256.get()) {
                zip.delete()
                throw GradleException("Checksum mismatch for CakePHP $version: $actual")
            }
        }
        files.sync {
            from(archives.zipTree(zip)) {
                include("cakephp-$version/src/**")
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(outputDir)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
