package com.daveme.chocolateCakePHP

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.PhpLangUtil
import com.jetbrains.php.lang.psi.elements.ClassConstantReference
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import com.jetbrains.php.lang.psi.stubs.indexes.PhpInheritanceIndex

private const val VIEW_HELPER_CAKE2_PARENT_CLASS = "\\AppHelper"
private const val VIEW_HELPER_CAKE3_PARENT_CLASS = "\\Cake\\View\\Helper"

private const val MODEL_CAKE2_PARENT_CLASS = "\\AppModel"
private const val MODEL_CAKE3_PARENT_CLASS = "\\Cake\\ORM\\Table"

private const val COMPONENT_CAKE2_PARENT_CLASS = "\\AppComponent"
private const val COMPONENT_CAKE3_PARENT_CLASS = "\\Cake\\Controller\\Component"

private val cake2HelperBlackList = hashSetOf(
    "Html5TestHelper",
    "OtherHelperHelper",
    "OptionEngineHelper",
    "PluggedHelperHelper",
    "HtmlAliasHelper",
    "TestHtmlHelper",
    "TestPluginAppHelper",
    "TimeHelperTestObject",
    "NumberHelperTestObject",
    "TextHelperTestObject"
)

private val cakeSkipRenderingMethods : HashSet<String> = listOf(
    "beforeFilter",
    "beforeRender",
    "beforeRedirect",
    "afterFilter",
    "afterRender",
    "__construct",
    "initialize",
    "components",
    "setPlugin",
    "getPlugin",
    "enableAutoRender",
    "invokeAction",
    "startupProcess",
    "shutdownProcess",
    "redirect",
    "setAction",
    "render",
    "viewClasses",
    "paginate",
    "isAction",
    "loadComponent",
    "setRequest",
).map { it.lowercase() }.toHashSet()

fun Method.isCustomizableViewMethod(): Boolean {
    return this.access.isPublic &&
                !cakeSkipRenderingMethods.contains(this.name.lowercase())
}

// Stub-compatible implementation using PhpInheritanceIndex with fallback to getDirectSubclasses
// Prefers PhpInheritanceIndex (stub-compatible) but falls back to getDirectSubclasses for test environments
private fun getAllSubclassesWithFallback(
    phpIndex: PhpIndex,
    project: Project,
    parentFqn: String,
    scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
): Collection<PhpClass> {
    val seen = hashSetOf<String>()
    val queue = ArrayDeque<String>()
    val out = mutableListOf<PhpClass>()
    queue += parentFqn

    while (queue.isNotEmpty()) {
        ProgressManager.checkCanceled()

        val fqn = queue.removeFirst()

        // Try PhpInheritanceIndex first (stub-compatible)
        // Try both with and without leading backslash as the index might store it differently
        val fqnVariants = setOf(fqn, fqn.removePrefix("\\"), "\\" + fqn.removePrefix("\\"))
        val directInheritors = mutableListOf<PhpClass>()

        for (fqnVariant in fqnVariants) {
            val inheritors = StubIndex.getElements(PhpInheritanceIndex.KEY, fqnVariant, project, scope, PhpClass::class.java)
            directInheritors.addAll(inheritors)
        }

        // Fall back to getDirectSubclasses if stub index returned nothing
        // This helps in test environments where stub indexes might not be fully available
        // Only call getDirectSubclasses when NOT in dumb mode (requires full PSI)
        if (directInheritors.isEmpty() && !DumbService.isDumb(project)) {
            directInheritors.addAll(phpIndex.getDirectSubclasses(fqn))
        }

        for (clazz in directInheritors) {
            ProgressManager.checkCanceled()

            val cfqn = clazz.fqn
            if (seen.add(cfqn)) {
                out += clazz
                queue += cfqn
            }
        }
    }
    return out
}

