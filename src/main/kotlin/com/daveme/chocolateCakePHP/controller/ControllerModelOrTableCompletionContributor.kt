package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.cake.isCakeTwoModel
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.codeInsight.completion.*
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable
import com.intellij.psi.util.PsiTreeUtil


class ControllerModelOrTableCompletionContributor : CompletionContributor() {

    init {
        val completionProvider = ControllerModelCompletionProvider()

        // When typing $this-><caret>
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .with(IsControllerPattern)
                )

        // When typing $this->Movie-><caret>
        val nestedPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .withFirstChild(
                                        PlatformPatterns.psiElement(FieldReference::class.java)
                                                .with(IsUpperCaseFieldRefPattern)
                                )
                )

        // When typing $this-><caret> inside a CakePHP 2 model
        val fieldOnVariablePattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(
                        PlatformPatterns.psiElement(FieldReference::class.java)
                                .withFirstChild(PlatformPatterns.psiElement(Variable::class.java))
                )
        val modelProvider = CakeTwoModelAssociationCompletionProvider()
        extend(CompletionType.BASIC, fieldOnVariablePattern, modelProvider)
        extend(CompletionType.SMART, fieldOnVariablePattern, modelProvider)

        extend(
            CompletionType.BASIC,
            fieldInControllerPattern,
            completionProvider
        )
        extend(
            CompletionType.SMART,
            fieldInControllerPattern,
            completionProvider
        )

        extend(
            CompletionType.BASIC,
            nestedPattern,
            completionProvider
        )
        extend(
            CompletionType.SMART,
            nestedPattern,
            completionProvider
        )
    }

    private class ControllerModelCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            completionParameters: CompletionParameters,
            processingContext: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val fieldReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                FieldReference::class.java
            ) ?: return

            if (DumbService.getInstance(fieldReference.project).isDumb) {
                return
            }

            val settings =
                Settings.getInstance(fieldReference.project)
            if (!settings.enabled) {
                return
            }

            val childElement = fieldReference.firstChild
            return if (childElement is FieldReference) {
                nestedLookup(settings, completionResultSet, childElement)
            } else {
                directLookup(settings, completionResultSet, fieldReference)
            }
        }

        private fun directLookup(
            settings: Settings,
            completionResultSet: CompletionResultSet,
            fieldReference: FieldReference,
        ) {
            val classReference = fieldReference.classReference ?: return
            if (classReference !is Variable) {
                return
            }
            val type = classReference.type.lookupCompleteType(classReference.project, null)

            val controllerClassNames = type.types.filter { it.isAnyControllerClass() }
            if (controllerClassNames.size > 0) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)
                val loaded = ConfiguredMemberService.loadedMembers(
                    fieldReference.project,
                    controllerClassNames,
                    setOf(MemberKind.TABLE)
                )
                completionResultSet.completeFromLoadedMembers(loaded.members, containingClasses)
                if (MemberKind.TABLE in loaded.initializeKinds) {
                    return
                }

                val modelSubclasses = phpIndex.getModelSubclasses(settings).filter {
                    it.name.removeFromEnd("Table", ignoreCase = true) !in loaded.members
                }
                completionResultSet.completeFromClasses(
                        modelSubclasses,
                        removeFromEnd = "Table",
                        containingClasses = containingClasses,
                )
            }
        }

        private fun nestedLookup(
            settings: Settings,
            completionResultSet: CompletionResultSet,
            fieldReferenceChild: FieldReference,
        ) {
            val project = fieldReferenceChild.project
            val phpIndex = PhpIndex.getInstance(project)
            val modelClasses = phpIndex.getModelSubclasses(settings)

            // Resolve the preceding $this->FieldReference by type, so an alias of a table works:
            val receiverTypes = fieldReferenceChild.type.lookupCompleteType(project, phpIndex, null).types
                .filter { it.startsWith("\\") }
            val tableTypes = receiverTypes
                .filter { it.isAnyTableClass() && phpIndex.getClassesByFQN(it).isNotEmpty() } +
                    receiverTypes.filter { settings.cake2Enabled && phpIndex.isCakeTwoModel(it) }

            // A receiver that does not resolve to a table or model, even by convention, has no
            // associations:
            if (tableTypes.isEmpty()) {
                return
            }
            val loaded = ConfiguredMemberService.loadedMembers(project, tableTypes, setOf(MemberKind.ASSOCIATION))
            completionResultSet.completeFromLoadedMembers(loaded.members)
            if (MemberKind.ASSOCIATION in loaded.initializeKinds) {
                return
            }
            val loadedAliases = loaded.members.keys
            completionResultSet.completeFromClasses(
                    modelClasses.filter { it.name.removeFromEnd("Table", ignoreCase = true) !in loadedAliases },
                    removeFromEnd = "Table"
            )
        }
    }

    private class CakeTwoModelAssociationCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(
            completionParameters: CompletionParameters,
            processingContext: ProcessingContext,
            completionResultSet: CompletionResultSet
        ) {
            val fieldReference = PsiTreeUtil.getParentOfType(
                completionParameters.position,
                FieldReference::class.java
            ) ?: return
            val variable = fieldReference.classReference as? Variable ?: return
            if (variable.name != "this") {
                return
            }
            val project = fieldReference.project
            if (DumbService.getInstance(project).isDumb) {
                return
            }
            val settings = Settings.getInstance(project)
            if (!settings.enabled || !settings.cake2Enabled) {
                return
            }
            val phpIndex = PhpIndex.getInstance(project)
            val models = variable.type.lookupCompleteType(project, phpIndex, null).types
                .filter { it.startsWith("\\") && phpIndex.isCakeTwoModel(it) }
            if (models.isEmpty()) {
                return
            }
            val loaded = ConfiguredMemberService.loadedMembers(project, models, setOf(MemberKind.ASSOCIATION))
            completionResultSet.completeFromLoadedMembers(loaded.members)
            if (MemberKind.ASSOCIATION in loaded.initializeKinds) {
                return
            }
            completionResultSet.completeFromClasses(
                phpIndex.getModelSubclasses(settings).filter { it.name !in loaded.members },
                removeFromEnd = "Table"
            )
        }
    }

}
