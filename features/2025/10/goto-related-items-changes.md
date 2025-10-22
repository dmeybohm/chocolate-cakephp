Great—here’s a thin `RelatedItemLineMarkerProvider` that **wraps your existing resolver** so you keep your custom action/chooser, and also light up the built-in **Related Symbol** popup.

# 1) `plugin.xml`

```xml
<extensions defaultExtensionNs="com.intellij">
  <!-- Keep your existing action + (generic) LineMarkerProvider if you have one -->
  <codeInsight.lineMarkerProvider
      language="ANY"
      implementationClass="com.example.mvc.MvcRelatedLineMarkerProvider"/>
</extensions>
```

# 2) Provider (Kotlin)

```kotlin
package com.example.mvc

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.GotoRelatedItem
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList

class MvcRelatedLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // Only place the marker on “name-like” elements, to avoid icon spam
        val owner = element as? PsiNameIdentifierOwner ?: return
        if (owner.nameIdentifier !== element) return

        val file = element.containingFile ?: return
        if (DumbService.isDumb(file.project)) return  // avoid work during indexing

        // Quick precheck so we don’t do heavy work on unrelated files
        if (!MvcResolver.isController(file) && !MvcResolver.isView(file)) return

        // Use your existing resolution (controller <-> view, plus anything else)
        val targets: List<PsiFile> = MvcResolver.findRelated(file)
        if (targets.isEmpty()) return

        // Convert to GotoRelatedItems so:
        //  1) the gutter icon navigates,
        //  2) your items appear in Navigate → Related Symbol.
        val items = toRelatedItems(file, targets)

        val icon = pickIconFor(file) // controller vs view icons (see helper below)

        val info = NavigationGutterIconBuilder.create(icon)
            .setTargets(items.mapNotNull { it.element })
            .setTooltipText(if (MvcResolver.isController(file))
                "Go to related view(s)" else "Go to related controller")
            .createLineMarkerInfo(element)

        // Important: attach the GotoRelatedItems so Related Symbol can see them
        info.createGotoRelatedItemsProvider { items }

        result.add(info)
    }

    private fun toRelatedItems(source: PsiFile, targets: List<PsiFile>): MutableList<GotoRelatedItem> {
        val list = SmartList<GotoRelatedItem>()
        for (t in targets) {
            val (group, name, container, icon) = renderPresentation(source, t)
            list += object : GotoRelatedItem(t, group) {
                override fun getCustomName() = name
                override fun getCustomContainerName() = container
                override fun getCustomIcon() = icon
            }
        }
        return list
    }

    // --- Presentation helpers you can tailor to your framework ---

    private fun renderPresentation(source: PsiFile, target: PsiFile): ItemPresentation {
        val isTargetView = MvcResolver.isView(target)
        val group = if (isTargetView) "Views" else if (MvcResolver.isController(target)) "Controllers" else "Related"
        val vf = target.virtualFile
        val name = vf?.name ?: target.name
        val container = vf?.parent?.path?.substringAfterLast('/') ?: target.containingDirectory?.name ?: ""
        val icon = if (isTargetView) viewIcon() else controllerIcon()
        return ItemPresentation(group, name, container, icon)
    }

    private fun pickIconFor(file: PsiFile) =
        if (MvcResolver.isController(file)) controllerIcon() else viewIcon()

    private fun controllerIcon() =
        IconLoader.getIcon("/icons/controller.svg", javaClass) // or AllIcons.Nodes.Class

    private fun viewIcon() =
        IconLoader.getIcon("/icons/view.svg", javaClass) // or AllIcons.FileTypes.Html

    private data class ItemPresentation(
        val group: String,
        val name: String,
        val container: String,
        val icon: javax.swing.Icon
    )
}
```

# 3) How this plays with your current setup

* **Your custom action** stays exactly as-is (with your multi-match chooser and “true toggle” behavior).
* This provider **adds a gutter icon** on controllers/views and contributes items to **Navigate → Related Symbol** (Ctrl+Alt+Home / ⌃⌘↑).
* Users get both experiences: your dedicated toggle *and* the standard Related popup.

# 4) Drop-in hooks for your resolver

Assuming you already have something like:

```kotlin
object MvcResolver {
    fun isController(file: PsiFile): Boolean { /* … */ }
    fun isView(file: PsiFile): Boolean { /* … */ }
    fun findRelated(file: PsiFile): List<PsiFile> { /* controller ↔ view(s) */ }
}
```

…this provider just calls into it. If your resolver is heavier, consider caching with `CachedValuesManager` (keyed by the file + mod count) and still keep the **fast `isController/isView` precheck**.

# 5) Optional niceties

* **Order** the `targets` before converting to items (e.g., current action’s view first).
* **Scope**: if you only want the icon on class/method identifiers, keep the `PsiNameIdentifierOwner` guard (reduces visual noise).
* **Icons**: you can ship `controller.svg`/`view.svg` in `/resources/icons/` or use `AllIcons` constants.

If you share your resolver signatures (or your MVC conventions), I can wire this to them 1:1 and tweak the presentation names/paths.
