# Cell support

## Context

Feature 5 in `features/ideas.md`. CakePHP 3, 4 and 5 cells are mini-controllers under
`src/View/Cell/` that render their own templates. The plugin knows nothing about them today:
`$this->cell('Inventory')` in a template is an opaque string, a cell's `display()` method gets no
template gutter marker, a cell template gets no view variable types from the cell's `set()` calls,
and "go to controller" from a cell template finds nothing.

CakePHP 2 has no cells, so everything here is gated on `settings.cake3Enabled`.

## How CakePHP resolves a cell

From `Cake\View\CellTrait` and `Cake\View\Cell` (checked against 3.x, 4.4 and 5.0 sources):

- `cell()` lives in `CellTrait`. `View` uses it, so templates, elements and layouts have
  `$this->cell()` and helpers have `$this->_View->cell()`. Controllers do not use the trait
  themselves, but the 3.x and 5.x docs show adding `use CellTrait;` to a controller, so
  `$this->cell()` in a controller is a supported call too. The method pattern accepts
  `$this->cell(...)` when there is no enclosing class (a template file) or the enclosing class
  is a `Cake\View\View` subclass or uses `Cake\View\CellTrait`, directly or through a parent,
  and `$this->_View->cell(...)` in a helper. An unrelated class with its own `cell()` method
  does not match: PhpStorm resolves that call natively and the handler adds nothing.
- The caller can pick the template on the returned cell: `$this->cell('Inventory')->render('compact')`
  or `$cell = $this->cell('Inventory'); echo $cell->render('compact');`. `render()` runs the
  action and then uses the given template under the cell's template path.
- The string is `[Plugin.]Name[::action]`. `pluginSplit()` takes the plugin, `App::className()`
  resolves `Name` to `App\View\Cell\NameCell` (or `Plugin\View\Cell\NameCell`), and the action
  defaults to `display`. `Name` may contain a sub-namespace: `Admin/Inventory` resolves to
  `App\View\Cell\Admin\InventoryCell`.
