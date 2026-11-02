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
`viewAll`. The framework only underscores a name with no slash (`_getTemplateFileName()`, and
`_getViewFileName()` on Cake 2), so `render('Sub/viewAll')` is used as is, relative to the controller's template directory
(`Widgets/Sub/viewAll`). Explicit names
reach the plugin in two ways:

- The index keys `render()` calls, view builder calls and `view` field assignments through
  `fullExplicitViewPath()`. `setViewPath()` indexes element calls the same way, and elements are
  not underscored.
- Navigation from an action builds the explicit names' files in the "other" view paths of
  `allViewPathsFromController()`, with `convertCase = false`. Cell templates go through
  `CellGotoDeclarationHandler`, also with `convertCase = false`. `ActionName` splits a path into
  a prefix and a name, so it no longer says whether the name the framework saw had a slash.
- Go to Declaration on a `render()` name, a `view` assignment or a view builder call goes through
  `resolveTemplateViewPaths()` in `TemplateGotoDeclarationHandler`, which makes the name the
  default action name, where case conversion is on. So `render('viewAll')` already reaches
  `view_all` on 3, 4 and 5, but `render('Sub/viewAll')` is converted to `Sub/view_all` too.
  On Cake 2 nothing is converted until step 3, which would then convert both.
- `indexCellReferences()` keys cell templates separately from `fullExplicitViewPath()`. It
  underscores the implicit cell action, but keys an explicit `render('viewAll')` as `viewAll`,
  so "go to related" from a cell's `view_all` misses it.

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
6. Before changing explicit names, add assumption tests for what this plan relies on but has not
   pinned:
   - A name with a slash, `render('Sub/viewAll')`, is used as is.
   - `setTemplate('viewAll')` is underscored, alone and after `setTemplatePath()` (3, 4, 5).
   - `$this->view = 'viewAll'` is underscored (2).
   - A cell's `render('viewAll')` is underscored (3, 4, 5).
   - A camelCase element or layout name is not underscored.
7. Underscore explicit template names that have no slash, adjusting to what step 6 finds:
   - In the index, convert the name of template references before `fullExplicitViewPath()`,
     leaving element calls alone.
   - In navigation, convert the "other" view paths whose name had no slash. Keep that fact on
     `ActionName`, or convert before the path is split.
   - Do the same for cell templates in `CellGotoDeclarationHandler`.
   - In `TemplateGotoDeclarationHandler`, convert only a name with no slash, in both the
     `NoPlugin` and `PluginFound` branches of `resolveTemplateViewPaths()`. A view builder name
     joined with its `setTemplatePath()` must still be converted, so decide from the
     `setTemplate()` name, not the joined path.
   - In `indexCellReferences()`, underscore explicit cell template names that have no slash.
   - The `ViewFileIndex` version bump from step 3 covers the index change.
8. Test on cake5, then mirror to the other versions: `render('viewAll')` links `view_all` to the
   action and navigates to it, `render('Sub/viewAll')` still links `Widgets/Sub/viewAll`, and a
   camelCase element name is unchanged. Also test:
   - Go to Declaration on `render('viewAll')` opens `view_all`, on `render('Sub/viewAll')`
     opens `Sub/viewAll`, and on `setTemplatePath('Gadgets')->setTemplate('viewAll')` opens
     `Gadgets/view_all`.
   - "Go to related" from a cell's `view_all` lists the `cell()->render('viewAll')` call.

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

Added the step 6 assumption tests. Every one matched the plan's expectation, so step 7 needs no
change:

- `testRenderNameWithSlashIsNotUnderscored`: `render('Sub/viewAll')` renders
  `Widgets/Sub/viewAll` on every version.
- `testBuilderSetTemplateNameIsUnderscored` and
  `testBuilderSetTemplateAfterSetTemplatePathIsUnderscored`: `setTemplate('viewAll')` renders
  `view_all`, in the directory `setTemplatePath()` names (3, 4, 5). The plugin joins the two into
  `/Gadgets/viewAll`, so step 7 has to underscore the name passed to `setTemplate()` before
  joining, not decide from the joined path.
- `testViewPropertyNameIsUnderscored`: `$this->view = 'viewAll'` renders `view_all` (2).
- `testCellRenderNameIsUnderscored`: a cell's `render('viewAll')` renders `view_all` (3, 4, 5).
- `testCamelCaseElementNameIsNotUnderscored` and `testCamelCaseLayoutNameIsNotUnderscored`:
  element and layout names are used as is on every version.

### Session #2

Implemented steps 1 to 8.

- Steps 1 and 2: `computeCustomName` finds the method with `strict = false`, so an implicit
  render is labelled `Controllers: WidgetsController::viewAll()`. Tested on every version in a
  new `TemplateNameCaseTest`.
- Steps 3 to 5: Cake 2 underscores implicit action names, in the index and in navigation from
  an action. `BothVersionsLayoutNavigationTest` now uses `view_all.ctp` and expects the method
  name.
- Step 7: `renderedTemplateName()` in `CakeController.kt` gives the name CakePHP looks up: the
  template part of a name with no slash is underscored, after any plugin prefix, and a name with
  a slash is used as written. Each explicit name is converted once, where it is read:
  - In the index: `render()` calls, `view` assignments, `setTemplate()` names before they are
    joined with `setTemplatePath()`, and explicit cell templates. Element calls are left alone.
  - In navigation: the explicit names of `actionNamesFromControllerMethod()` and the single
    call builders, `setTemplate()` names in `findViewBuilderCalls()`, Go to Declaration and
    cell Go to Declaration.
  - Where an explicit name takes the default action name's place, `allViewPathsFromController()`
    and `allViewPathsFromPluginTemplate()` take `convertDefaultCase = false`, so the name is not
    converted a second time. That covers `TemplateGotoDeclarationHandler` and the line markers on
    a `render()` call, a `setTemplate()` call and a `view` assignment, which had the same slash
    bug as Go to Declaration.
- The `ViewFileIndex` version is 27, not the 26 from step 3, since a build of the step 3 commit
  would already hold a version 26 index without the explicit name keys.
- Step 8: `TemplateNameCaseTest` covers "go to related", the method line marker and Go to
  Declaration for each form on each version where it exists, and a camelCase element name kept
  as written. On Cake 5, six of its eight tests fail without step 7.

The full Gradle suite passes: 1766 tests.
