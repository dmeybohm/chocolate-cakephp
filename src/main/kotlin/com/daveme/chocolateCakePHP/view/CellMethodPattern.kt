package com.daveme.chocolateCakePHP.view

import com.daveme.chocolateCakePHP.cake.CELL_TRAIT
import com.intellij.patterns.PatternCondition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.PhpClass
import com.jetbrains.php.lang.psi.elements.Variable

private const val VIEW_CLASS = "\\Cake\\View\\View"

object CellMethodPattern :
    PatternCondition<MethodReference>("CellMethodPattern") {

    // $this->cell('Inventory') in a template or a class with CellTrait
    // $this->_View->cell('Inventory') in a helper
    override fun accepts(
        methodReference: MethodReference,
        context: ProcessingContext
    ): Boolean {
        if (!methodReference.name.equals("cell", ignoreCase = true)) {
            return false
        }
        return when (val receiver = methodReference.firstChild) {
            is Variable -> receiver.name == "this" && thisHasCellTrait(methodReference)
            is FieldReference -> receiver.name == "_View" &&
                (receiver.firstChild as? Variable)?.name == "this"
            else -> false
        }
    }

    private fun thisHasCellTrait(methodReference: MethodReference): Boolean {
        val phpClass = PsiTreeUtil.getParentOfType(methodReference, PhpClass::class.java)
            ?: return true
        val visited = HashSet<String>()
        var current: PhpClass? = phpClass
        while (current != null && visited.add(current.fqn)) {
            if (current.fqn.equals(VIEW_CLASS, ignoreCase = true)) {
                return true
            }
            if (current.traits.any { it.fqn.equals(CELL_TRAIT, ignoreCase = true) }) {
                return true
            }
            current = current.superClass
        }
        return false
    }

}
