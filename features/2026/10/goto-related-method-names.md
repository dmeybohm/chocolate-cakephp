# Go to related method names

## Context

"Go to related" from a template labels a controller action with the file name when the template is
linked by implicit rendering: `Controllers: PostsController.php` instead of
`Controllers: PostsController::viewAll()`. Navigation still reaches the right method.

An implicit render is stored as a `METHOD` reference, so the item's element is the `Method` itself.
`ViewToControllerGotoRelatedProvider.computeCustomName` calls
`PsiTreeUtil.getParentOfType(element, Method::class.java)`, which is strict and starts at the parent,
so it finds no method and falls back to the file name.

## Plan

1. Pass `strict = false` to `getParentOfType` in `computeCustomName`.
2. Test the label of an implicit render on cake5, then mirror the test to the other versions.
3. Once [layout navigation references](layout-navigation-references.md) has merged, tighten the
   template assertion in `BothVersionsLayoutNavigationTest` to expect the method name.

## Implementation Progress

### Session #1

Merged `origin/main`, which brought in the assumption suite, and added
`testCamelCaseActionRendersImplicitTemplate`: an implicit render of `viewAll` uses the
`view_all` template on every version. Cake 2's `View::_getViewFileName()` underscores the name
too, so the plugin's Cake 2 handling, which keeps the action name as is
(`fullImplicitViewPath()` and `addViewFilenameExtension()`), links `viewAll` to `viewAll.ctp`
instead. That is a separate bug, left out of this branch. Until it is fixed, the Cake 2 mirror
of the label test should use a single word action, so it does not pin the wrong file name.