fun PhpIndex.getViewHelperSubclasses(project: Project, settings: Settings): Collection<PhpClass> {
    val result = mutableSetOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclassesWithFallback(this, project, VIEW_HELPER_CAKE2_PARENT_CLASS).filter {
            !cake2HelperBlackList.contains(it.name)
        }
    }
    if (settings.cake3Enabled) {
        result += getAllSubclassesWithFallback(this, project, VIEW_HELPER_CAKE3_PARENT_CLASS)
    }
    return result
}

fun PhpIndex.getModelSubclasses(project: Project, settings: Settings): Collection<PhpClass> {
    val result = mutableSetOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclassesWithFallback(this, project, MODEL_CAKE2_PARENT_CLASS)
    }
    if (settings.cake3Enabled) {
        result += getAllSubclassesWithFallback(this, project, MODEL_CAKE3_PARENT_CLASS)
    }
    return result
}

fun PhpIndex.getComponentSubclasses(project: Project, settings: Settings): Collection<PhpClass> {
    val result = mutableSetOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getAllSubclassesWithFallback(this, project, COMPONENT_CAKE2_PARENT_CLASS)
    }
    if (settings.cake3Enabled) {
        result += getAllSubclassesWithFallback(this, project, COMPONENT_CAKE3_PARENT_CLASS)
    }
    return result
}

fun PhpIndex.getAllAncestorTypesFromFQNs(classes: List<String>): List<PhpClass> {
    val result = ArrayList<PhpClass>()
    classes.map {
        val directClasses = getClassesByFQN(it)

        //
        // Add parent classes, traits, and interfaces:
        //
        val superClasses = ArrayList<PhpClass>()
        directClasses.map directSubclasses@  {
            val superClass = it.superClass ?: return@directSubclasses
            superClasses.add(superClass)
        }

        val interfaces = ArrayList<PhpClass>()
        directClasses.map {
            interfaces += it.implementedInterfaces
        }

        val traits = ArrayList<PhpClass>()
        directClasses.map {
            traits += it.traits
        }

        result += directClasses + superClasses + traits
    }

    return result
}

fun PhpIndex.viewHelperClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\${fieldName}Helper")
    }
    if (settings.cake3Enabled) {
        result += getClassesByFQN(
            "\\Cake\\View\\Helper\\${fieldName}Helper"
        )
        result += getClassesByFQN(
            "${settings.appNamespace}\\View\\Helper\\${fieldName}Helper"
        )
        for (pluginConfig in settings.pluginConfigs) {
            result += getClassesByFQN("${pluginConfig.namespace}\\View\\Helper\\${fieldName}Helper")
        }
    }
    return result
}

fun PhpIndex.componentAndModelClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> =
    this.componentFieldClassesFromFieldName(settings, fieldName) +
            this.modelFieldClassesFromFieldName(settings, fieldName)

fun PhpIndex.componentFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\${fieldName}Component")
    }

    if (settings.cake3Enabled) {
        result += getClassesByFQN(
                "\\Cake\\Controller\\Component\\${fieldName}Component"
        )
        result += getClassesByFQN(
                "${settings.appNamespace}\\Controller\\Component\\${fieldName}Component"
        )
        for (pluginConfig in settings.pluginConfigs) {
            result += getClassesByFQN("${pluginConfig.namespace}\\Controller\\Component\\${fieldName}Component")
        }
    }
    return result
}

fun PhpIndex.customFinderMethods(types: List<String>, customFinderName: String): Collection<Method> {
    val fullFinderMethodName = "find" + customFinderName
    return types.flatMap { type ->
        getClassesByFQN(type).mapNotNull { phpClass -> phpClass.findMethodByName(fullFinderMethodName) }
    }
}

fun PhpIndex.modelFieldClassesFromFieldName(settings: Settings, fieldName: String): Collection<PhpClass> {
    val result = arrayListOf<PhpClass>()
    if (settings.cake2Enabled) {
        result += getClassesByFQN("\\$fieldName")
    }
    if (settings.cake3Enabled) {
        result += getClassesByFQN(
                "${settings.appNamespace}\\Model\\Table\\$fieldName"
        )
        for (pluginConfig in settings.pluginConfigs) {
            result += getClassesByFQN("${pluginConfig.namespace}\\Model\\Table\\${fieldName}")
        }
    }
    return result
}

