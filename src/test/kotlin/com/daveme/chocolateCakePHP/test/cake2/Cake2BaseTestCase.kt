package com.daveme.chocolateCakePHP.test.cake2

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.test.BaseTestCase
import com.intellij.openapi.roots.ProjectFileIndex
import com.jetbrains.php.PhpIndex

private val phpPseudoTypes = setOf(
    "bool", "int", "float", "string", "array", "object", "mixed", "null", "void",
    "false", "true", "callable", "iterable",
)

abstract class Cake2BaseTestCase : BaseTestCase() {

    override val cakeMajorVersion = 2

    override fun setUp() {
        super.setUp()

        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = Settings.getDefaults(myFixture.project).state.copy()
        newState.cake2Enabled = true
        newState.cake3Enabled = false
        newState.cake2AppDirectory = "app"
        originalSettings.loadState(newState)

        setUpTestFiles()
    }

    abstract fun setUpTestFiles()

    /**
     * The class types among [types] that come from the project, leaving out PHP pseudo-types
     * and classes that only exist in an attached framework library.
     */
    protected fun projectClassTypes(types: Collection<String>): Set<String> {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val phpIndex = PhpIndex.getInstance(project)
        return types
            .filter { it.startsWith("\\") && it.removePrefix("\\").lowercase() !in phpPseudoTypes }
            .filterNot { type ->
                val classes = phpIndex.getClassesByFQN(type)
                classes.isNotEmpty() && classes.all { phpClass ->
                    phpClass.containingFile?.virtualFile?.let { fileIndex.isInLibrary(it) } ?: false
                }
            }
            .toSet()
    }
}