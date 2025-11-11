import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

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
            untilBuild = properties("pluginUntilBuild")
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

    test {
        useJUnitPlatform()
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