fun PhpIndex.phpClassesFromType(type: PhpType): Collection<PhpClass> {
    return type.types.asSequence()
        .map { klass ->
            this.getClassesByFQN(klass).asSequence()
        }
        .flatMap { it -> it }
        .toList()
}

fun Collection<PhpClass>.findFirstMethodWithName(methodName: String): Method? {
    return this.asSequence()
        .mapNotNull { it.findMethodByName(methodName) }
        .firstOrNull()
}

fun viewHelperTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
        result = result.add("\\${fieldName}Helper")
    }
    if (settings.cake3Enabled) {
        result = result.add("\\Cake\\View\\Helper\\${fieldName}Helper")
            .add("${settings.appNamespace}\\View\\Helper\\${fieldName}Helper")
        for (pluginConfig in settings.pluginConfigs) {
            result = result.add("${pluginConfig.namespace}\\View\\Helper\\${fieldName}Helper")
        }
    }
    return result
}

fun componentOrModelTypeFromFieldName(settings: Settings, fieldName: String): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
       result = result .add("\\" + fieldName)
            .add("\\" + fieldName + "Component")
    }
    if (settings.cake3Enabled) {
        // todo defer lookup to `complete()` in typeProviders and only use either
        //      the singular or plural, whichever is available.
        val pluralFieldName = Inflector.pluralize(fieldName)

        result = result.add("\\Cake\\Controller\\Component\\${fieldName}Component")
            .add("${settings.appNamespace}\\Controller\\Component\\${fieldName}Component")
            .add("${settings.appNamespace}\\Model\\Table\\${pluralFieldName}Table")
            .add("${settings.appNamespace}\\Model\\Table\\${fieldName}Table")
        for (pluginConfig in settings.pluginConfigs) {
            result = result.add("${pluginConfig.namespace}\\Controller\\Component\\${fieldName}Component")
        }
    }
    return result
}

fun controllerTypeFromControllerName(settings: Settings, controllerName: String): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
        result = result.add("\\${controllerName}Controller")
    }
    if (settings.cake3Enabled) {
        result = result.add("\\Cake\\Controller\\${controllerName}Controller")
            .add("${settings.appNamespace}\\Controller\\${controllerName}Controller")
        for (pluginConfig in settings.pluginConfigs) {
            result = result.add("${pluginConfig.namespace}\\Controller\\${controllerName}Controller")
        }
    }
    return result
}

fun viewType(settings: Settings): PhpType {
    var result = PhpType()
    if (settings.cake2Enabled) {
        result = result.add("\\AppView")
    }
    if (settings.cake3Enabled) {
        result = result.add("${settings.appNamespace}\\View\\AppView")
    }
    return result
}

fun findParentWithClass(element: PsiElement, clazz: Class<out PsiElement>): PsiElement? {
    var iterationElement = element
    while (true) {
        val parent = iterationElement.parent ?: break
        if (PlatformPatterns.psiElement(clazz).accepts(parent)) {
            return parent
        }
        iterationElement = parent
    }
    return null
}

// Get the literal string contents, or ::class reference.
fun PsiElement.getStringifiedClassOrNull(): String? = when (this) {
    is StringLiteralExpression -> this.contents.absoluteClassName()
    is ClassConstantReference -> this.getStaticClassRefOrNull()
    else -> null
}

// If the constant is ::class, resolve it, and otherwise null.
fun ClassConstantReference.getStaticClassRefOrNull(): String? {
    if (!PhpLangUtil.equalsClassConstantNames(this.name, "class")) {
        return null
    }
    val reference = this.classReference as? ClassReference ?: return null
    return reference.fqn
}

fun VirtualFile.findElementAt(project: Project, offset: Int): PsiElement? {
    val psiFile = PsiManager.getInstance(project).findFile(this) ?: return null
    return psiFile.findElementAt(offset)
}