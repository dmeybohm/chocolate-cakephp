package com.daveme.chocolateCakePHP.controller

import com.daveme.chocolateCakePHP.*
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberService
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.intellij.codeInsight.completion.*
import com.intellij.openapi.project.DumbService
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Variable

class ControllerComponentCompletionContributor : CompletionContributor() {
    init {
        // When typing $this-><caret>
        val fieldInControllerPattern = PlatformPatterns.psiElement(LeafPsiElement::class.java)
            .withParent(
                PlatformPatterns.psiElement(FieldReference::class.java)
                    .with(IsControllerPattern)
            )

        extend(
            CompletionType.BASIC,
            fieldInControllerPattern,
            ControllerCompletionProvider()
        )
        extend(
            CompletionType.SMART,
            fieldInControllerPattern,
            ControllerCompletionProvider()
        )
    }

    private class ControllerCompletionProvider : CompletionProvider<CompletionParameters>() {

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

            directLookup(settings, completionResultSet, fieldReference)
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
            if (controllerClassNames.isNotEmpty()) {
                val phpIndex = PhpIndex.getInstance(fieldReference.project)
                val containingClasses = phpIndex.getAllAncestorTypesFromFQNs(controllerClassNames)

                val loaded = ConfiguredMemberService.loadedMembers(
                    fieldReference.project,
                    controllerClassNames,
                    setOf(MemberKind.COMPONENT)
                )
                completionResultSet.completeFromLoadedMembers(loaded.members, containingClasses)
                if (MemberKind.COMPONENT in loaded.initializeKinds) {
                    return
                }

                val componentSubclasses = phpIndex.getComponentSubclasses(settings).filter {
                    it.name.removeFromEnd("Component", ignoreCase = true) !in loaded.members
                }
                completionResultSet.completeFromClasses(
                    componentSubclasses,
                    removeFromEnd = "Component",
                    containingClasses = containingClasses
                )
            }
        }
    }

}