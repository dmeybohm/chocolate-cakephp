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

- `cell()` lives in `CellTrait`, which only `View` uses. Controllers do not have `$this->cell()`
  in any of 3, 4 or 5, so the "and controllers" in `ideas.md` is wrong; this feature covers
  templates, elements, layouts and view helpers (`$this->_View->cell()` is the helper form).
- The string is `[Plugin.]Name[::action]`. `pluginSplit()` takes the plugin, `App::className()`
  resolves `Name` to `App\View\Cell\NameCell` (or `Plugin\View\Cell\NameCell`), and the action
  defaults to `display`. `Name` may contain a sub-namespace: `Admin/Inventory` resolves to
  `App\View\Cell\Admin\InventoryCell`.
- The template is `Inflector::underscore($action)` under a path derived from the class name
  after `\View\Cell\`, with namespace separators turned into directories:
  - Cake 4/5: `templates/cell/Admin/Inventory/display.php` (`Cell::TEMPLATE_FOLDER` is `cell`)
  - Cake 3: `src/Template/Cell/Admin/Inventory/display.ctp`
- Inside the action the cell may call `$this->viewBuilder()->setTemplate()` /
  `setTemplatePath()`, and `Cell::render($template)` takes an explicit template. The path
  prefix is only applied when the builder has no template path, so an action-level
  `setTemplatePath()` wins, as it does in controllers.
- Cells use `ViewVarsTrait`, so `$this->set()` in an action feeds the cell template, and
  `LocatorAwareTrait`, so `$this->fetchTable()` works. They have no component registry.

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

### 2. Cell action to template

A cell file is `{Name}Cell.php` under a `View/Cell` directory. `ControllerMethodLineMarker`
works from a `ControllerPath` (name + prefix, giving `Admin/Movie/`); the cell equivalent is a
path of `cell/Admin/Inventory/` on Cake 4/5 and `Cell/Admin/Inventory/` on Cake 3. The plan is
to generalise the marker's `RelatedLookupInfo` from `ControllerPath` to a small sealed source
type (controller or cell) rather than copying the marker, so `render()`, `setTemplate()` and
`setTemplatePath()` inside a cell action get the same markers as inside a controller action for
free. `actionNamesFromControllerMethod` is already source-agnostic.

`CreateViewFileAction` (Ctrl-click on the marker) should create the cell template in the right
directory too.

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
  name, sub-namespace name, no navigation on other receivers, ternary branches.
- `CellCompletionTest`: cell names offered, plugin names prefixed, actions offered after `::`.
- `CellMethodLineMarkerTest` (or extend the existing controller marker test): marker on
  `display()` targets the template, `render('compact')` and `setTemplate()` markers.
- `CellViewVariableTest`: `$items` completes with its type in the cell template; the
  undefined-variable suppressor accepts it.
- `CellTemplateToCellGotoRelatedTest`: from `cell/Inventory/display.php` to `display()`.

## Implementation Progress

### Session #1

Created the branch and worktree, surveyed the controller/template machinery and the
CakePHP 3/4/5 cell sources, and wrote this plan.
