package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.CakeFourTemplatesDir
import com.daveme.chocolateCakePHP.cake.CakeThreeTemplatesDir
import com.daveme.chocolateCakePHP.cake.CakeTwoTemplatesDir
import com.daveme.chocolateCakePHP.cake.TemplatesDir
import com.daveme.chocolateCakePHP.cake.cakeThreePluginName
import com.daveme.chocolateCakePHP.cake.cakeTwoPluginName
import com.daveme.chocolateCakePHP.cake.templatesDirectoryOfViewFile
import com.daveme.chocolateCakePHP.view.viewfileindex.lookupControllerFileInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile

private val pluginPrefix = Regex("^([A-Z][A-Za-z0-9_/]*)\\.(.+)$")

/**
 * The conventions one CakePHP version uses for view files: which plugin a file belongs to, how
 * view names resolve to plugins, and which classes supply a view's helpers.
 */
sealed class TemplateDirStructure(val id: Char) {

    /**
     * The plugin [file] belongs to, or null for a file of the app.
     */
    abstract fun pluginOf(file: VirtualFile, projectDir: VirtualFile?, settings: Settings): String?

    /**
     * The prefix a template key has in the app's override directory for [plugin].
     */
    abstract fun overridePrefix(plugin: String): String

    /**
     * A view name without its plugin prefix, and the plugin the prefix names, if it has one.
     * Without a prefix, the name resolves in the plugin of whatever renders the view.
     */
    open fun resolveName(name: String): Pair<String, String?> {
        val match = pluginPrefix.matchEntire(name) ?: return name to null
        return match.groupValues[2] to match.groupValues[1]
    }

    /**
     * The directory layouts are in, under the templates directory, when layouts are tracked.
     */
    open val layoutDirectory: String? = null

    /**
     * Whether the view file index stores references to layouts.
     */
    open val indexesLayoutReferences: Boolean = false

    /**
     * Whether [templateKey] is the key of a layout.
     */
    fun isLayoutKey(templateKey: String): Boolean =
        layoutDirectory?.let { templateKey.startsWith("$it/") } ?: false

    /**
     * The directory cell templates are in, under the templates directory, on versions with cells.
     */
    open val cellDirectory: String? = null

    /**
     * Whether [templateKey] is the key of a cell's template.
     */
    fun isCellKey(templateKey: String): Boolean =
        cellDirectory?.let { templateKey.startsWith("$it/") } ?: false

    /**
     * Whether a `$helpers` property on the class rendering a view supplies its helpers.
     */
    abstract val readsHelpersProperty: Boolean

    /**
     * The class every application controller extends, when layouts are tracked.
     */
    open val appControllerClass: String? = null

    /**
     * The framework class every controller extends, when layouts are tracked.
     */
    open val frameworkControllerClass: String? = null

    /**
     * The classes that supply the helpers of every view file, whatever renders it.
     */
    abstract fun viewClassReceivers(settings: Settings): List<String>

    /**
     * Whether the controllers that render a view file supply its helpers.
     */
    abstract val usesRenderingControllers: Boolean

    /**
     * The receivers of a view file no controller is found rendering.
     */
    abstract val fallbackReceivers: List<String>

    object CakeTwo : TemplateDirStructure('2') {
        override fun pluginOf(file: VirtualFile, projectDir: VirtualFile?, settings: Settings) =
            cakeTwoPluginName(file, projectDir, settings)

        override fun overridePrefix(plugin: String) = "Plugin/$plugin/"

        // The controller's helpers replace the view's own:
        override fun viewClassReceivers(settings: Settings) = emptyList<String>()

        override val usesRenderingControllers = true

        override val fallbackReceivers = listOf("\\AppController")

        override val layoutDirectory = "Layouts"

        override val indexesLayoutReferences = true

        override val readsHelpersProperty = true

        override val appControllerClass = "\\AppController"

        override val frameworkControllerClass = "\\Controller"
    }

    class CakeThreeAndLater(
        id: Char,
        private val overrideDirectory: String,
        override val layoutDirectory: String,
        override val cellDirectory: String,
        override val readsHelpersProperty: Boolean,
    ) : TemplateDirStructure(id) {
        override fun pluginOf(file: VirtualFile, projectDir: VirtualFile?, settings: Settings) =
            cakeThreePluginName(file, projectDir, settings, overrideDirectory)

        override fun overridePrefix(plugin: String) = "$overrideDirectory/$plugin/"

        override fun viewClassReceivers(settings: Settings) = listOf("${settings.appNamespace}\\View\\AppView")

        override val usesRenderingControllers = true

        override val fallbackReceivers = emptyList<String>()

        override val frameworkControllerClass = "\\Cake\\Controller\\Controller"
    }

    companion object {
        private val cakeThree = CakeThreeAndLater('3', "Plugin", "Layout", "Cell", readsHelpersProperty = true)
        private val cakeFour = CakeThreeAndLater('4', "plugin", "layout", "cell", readsHelpersProperty = false)

        fun of(templatesDir: TemplatesDir): TemplateDirStructure = when (templatesDir) {
            is CakeTwoTemplatesDir -> CakeTwo
            is CakeThreeTemplatesDir -> cakeThree
            is CakeFourTemplatesDir -> cakeFour
        }

        fun ofId(id: Char): TemplateDirStructure? = listOf(CakeTwo, cakeThree, cakeFour).firstOrNull { it.id == id }

        /**
         * The plugin of a controller or cell class file, or null for the app.
         */
        fun pluginOfClassFile(project: Project, settings: Settings, file: VirtualFile): String? =
            ofSourceFile(project, settings, file, isController = true)
                ?.pluginOf(file, project.guessProjectDir(), settings)

        /**
         * The structure of a file that refers to views: a view file, or a controller.
         */
        fun ofSourceFile(project: Project, settings: Settings, file: VirtualFile, isController: Boolean): TemplateDirStructure? {
            templatesDirectoryOfViewFile(project, settings, file)?.let { return of(it) }
            if (!isController) {
                return null
            }
            // A plugin controller is not under the app directory, so with only CakePHP 2
            // enabled every controller is a CakePHP 2 one:
            return if (settings.cake2Enabled &&
                (!settings.cake3Enabled || lookupControllerFileInfo(file, settings).isCakeTwo))
                CakeTwo
            else
                cakeFour
        }
    }
}
