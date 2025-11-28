package com.daveme.chocolateCakePHP.navigation

import com.daveme.chocolateCakePHP.cake.CakeIcons
import com.intellij.navigation.GotoRelatedItem
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIcons
import javax.swing.Icon

/**
 * Simple GotoRelatedItem wrapper for CakePHP navigation.
 *
 * This class follows the pattern used by the Symfony plugin (PopupGotoRelatedItem),
 * pre-computing all display data to avoid EDT threading violations.
 *
 * @param element The PSI element to navigate to
 * @param group The group name for the Related Symbol popup (e.g., "Controllers", "Views")
 * @param customName Pre-computed name to display
 * @param containerName Pre-computed container name to display
 * @param iconPath Pre-computed file path for icon selection
 */
class CakeGotoRelatedItem(
    element: PsiElement,
    group: String,
    private val customName: String,
    private val containerName: String?,
    private val iconPath: String?
) : GotoRelatedItem(element, group) {

    override fun getCustomName(): String = customName

    override fun getCustomContainerName(): String? = containerName

    override fun getCustomIcon(): Icon {
        return if (iconPath == null) {
            PhpIcons.FUNCTION
        } else if (iconPath.contains("/Controller/")) {
            CakeIcons.LOGO_SVG
        } else {
            PhpIcons.PHP_FILE
        }
    }
}
