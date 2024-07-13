package com.daveme.chocolateCakePHP

import com.daveme.chocolateCakePHP.cake.PluginEntry
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.lang.ref.WeakReference


private const val DEFAULT_NAMESPACE = "\\App"

private const val DEFAULT_APP_DIRECTORY = "src"

private const val DEFAULT_CAKE3_TEMPLATE_EXTENSION = "ctp"

data class SettingsState(
    var cakeTemplateExtension: String = DEFAULT_CAKE3_TEMPLATE_EXTENSION,
    var appDirectory: String = DEFAULT_APP_DIRECTORY,
    var appNamespace: String = DEFAULT_NAMESPACE,
    var pluginPath: String = "plugins",
    var cake2AppDirectory: String =  "app",
    var cake2TemplateExtension: String = "ctp",
    var cake2PluginPath: String = "app/Plugin",
    var cake2Enabled: Boolean = false,
    var cake3Enabled: Boolean = true,
    var cake3ForceEnabled: Boolean = false,
    var pluginNamespaces: List<String> = arrayListOf("\\DebugKit"),
    var dataViewExtensions: List<String> = arrayListOf("json", "xml"),
)

// For accessibility from Java, which doesn't support copy() with default args:
fun copySettingsState(state: SettingsState): SettingsState = state.copy()

data class CakeAutoDetectedValues(
    val cake3OrLaterPresent: Boolean = false,
    val namespace: String = DEFAULT_NAMESPACE,
    val appDirectory: String = DEFAULT_APP_DIRECTORY,
)

@Service(Service.Level.PROJECT)
class CakePhpAutoDetector(project: Project)
{
    val projectRef = WeakReference(project)
    val autoDetectedValues by lazy { autodetectCakePhp() }

    private fun autodetectCakePhp(): CakeAutoDetectedValues {
        val project = projectRef.get() ?: return CakeAutoDetectedValues()

        val topDir = project.guessProjectDir() ?: return CakeAutoDetectedValues()
        val composerJson = topDir.findFileByRelativePath("composer.json")
            ?: return CakeAutoDetectedValues()
        val fullPath = composerJson.canonicalPath ?: return CakeAutoDetectedValues()
        val composerContents = File(fullPath).readText()

        val namespace = checkNamespaceInAppController(topDir)
        return CakeAutoDetectedValues(
            cake3OrLaterPresent = checkCakePhpInComposerJson(composerContents),
            namespace = checkNamespaceInAppController(topDir),
            appDirectory = extractAppDirFromComposerJson(composerContents, namespace)
        )
    }

    private fun extractAppDirFromComposerJson(
        composerContents: String,
        namespace: String
    ): String {
        try {
            val json = JsonParser(composerContents).parse() as? Map<*, *>
                ?: return DEFAULT_APP_DIRECTORY
            val autoloadObj = json["autoload"] as? Map<*, *>
                ?: return DEFAULT_APP_DIRECTORY
            val psr4 = autoloadObj["psr-4"] as? Map<*, *>
                ?: return DEFAULT_APP_DIRECTORY
            val targetNamespace = "${namespace}\\".removeFromStart("\\")
            val directory = psr4[targetNamespace] as? String
                ?: return DEFAULT_APP_DIRECTORY
            return directory.removeFromEnd("/")
        } catch (e: Exception) {
            return DEFAULT_APP_DIRECTORY
        }
    }

    private fun checkCakePhpInComposerJson(composerContents: String): Boolean {
        return composerContents.contains("\"cakephp/cakephp\"")
    }

    private fun checkNamespaceInAppController(topDir: VirtualFile): String {
        val appController = topDir.findFileByRelativePath("src/Controller/AppController.php")
            ?: return DEFAULT_NAMESPACE
        val fullPath = appController.canonicalPath ?: return DEFAULT_APP_DIRECTORY
        val regex = Regex("^\\s*namespace\\s+([\\w\\\\]+);")

        val lines = File(fullPath).readLines()
        for (line in lines) {
            val namespace = regex.find(line)?.groupValues?.get(1) ?: continue
            return namespace.removeFromEnd("\\Controller").absoluteClassName()
        }
        return DEFAULT_NAMESPACE
    }
}

@Service(Service.Level.PROJECT)
@State(
    name = "ChocolateCakePHPSettings",
    storages = [Storage( "ChocolateCakePHP.xml")]
)
class Settings : PersistentStateComponent<SettingsState> {

    private var state = SettingsState()

    val cakeTemplateExtension get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled) {
            DEFAULT_CAKE3_TEMPLATE_EXTENSION
        } else {
            state.cakeTemplateExtension
        }
    }

    val appDirectory get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled)
            autoDetectedValues.appDirectory
        else
            state.appDirectory
    }

    val appNamespace get(): String {
        return if (state.cake3Enabled && !state.cake3ForceEnabled)
            autoDetectedValues.namespace
        else
            return state.appNamespace.absoluteClassName()
    }

    val pluginPath get() = state.pluginPath
    val cake2AppDirectory get() = state.cake2AppDirectory
    val cake2TemplateExtension get() = state.cake2TemplateExtension

    val cake2Enabled get() = state.cake2Enabled
    val cake3Enabled get() = cake3ForceEnabled ||
            (state.cake3Enabled && autoDetectedValues.cake3OrLaterPresent)
    val cake3ForceEnabled get() = state.cake3ForceEnabled
    var autoDetectedValues = CakeAutoDetectedValues()

    val pluginEntries: List<PluginEntry>
        get() {
            return pluginEntryListFromNamespaceList(state.pluginNamespaces)
        }

    val dataViewExtensions: List<String>
        get() {
            return state.dataViewExtensions
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

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): Settings {
            val settings = project.getService(Settings::class.java)
            if (settings.state.cake3Enabled && !settings.state.cake3ForceEnabled) {
                val autodetector = project.getService(CakePhpAutoDetector::class.java)
                settings.autoDetectedValues = autodetector.autoDetectedValues
            }
            return settings
        }

        @JvmStatic
        val defaults get() = Settings()

        @JvmStatic
        fun fromSettings(settings: Settings): Settings {
            val newState = settings.state.copy()
            val newSettings = Settings()
            newSettings.loadState(newState)
            return newSettings
        }

        @JvmStatic
        fun pluginEntryListFromNamespaceList(list: List<String>): List<PluginEntry> {
            val result = arrayListOf<PluginEntry>()
            list.forEach { result.add(PluginEntry(it)) }
            return result.toList()
        }

        @JvmStatic
        fun pluginNamespaceListFromEntryList(list: List<PluginEntry>): List<String> {
            val result = arrayListOf<String>()
            list.forEach { result.add(it.namespace) }
            return result
        }

    }

}

