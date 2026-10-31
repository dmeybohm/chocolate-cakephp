package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.isAnyCellClass
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.HELPERS_PROPERTY
import com.daveme.chocolateCakePHP.configuredmemberindex.LoadedMembers
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberTypeResult
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.php.PhpIndex

/**
 * The classes whose configuration supplies the helpers of a view file.
 */
object ViewHelperReceivers {

    private val helpersProperty = setOf(HELPERS_PROPERTY)

    private val RENDERING_ALIASES_KEY = Key.create<CachedValue<Set<String>>>(
        "com.daveme.chocolateCakePHP.view.ViewHelperReceivers.renderingAliases"
    )

    /**
     * The helper aliases any CakePHP 3 to 5 controller or cell configures.
     */
    fun aliasesOfRenderingClasses(project: Project): Set<String> {
        val tracker = project.getService(PhpFilesModificationTracker::class.java)
        tracker.ensureArmed()
        return CachedValuesManager.getManager(project).getCachedValue(
            project,
            RENDERING_ALIASES_KEY,
            {
                val phpIndex = PhpIndex.getInstance(project)
                val classes = listOf("\\Cake\\Controller\\Controller", "\\Cake\\View\\Cell")
                    .flatMap { phpIndex.getAllSubclasses(it) }
                    .map { it.fqn }
                    .distinct()
                val aliases = ConfiguredMemberService.loadedMembers(project, classes, setOf(MemberKind.HELPER)).members.keys
                CachedValueProvider.Result.create(aliases, tracker)
            },
            false
        )
    }

    /**
     * The receivers of [viewFile]: its structure's view classes, and the controllers and cells
     * that render it when the structure uses them.
     */
    fun of(project: Project, viewFile: ViewFile): List<String> {
        val settings = Settings.getInstance(project)
        val structure = viewFile.structure
        if (!structure.usesRenderingControllers) {
            return structure.viewClassReceivers(settings)
        }
        val controllers = RenderingControllers.of(project, viewFile).ifEmpty { structure.fallbackReceivers }
        return structure.viewClassReceivers(settings) + controllers
    }

    /**
     * The helpers the receivers of [viewFile] load.
     */
    fun loadedHelpers(project: Project, viewFile: ViewFile): LoadedMembers {
        val loaded = ConfiguredMemberService.loadedMembers(
            project, of(project, viewFile), setOf(MemberKind.HELPER), ignoredProperties(viewFile)
        )
        return loaded.copy(members = loaded.members.mapValues { (alias, member) ->
            helperMemberType(project, viewFile, alias) ?: member
        })
    }

    /**
     * The configured type of helper [fieldName] in [viewFile]. A view loads any helper by name, so
     * the result only determines the type when the view class or every class rendering the view
     * loads it, or when the view is a cell's template and its cells or its callers load it.
     */
    fun helperMemberType(project: Project, viewFile: ViewFile, fieldName: String): MemberTypeResult? {
        val settings = Settings.getInstance(project)
        val viewClasses = viewFile.structure.viewClassReceivers(settings)
        val ignored = ignoredProperties(viewFile)
        val viewClassType = helperType(project, viewClasses, fieldName, ignored)
        // View::initialize() runs after the controller's helpers reach the view, so its load wins:
        if (viewClassType != null && viewClassType.hasInitializeLoad) {
            return viewClassType
        }
        val perReceiver = (of(project, viewFile) - viewClasses.toSet()).associateWith {
            helperType(project, listOf(it), fieldName, ignored)
        }
        val cells = perReceiver.filterKeys { it.isAnyCellClass() }
        val controllers = (perReceiver - cells.keys).values
        val isCellTemplate = viewFile.structure.isCellKey(viewFile.templateKey)
        val callersDetermine = isCellTemplate && controllers.isNotEmpty() && controllers.all { it.determinesHelper() }
        // The caller's helpers reach a cell's builder after its initialize() has run, so they win:
        val cellFqns = if (callersDetermine)
            cells.keys.flatMap { cell ->
                ConfiguredMemberService.memberTypeOutsideInitialize(
                    project, cell, setOf(MemberKind.HELPER), fieldName, ignored(cell)
                )?.classFqns.orEmpty()
            }
        else
            cells.values.flatMap { it?.classFqns.orEmpty() }
        val fqns = viewClassType?.classFqns.orEmpty() +
                controllers.flatMap { it?.classFqns.orEmpty() } + cellFqns
        if (fqns.isEmpty()) {
            return null
        }
        val isDeterminedByCells = isCellTemplate && cells.isNotEmpty() && cells.values.all { it.determinesHelper() }
        val phpIndex = PhpIndex.getInstance(project)
        return MemberTypeResult(
            classFqns = fqns,
            classes = fqns.flatMap { phpIndex.getClassesByFQN(it) },
            hasInitializeLoad = callersDetermine || isDeterminedByCells ||
                    (perReceiver.isNotEmpty() && perReceiver.values.all { it.determinesHelper() }),
        )
    }

    private fun MemberTypeResult?.determinesHelper(): Boolean = this != null && hasInitializeLoad

    private fun helperType(
        project: Project,
        receivers: List<String>,
        fieldName: String,
        ignoredProperties: (String) -> Set<String>
    ): MemberTypeResult? =
        ConfiguredMemberService.memberType(project, receivers, setOf(MemberKind.HELPER), fieldName, ignoredProperties)
            ?.takeIf { it.classFqns.isNotEmpty() }

    private fun ignoredProperties(viewFile: ViewFile): (String) -> Set<String> {
        val ignored = if (viewFile.structure.readsHelpersProperty) emptySet() else helpersProperty
        return { ignored }
    }
}
