package com.daveme.chocolateCakePHP

import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.daveme.chocolateCakePHP.cake.ThemeEntry
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.nio.charset.StandardCharsets


private const val DEFAULT_NAMESPACE = "\\App"

private const val DEFAULT_APP_DIRECTORY = "src"

private const val DEFAULT_CAKE3_TEMPLATE_EXTENSION = "ctp"

fun defaultPluginConfig(): PluginConfig {
    return PluginConfig()
}

fun defaultThemeConfig(): ThemeConfig {
    return ThemeConfig()
}

interface ThemeOrPluginConfig {
    val pluginPath: String
    val assetPath: String
}

@Tag("PluginConfig")
data class PluginConfig(

    @Property
    val namespace: String = "",

    @Property
    val srcPath: String = "src",

    @Property
    override val pluginPath: String = "",

    @Property
    override val assetPath: String = "webroot",
) : ThemeOrPluginConfig {}

@Tag("ThemeConfig")
data class ThemeConfig (
    @Property
    override val pluginPath: String = "",

    @Property
    override val assetPath: String = "webroot"
) : ThemeOrPluginConfig

data class SettingsState(
    var cakeTemplateExtension: String = DEFAULT_CAKE3_TEMPLATE_EXTENSION,
    var appDirectory: String = DEFAULT_APP_DIRECTORY,
    var appNamespace: String = DEFAULT_NAMESPACE,
    var pluginPath: String = "plugins",
    var cake2AppDirectory: String =  "app",
    var cake2TemplateExtension: String = "ctp",
    var cake2Enabled: Boolean = false,
    var cake3Enabled: Boolean = true,
    var cake3ForceEnabled: Boolean = false,

    // deprecated: superseded by pluginConfigs
    var pluginNamespaces: List<String> = listOf(),

    var dataViewExtensions: List<String> = arrayListOf("json", "xml"),

    @XCollection
    var pluginConfigs: List<PluginConfig> = listOf(),

    @XCollection
    var themeConfigs: List<ThemeConfig> = listOf(),
)

// For accessibility from Java, which doesn't support copy() with default args:
fun copySettingsState(state: SettingsState): SettingsState = state.copy()

data class CakeAutoDetectedValues(
    val cake3OrLaterPresent: Boolean = false,
    val namespace: String = DEFAULT_NAMESPACE,
    val appDirectory: String = DEFAULT_APP_DIRECTORY,
)

@Service(Service.Level.PROJECT)
class CakePhpAutoDetector(val project: Project)
{
    val autoDetectedValues: CakeAutoDetectedValues
        get() = CachedValuesManager.getManager(project).getCachedValue(project) {
            val result = autodetectCakePhp()
            val dependencies = mutableListOf<Any>()

            // Add composer.json as a dependency
            val composerJson = project.guessProjectDir()?.findFileByRelativePath("composer.json")
            if (composerJson != null) {
                dependencies.add(composerJson)
            }

            // Add config/app.php as a dependency
            val appConfig = project.guessProjectDir()?.findFileByRelativePath("config/app.php")
            if (appConfig != null) {
                dependencies.add(appConfig)
            }

            CachedValueProvider.Result.create(result, dependencies)
        }

    private fun autodetectCakePhp(): CakeAutoDetectedValues {
        val topDir = project.guessProjectDir() ?: return CakeAutoDetectedValues()
        val composerJson = topDir.findFileByRelativePath("composer.json")
            ?: return CakeAutoDetectedValues()

        val composerContents = try {
            String(composerJson.contentsToByteArray(), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return CakeAutoDetectedValues()
        }

        val namespace = checkNamespaceInAppConfig(topDir)

        val (cake3OrLaterPresent: Boolean, appDirectory: String) = try {
            val composerJsonParsed = jsonParse(composerContents)
                as? Map<*, *> ?: throw Exception("Failed parsing")
            Pair(
                checkCakePhpInComposerJson(composerJsonParsed),
                extractAppDirFromComposerJson(composerJsonParsed, namespace)
            )
        } catch (e: Exception) {
            Pair(false, DEFAULT_APP_DIRECTORY)
        }

        return CakeAutoDetectedValues(
            cake3OrLaterPresent = cake3OrLaterPresent,
            namespace = namespace,
            appDirectory = appDirectory,
        )
    }

    private fun extractAppDirFromComposerJson(
        json: Map<*, *>,
        namespace: String
    ): String {
        val autoloadObj = json["autoload"] as? Map<*, *>
            ?: return DEFAULT_APP_DIRECTORY
        val psr4 = autoloadObj["psr-4"] as? Map<*, *>
            ?: return DEFAULT_APP_DIRECTORY
        val targetNamespace = "${namespace}\\".removeFromStart("\\")
        val directory = psr4[targetNamespace] as? String
            ?: return DEFAULT_APP_DIRECTORY
        return directory.removeFromEnd("/")
    }

    private fun checkCakePhpInComposerJson(composerParsed: Map<*, *>): Boolean {
        val required = composerParsed["require"] as? Map<*, *> ?: return false
        if (required["cakephp/cakephp"] != null) {
            return true
        }
        return false
    }

    private fun checkNamespaceInAppConfig(topDir: VirtualFile): String {
        val appConfig = topDir.findFileByRelativePath("config/app.php")
            ?: return DEFAULT_NAMESPACE

        val contents = try {
            String(appConfig.contentsToByteArray(), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return DEFAULT_NAMESPACE
        }

        val regex = Regex("""^\s*['"]namespace['"]\s*=>\s*['"]([^']*)['"]\s*,\s*$""")
        val lines = contents.lines()
        for (line in lines) {
            val namespace = regex.find(line)?.groupValues?.get(1) ?: continue
            return namespace.replace("\\\\", "\\")
                .absoluteClassName()
        }
        return DEFAULT_NAMESPACE
    }
}

@Service(Service.Level.PROJECT)
@State(
    name = "ChocolateCakePHPSettings",
    storages = [Storage( "ChocolateCakePHP.xml")]
)
class Settings(val project: Project) : PersistentStateComponent<SettingsState>, Disposable {

    private var state = SettingsState()

    override fun dispose() {
        // nothing here, but maybe should keep track of things to dispose that use this?
    }

    val cakeTemplateExtension get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled) {
            DEFAULT_CAKE3_TEMPLATE_EXTENSION
        } else {
            state.cakeTemplateExtension
        }
    }