- The template is `Inflector::underscore($action)` under a path derived from the class name
  after `\View\Cell\`, with namespace separators turned into directories:
  - Cake 4/5: `templates/cell/Admin/Inventory/display.php` (`Cell::TEMPLATE_FOLDER` is `cell`)
  - Cake 3: `src/Template/Cell/Admin/Inventory/display.ctp`
- Inside the action the cell may call `$this->viewBuilder()->setTemplate()` /
  `setTemplatePath()`. The path prefix is only applied when the builder has no template path,
  so an action-level `setTemplatePath()` wins, as it does in controllers. `$this->render()`
  from inside the action is not a valid call: `Cell::render()` invokes the action itself, so
  that would run the action again. The caller picks an alternate template with
  `Cell::render($template)` on the returned cell, as above.
- Cells use `ViewVarsTrait`, so `$this->set()` in an action feeds the cell template. For
  tables: Cake 3 cells load them with `$this->loadModel()` (`ModelAwareTrait`); Cake 4 keeps
  `loadModel()` and adds `$this->fetchTable()` from 4.3; Cake 5 has only `fetchTable()`. They
  have no component registry.
- A cell template renders in its own `View` with only the cell's `set()` variables; the
  calling template's variables do not reach it.

## Scope

In the order to implement them, each with cake5 tests first and then cake4 and cake3:

### 1. Navigation and completion for the `cell()` string

A `CellGotoDeclarationHandler` on the first argument of `$this->cell(...)` (and
`$this->_View->cell(...)` in helpers) that goes to the cell class, or to the action method when
the string has a `::action` part and the method exists. A `CellCompletionContributor` that
offers every `*Cell` subclass of `Cake\View\Cell` in the app and configured plugin namespaces
(plugin cells prefixed `Plugin.`), and after `::` the public methods of the resolved cell.

The method pattern mirrors `ElementMethodPattern`; `templateArgumentFromLiteral` already
handles the ternary / match / parenthesised cases so the click resolves on the branch that was
clicked.

The chained `$this->cell('Inventory')->render('compact')` form gets a goto declaration on the
`render()` argument that resolves to `cell/Inventory/compact.php`: the receiver is a `cell()`
`MethodReference` whose first argument names the cell, so the cell path is known structurally.
`RenderMethodPattern` only matches `$this->render()`, so this does not collide with
`TemplateGotoDeclarationHandler`. The `$cell = $this->cell(...); $cell->render('compact')` form
resolves the variable to its preceding `cell()` assignment in the same scope, using the same
preceding-assignment rule `templateNamesFromVariable` applies; anything else (parameters,
properties, a cell built in another method) is not resolved.

Caller-side references, both `cell('Inventory')` (the default `display` template) and the
`render('compact')` forms, are indexed in `ViewFileDataIndexer` under the cell template path so
the template side can find its usages. They are not variable sources, though:
`forEachContributingSource` in `ViewVariableIndexService` treats every reference to a template
as a `ViewSet` source and expands it further, which would hand the calling template's variables
to the cell template. So these references get their own `ElementType.CELL_RENDER`,
`PsiElementAndPath` carries the element type through from `ViewReferenceData`, and the variable
walk skips `CELL_RENDER` references without expanding them. The cell template's variables come
only from the cell action's `set()` calls (step 3).

### 2. Cell action to template

A cell file is `{Name}Cell.php` under a `View/Cell` directory. `ControllerMethodLineMarker`
works from a `ControllerPath` (name + prefix, giving `Admin/Movie/`); the cell equivalent is a
path of `cell/Admin/Inventory/` on Cake 4/5 and `Cell/Admin/Inventory/` on Cake 3. The plan is
to generalise the marker's `RelatedLookupInfo` from `ControllerPath` to a small sealed source
type (controller or cell) rather than copying the marker, so `setTemplate()` inside a cell
action, alone or at the end of a `setTemplatePath()->setTemplate()` chain, gets the same marker
as inside a controller action for free. As in controllers, a standalone `setTemplatePath()`
gets no marker of its own; it only sets the prefix of the `setTemplate()` calls after it.
`actionNamesFromControllerMethod` is already source-agnostic; it also collects
`$this->render()` calls, which do not occur in a valid cell action, so nothing is done about
them either way.

`CreateViewFileAction` (Ctrl-click on the marker) should create the cell template in the right
directory too.

`ToggleBetweenControllerAndViewAction` checks `isCakeControllerFile` explicitly in both
`update()` and `actionPerformed()`, so it needs its own `isCakeCellFile` branch: enabled in a
cell file, and `tryToNavigateToView` built on the same controller-or-cell source as the marker.
The template-to-cell direction comes from the index (step 3); the cell-to-template direction
does not.

### 3. Index cells like controllers

Both file indexes gate on `isCakeControllerFile`, which is a filename-suffix check:

- `ViewFileDataIndexer` indexes a controller's public methods as implicit renders keyed by
  template path (`Movie/artist`), which drives `ViewToControllerGotoRelatedProvider` and the
  toggle action.
- `ViewVariableASTDataIndexer` indexes a controller action's `$this->set()` calls under its
  `ControllerPath`, which drives view variable completion, types and the undefined-variable
  suppressor in the template.

Add `isCakeCellFile` (suffix `Cell`, parent chain contains `View/Cell`) and a cell path
function, and run the same indexing for cells with the cell path prefix. The prefix needs the
cell directory name, which differs by version (`cell` vs `Cell`), and an indexer only has the
file; resolve it the way `elementPathPrefixFromSourceFile` does, by looking for the actual
`cell`/`Cell` directory under the templates directory the cell's `src` belongs to, falling back
to `cell`. Skip the lifecycle methods (`initialize`, `beforeRender`, ...) as the controller
indexing does. Cell templates then get their variables and the "go to controller" lookups
from the template side resolve to the cell action without further changes, since both look up
by template path.

The `ViewVariableIndexService` walks `ControllerAction` sources by checking
`isAnyControllerClass()` on the file name; the cell path needs an equivalent branch there.
Elements rendered from a cell template inherit its variables through the existing element
flow.

### 4. Later, if wanted

- `$_validCellOptions` key completion in the third argument of `cell()`.
- Cell `initialize()` as a configured-member source (feature 6) once cells can load anything
  worth indexing; today only `fetchTable()` and helpers via the view builder apply.
- `$this->cell()` return type (`Cake\View\Cell`) is already correct from the stub; the
  specific cell subclass as the type is possible from the string argument but has little use.

## Fixtures and stubs

- `cake5/src5/View/Cell/InventoryCell.php` with `display()` (sets `$items`) and `compact()`,
  `cake5/src5/View/Cell/Admin/ReportCell.php` for the sub-namespace case, and
  `cake5/vendor/test/test_plugin/src/View/Cell/WidgetCell.php` for the plugin case.
- Templates `cake5/templates/cell/Inventory/display.php`, `compact.php`,
  `cake5/templates/cell/Admin/Report/display.php` and the plugin's
  `templates/cell/Widget/display.php`.
- `cake5/vendor/cakephp.php` gains `Cake\View\Cell` (with `ViewVarsTrait`, `render()`) and a
  `cell()` method on `View` so the real-vendor mode (`-DchocolateCakePHP.vendor=real`) and the
  stub mode agree. Same for the cake4 and cake3 stubs when those tests are written.

## Tests (cake5 first)

- `CellGotoDeclarationTest`: plain name to class, `Name::action` to method, plugin-prefixed
  name, sub-namespace name, the same call from a controller that uses `CellTrait`, from a
  helper via `$this->_View->cell()`, no navigation for `$obj->cell()` or for `$this->cell()`
  in a class that has its own `cell()` and no trait, ternary branches.
- `CellRenderGotoDeclarationTest`: `$this->cell('Inventory')->render('compact')` goes to
  `cell/Inventory/compact.php`; the `$cell = ...; $cell->render('compact')` form too; a
  `render()` on an unrelated receiver does not navigate.
- `CellCompletionTest`: cell names offered, plugin names prefixed, actions offered after `::`.
- `CellMethodLineMarkerTest` (or extend the existing controller marker test): marker on
  `display()` targets the template, a `setTemplate()` inside the action gets its own marker,
  and a `setTemplatePath('cell/Other')->setTemplate('x')` chain marks `cell/Other/x.php`,
  as in a controller.
- `CellToggleActionTest`: the toggle action is enabled in a cell file and goes from `display()`
  to its template, and back from the template.
- `CellViewVariableTest`: `$items` completes with its type in the cell template; the
  undefined-variable suppressor accepts it; a variable set only in the template that calls
  `$this->cell('Inventory')` is not offered in the cell template.
- `CellTemplateToCellGotoRelatedTest`: from `cell/Inventory/display.php` to `display()`.

## Implementation Progress

### Session #1

Created the branch and worktree, surveyed the controller/template machinery and the
CakePHP 3/4/5 cell sources, and wrote this plan.

### Session #2

Step 1 is done for cake5:

- `CellMethodPattern` accepts `$this->cell()` with no enclosing class, in a `Cake\View\View`
  subclass or in a class using `Cake\View\CellTrait` (walking the parent chain), and
  `$this->_View->cell()`. `cellCallsFromReceiver` resolves a `render()` receiver to the
  `cell()` call itself or to the `cell()` calls assigned to the variable before it.
- `cake/CakeCell.kt` holds the `[Plugin.]Name[::action]` parsing (`parseCellName`), the class
  FQN, the cell template directory name per `TemplatesDir` (`cell` / `Cell`) and
  `allViewPathsFromCellTemplate`. An unknown plugin prefix falls back to the plugin name as
  the namespace, as `App::className()` does.
- `CellGotoDeclarationHandler` covers both the `cell()` string (class, or the action method
  when it exists) and the `render()` argument on a cell (the template under the cell's
  directory). `CellCompletionContributor` matches against the whole literal up to the caret,
  since `.`, `/` and `::` break the default prefix; after `::` it offers the cell's own public
  methods as `Name::method`.
- The preceding-assignment scan in `templateNamesFromVariable` is now `precedingAssignments`,
  shared with `cellCallsFromReceiver`; `TemplateNames.kt` gained the AST equivalent
  `assignedValueNodes` for the indexer.
- `ViewFileDataIndexer` indexes `cell()` under `{cell dir}/{Name}/{underscored action}` and
  `render()` on a cell under `{cell dir}/{Name}/{template}` as `ElementType.CELL_RENDER`, with
  the method name's offset so the reference resolves to that exact call. A `cell()` that is
  directly the receiver of `render()` is indexed only under the rendered template; one
  assigned to a variable is indexed under its default template as well. The index version
  went to 20. `cellPathPrefixFromSourceFile` finds the cell directory from the caller's
  templates directory (or the one next to its `src`), defaulting to `Cell` beside a `Template`
  directory and `cell` otherwise.
- `PsiElementAndPath` carries the `ElementType`, and `forEachContributingSource` drops
  `CELL_RENDER` references before enqueueing them. The test for this fails without the skip
  (the calling controller's `$metadata` reaches the cell template).

Known limits: plugin cells are indexed without the plugin, so `cell/Widget/display` cannot
tell an app `Widget` cell from a plugin one; the indexer cannot check for `CellTrait`, so an
unrelated class's own `$this->cell()` is indexed too. `ViewToControllerGotoRelatedProvider`
lists the callers of a cell template under "Controllers" for now.

Tests: `CellGotoDeclarationTest`, `CellRenderGotoDeclarationTest`, `CellCompletionTest`,
`CellReferenceIndexTest` (cake5). Full suite passes (989 tests).

### Session #3

Steps 2 and 3 are done, with cake4 and cake3 coverage for all three steps.

- `ViewOwnerPath` is the sealed controller-or-cell type the plan called for: `ControllerPath`
  and the new `CellPath` (`cell/Admin/Inventory/`) implement it, and
  `allViewPathsFromController` / `viewPathsFromControllerNameAndActionName` take it, since
  they only ever used `viewPath`. Cells get no data view paths. `RelatedLookupInfo` holds an
  `ownerPath` built by `viewOwnerPathFromFile`, so the controller marker, its `setTemplate()`
  markers, `setTemplatePath()->setTemplate()` chains and the create-file popup all work in a
  cell without further changes.
- `isCakeCellFile` / `cellNameFromPath` take the name from the path after the last `View/Cell`.
  `cellDirNameFromSourceFile` (moved from the view file index into `CakeCell.kt`) gives the
  cell path its `cell` / `Cell` directory.
- `ToggleBetweenControllerAndViewAction` is enabled in a cell file and builds the cell-to-template
  lookup from the same owner path. From a template it drops `CELL_RENDER` references, so a cell
  template toggles to its cell action and not to the templates calling it. The template-to-cell
  direction runs as a non-blocking read action and has no test of its own; the goto-related
  test covers the same index lookup.
- `ViewFileDataIndexer` treats a cell file like a controller: public actions are implicit
  renders and `viewPathPrefixFromSourceFile` returns the cell path, so `setTemplate()` and
  `render()` inside a cell are keyed under it. Before this, a cell's `setTemplate('x')` was
  keyed as `Cell/x`, because the walk up from `src/View/Cell` stopped at `View`.
- `ViewVariableASTDataIndexer` indexes a cell action's `set()` calls under
  `cellActionKey(name, method)` (`cell:Admin/Inventory:display`); the controller indexing became
  `indexActions` with a key function. `forEachContributingSource` turns a reference from a
  cell file into a `ControllerAction` source with that key. Both index versions were bumped.
- `ViewToControllerGotoRelatedProvider` groups a cell template's references as "Cells" (the
  action) and "Cell usages" (`cell()` / `render()` callers), and controllers as before.
- The toggle action is enabled but not visible outside view files, so `testAction()` does not
  perform it; `CellToggleActionTest` builds the event and calls `actionPerformed` itself.

Tests: `CellMethodLineMarkerTest`, `CellToggleActionTest`, `CellViewVariableTest`,
`CellTemplateToCellGotoRelatedTest` (cake5), and `CellTest` in cake4 and cake3 covering the
`cell()` goto, `render()` goto, the action marker, cell variables (including that caller
variables are kept out) and template-to-cell navigation. The cake4 and cake3 stubs gained
`CellTrait`, `Cell` and `Helper::$_View`.

### Session #4

Step 4 was dropped: `$_validCellOptions` completion is a rarely used corner, cells seldom load
anything in `initialize()`, and typing `cell()` as the specific subclass mostly helps `render()`,
which already works. What a cell does most is fetch a table in its action, and that was
missing: `fetchTable()` was only recognised on controllers, so in a cell the call had no
argument completion, no navigation and only the base `Table` type. `String.isAnyCellClass()` /
`PhpType.isProbablyCellClass()` now sit next to the controller checks in
`TableLocatorMethodPattern`, `TableLocatorCompletionContributor`,
`TableLocatorGotoDeclarationHandler` and `TableLocatorTypeProvider`. `loadModel()` gets no
table-specific return type in controllers either, so nothing was done for it in cells. The
cake5 and cake4 stub `Cell` gained `LocatorAwareTrait`, as the real one has from 4.3.

Plugin cells now have templates in the fixtures for all three versions, with tests for the
`render()` goto and the action marker into the plugin's templates. The cake4 and cake3
`CellTest` gained name completion (including plugin names), action completion, the plugin
cell goto and the plugin `render()` goto, and cake4 a `fetchTable()` case.

Tests: `CellFetchTableTest` (cake5), additions to `CellRenderGotoDeclarationTest`,
`CellMethodLineMarkerTest` and both `CellTest` classes.

### Session #5

Review fixes from PR #297.

- `render()` with no template argument renders the action's own template, so
  `$this->cell('Inventory')->render()` and `$cell->render()` are now indexed under it. Before,
  the `cell()` call was skipped because it fed a `render()`, and the `render()` was dropped for
  having no template, so the caller disappeared from the "Cell usages".
- A `cell()` call that feeds a `render()` is now skipped only when that `render()` is itself
  indexed. With an argument that doesn't resolve, such as `render($template)`, the `cell()` call
  keeps its default-template usage instead of losing the link.
- `$cell = $this->cell('Inventory')` is no longer counted as a `display` usage when every later
  use of `$cell` in the scope is an indexed `$cell->render(...)`. This replaces Session #2's
  "indexed under its default template as well". Any other use (`echo $cell`, passing it on,
  reassigning it) keeps the default usage, so `echo $cell; $cell->render('compact');` lists one
  usage under each template. An assignment with no later use still counts.
  `laterUsesOfAssignedVariable` in `TemplateNames.kt` finds the uses. It stops at the outer
  `VARIABLE` node, because the variable's own token is also a `VARIABLE` and would otherwise
  be counted as a second use that isn't a receiver.
- The view file index version went to 22.

The reviewer also found that an app cell and a plugin cell with the same name share keys,
which affects variables (completion, types, undefined-variable suppression) as well as usage
lists. The view file index keys plugin templates relative to their own templates directory,
so `cell/Widget/display` matches both cells' actions before the variable key comes into it.
Plugin controllers share the same collision through `controllerMethodKey`. Plugin-aware keys
for controllers and cells together are left for a follow-up.

Tests: six new cases in `CellReferenceIndexTest`: chained and variable `render()` with no
argument, an unresolved template argument, rendered-only, echoed-only, and echoed-then-rendered.

### Session #6

Merged main after #299, which conflicted in the view file index:

- `ElementType` keeps both main's `FIELD` and this branch's `CELL_RENDER`, and
  `ViewReferenceData`/`PsiElementAndPath` carry both `elementType` and main's `pluginPrefix`.
- The view file index version went to 23, past both sides' versions.
- Cell files count as controllers in the indexer, so they also get main's plugin context and
  `$layout` handling. Cells don't declare `$layout`, so nothing changes for them.
- Main's rendering-controller lookup also walks view references, but only for CakePHP 2
  templates, so `CELL_RENDER` references never reach it.

Main's `pluginPrefix` is a starting point for the plugin-aware keys follow-up.

Full stub suite: 1118 tests passing.
