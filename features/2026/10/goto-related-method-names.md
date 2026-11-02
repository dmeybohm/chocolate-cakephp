# Go to related method names

## Context

"Go to related" from a template labels a controller action with the file name when the template is
linked by implicit rendering: `Controllers: PostsController.php` instead of
`Controllers: PostsController::viewAll()`. Navigation still reaches the right method.

An implicit render is stored as a `METHOD` reference, so the item's element is the `Method` itself.
`ViewToControllerGotoRelatedProvider.computeCustomName` calls
`PsiTreeUtil.getParentOfType(element, Method::class.java)`, which is strict and starts at the parent,
so it finds no method and falls back to the file name.

### Cake 2 camelCase actions

On every version, CakePHP underscores a template name that has no slash, so `viewAll` renders
`view_all` (`testCamelCaseActionRendersImplicitTemplate`). On Cake 2 the plugin keeps the name
as is in two places, so it links `viewAll` to `viewAll.ctp`:

- `fullImplicitViewPath()` in `ViewFileIndexService.kt` skips the conversion when
  `controllerInfo.isCakeTwo`, so the index keys an implicit render by the wrong name.
- The `CakeTwoTemplatesDir` branch of `addViewFilenameExtension()` in `CakeController.kt` ignores
  `convertCase`, so navigation from an action and view file creation use the wrong name.

`BothVersionsLayoutNavigationTest` pins the current behavior with a `viewAll.ctp` template.

A name passed to `render()` is underscored too, on every version: `render('viewAll')` renders
`view_all` (`testCamelCaseRenderNameRendersUnderscoredTemplate`). The plugin passes
`convertCase = false` for explicit names on every version, so it links `render('viewAll')` to
`viewAll`. That is not part of this plan yet.

## Plan

1. Pass `strict = false` to `getParentOfType` in `computeCustomName`.
2. Test the label of an implicit render on cake5, then mirror the test to the other versions.
3. Underscore camelCase action names on Cake 2:
   - Always convert in `fullImplicitViewPath()`, and drop its now unused `controllerInfo`
     parameter along with the `lookupControllerFileInfo()` call in `indexImplicitRender()`.
   - Apply `conditionalCamelCaseToUnderscore(convertCase)` in the `CakeTwoTemplatesDir` branch,
     as the Cake 3 and 4 branches already do.
   - Bump the `ViewFileIndex` version, since the indexed keys change.
4. Test the Cake 2 fix: an implicit render of `viewAll` links `view_all.ctp` to the action,
   `viewAll.ctp` is not linked, and navigating from the action opens `view_all.ctp`.
5. Rename the template in `BothVersionsLayoutNavigationTest` to `view_all.ctp` and tighten its
   assertion to expect `Controllers: PostsController::viewAll()`. Layout navigation references
   has merged, so this no longer waits on it.

## Implementation Progress

### Session #1

Merged `origin/main`, which brought in the assumption suite, and added
`testCamelCaseActionRendersImplicitTemplate`: an implicit render of `viewAll` uses the
`view_all` template on every version. Cake 2's `View::_getViewFileName()` underscores the name
too, so the plugin's Cake 2 handling, which keeps the action name as is
(`fullImplicitViewPath()` and `addViewFilenameExtension()`), links `viewAll` to `viewAll.ctp`
instead. Steps 3 to 5 now fix that on this branch.

Added `testCamelCaseRenderNameRendersUnderscoredTemplate`, which shows the same underscoring
for a name passed to `render()`, on every version.
