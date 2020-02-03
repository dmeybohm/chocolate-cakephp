package com.daveme.chocolateCakePHP

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.PhpFileType

data class PluginEntry(val plugin: String, val fqn: String)

typealias PluginList = MutableList<PluginEntry>

class CakePlugin {
    private val models = mutableMapOf<String, PluginList>()
    private val components = mutableMapOf<String, PluginList>()
    private val viewHelpers = mutableMapOf<String, PluginList>()

    fun indexCake2DirectoryFromFile(file: PsiFile) {
        val directory = file.containingDirectory
        val pluginDir = findPluginDirFromDirectory(directory)

        for (pluginInstanceDir in pluginDir.subdirectories) {
            if (!pluginInstanceDir.isDirectory) {
                continue
            }

            for (subdir in pluginInstanceDir.subdirectories) {
                when (subdir.name) {
                    "Model" -> addFiles(this.models, pluginInstanceDir.name, subdir)
//                    "Controller" -> findComponents(pluginInstanceDir.name, subdir)
//                    "View" -> findViewHelpers(pluginInstanceDir.name, subdir)
                }
            }
        }
    }

    private fun findPluginDirFromDirectory(directory: PsiDirectory?): PsiDirectory {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun addFiles(map: MutableMap<String, PluginList>, pluginInstanceName: String, subdir: PsiDirectory) {
        for (file in subdir.files) {
            if (file.isDirectory) {
                continue
            }
            if (file.fileType is PhpFileType) {
                if (!map.contains(pluginInstanceName)) {
                    map[pluginInstanceName] = mutableListOf()
                }
                map[pluginInstanceName]!!.add(PluginEntry(plugin=pluginInstanceName, fqn=file.name))
            }
        }
    }
}