    val appDirectory get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled)
            getAutoDetectedValues().appDirectory
        else
            state.appDirectory
    }

    val appNamespace get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled)
            getAutoDetectedValues().namespace
        else
            return state.appNamespace.absoluteClassName()
    }

    val cake2AppDirectory get() = state.cake2AppDirectory
    val cake2TemplateExtension get() = state.cake2TemplateExtension

    val cake2Enabled get() = state.cake2Enabled
    val cake3Enabled get() = cake3ForceEnabled ||
            (state.cake3Enabled && getAutoDetectedValues().cake3OrLaterPresent)
    val cake3AutoDetect get() = state.cake3Enabled
    val cake3ForceEnabled get() = state.cake3ForceEnabled

    private fun getAutoDetectedValues(): CakeAutoDetectedValues {
        if (!state.cake3Enabled || state.cake3ForceEnabled) {
            return CakeAutoDetectedValues()
        }
        return project.getService(CakePhpAutoDetector::class.java).autoDetectedValues
    }

    private fun synthesizePluginEntries(
        pluginNamespaces: List<String>,
        pluginConfigs: List<PluginConfig>
    ): List<PluginConfig> {
        val result = hashMapOf<String, PluginConfig>()
        for (pluginConfig in pluginConfigs) {
            result.set(pluginConfig.namespace, pluginConfig)
        }

        for (pluginNamespace in pluginNamespaces) {
            if (!result.containsKey(pluginNamespace)) {
                result.set(pluginNamespace, PluginConfig(
                    namespace = pluginNamespace,
                    pluginPath = ""
                ))
            }
        }

        return result.values.toList()
    }

    val dataViewExtensions: List<String>
        get() {
            return state.dataViewExtensions
        }

    val pluginConfigs: List<PluginConfig>
        get() {
            if (state.pluginNamespaces.isEmpty()) {
                return state.pluginConfigs
            } else {
                return synthesizePluginEntries(
                    state.pluginNamespaces,
                    state.pluginConfigs
                )
            }
        }

    val themeConfigs: List<ThemeConfig>
        get() {
            return state.themeConfigs
        }

    val pluginAndThemeConfigs: Sequence<ThemeOrPluginConfig>
        get() {
            return themeConfigs.asSequence() +
                    pluginConfigs.asSequence()
        }

    val enabled: Boolean
        get() {
            return cake3Enabled || cake2Enabled
        }

    override fun equals(other: Any?): Boolean {
        return if (other is Settings) {
            this.state == other.state
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    override fun getState(): SettingsState {
        return this.state
    }

    override fun loadState(newState: SettingsState) {
        XmlSerializerUtil.copyBean(newState, this.state)
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): Settings {
            return project.getService(Settings::class.java)
        }

        @JvmStatic
        fun getDefaults(project: Project): Settings {
            return Settings(project)
        }

        @JvmStatic
        fun fromSettings(settings: Settings): Settings {
            val newState = settings.state.copy()
            val newSettings = Settings(settings.project)
            newSettings.loadState(newState)
            return newSettings
        }

        @JvmStatic
        fun pluginConfigsFromEntryList(
            list: List<PluginEntry>
        ): List<PluginConfig> {
            return list.map { it.toPluginConfig() }
        }

        @JvmStatic
        fun themeConfigsFromEntryList(
            list: List<ThemeEntry>
        ): List<ThemeConfig> {
            return list.map { it.toThemeConfig() }
        }
    }

}

