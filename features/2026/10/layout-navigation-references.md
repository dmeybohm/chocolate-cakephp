# Layout navigation references

## Context

A template links back to the controllers that render it: "go to related" and the toggle action read
the view file index, which stores a reference under the template's key for each render the indexer
finds. On CakePHP 2 the indexer also stores layout references, under `Layouts/<name>`, so a layout
should already link to the controllers that name it. That has never been tested. On CakePHP 3 to 5
layouts have no references at all.

[The `RenderingControllers` simplification](rendering-controllers-element-plugin.md) (#305) stopped
helper lookup from reading the layout references, so navigation is now their main use, which is why
this is its own change. [Plugin-aware view keys](plugin-aware-view-keys.md) (#307) has since merged
and settles most of the plugin questions the first draft of this log raised. [Controller-side
helpers](controller-side-helpers.md) (#309) then gave `CakeThreeAndLater` the per-version layout and
cell directories, ran `RenderingControllers` on every version, counting cells, and added
`indexesLayoutReferences` to keep layout indexing off on 3 to 5 until this feature. The notes below
are from reading the code after those merges.

## What exists today

### CakePHP 2 layout references

`ViewFileDataIndexer.indexLayoutReferences` runs for any file whose structure sets
`indexesLayoutReferences`, which only `TemplateDirStructure.CakeTwo` does, and keys the names under
its `layoutDirectory` (`Layouts`). It stores:

| Form | Where it is read | Reference type |
|---|---|---|
| `$this->render('view', 'admin')`, the second argument | Any file | `METHOD_REFERENCE` |
| `$this->layout = 'admin'` | Any file, controllers and templates alike | `FIELD_ASSIGNMENT` |
| `public $layout = 'admin'` on a class | Controller files only | `FIELD` |

Each name goes through `resolveName`, so `Blog.admin` is stored under `Layouts/admin` with
`pluginPrefix` `Blog`. Names starting with `/` are skipped. The key is independent of the file naming
the layout, unlike template keys, which are relative to the controller's view directory.

The consumers are the ones templates have:

- `ViewToControllerGotoRelatedProvider` accepts any view file (`isCakeViewFile` only asks for a
  templates directory above it), builds the `ViewFile` with `viewFileOf` and lists
  `directReferencesTo`. For a layout `app/View/Layouts/admin.ctp` that key is `Layouts/admin`.
- `ToggleBetweenControllerAndViewAction.tryToNavigateFromView` does the same, dropping
  `CELL_RENDER` references, and shows "No related file found" when nothing is left.
- `ContributingSourceWalk` reads the same references for variables. See
  [Effects on variables](#effects-on-variables).
- `RenderingControllers` stops at a layout and takes all controllers, on every version since #309,
  so it no longer reads them.

`directReferencesTo` filters by plugin through `ViewFileResolution.directlyReaches`, so the
collision this log worried about before #307 is handled: an app `admin` layout and a `Blog` `admin`
layout share the key, but each lists only the references that reach it by the framework's rule.

Two details of the current output:

- Every non-cell item is grouped under "Controllers" and named `Class::method()`. A `FIELD`
  reference has no method, so it falls back to the file name. A template that names a layout
  (`$this->layout = 'probe'`, as in `testLayoutNamedFromTemplateWraps`) is listed under
  "Controllers" with the template's file name.
- `canonicalizeFilenameToKey` strips a data-view directory, so `Layouts/json/default.ctp` has the
  key `Layouts/default`, the same as `Layouts/default.ctp`, and a reference to `default` reaches both.
  The framework's data views do not render it, as [Session #3](#session-3) records.
  `Layouts/Emails/html/default.ctp` and `Layouts/js/default.ctp` keep their directories in the key,
  and nothing indexed names them.

### CakePHP 3 to 5

Since #309 `CakeThreeAndLater` has a `layoutDirectory` (`Layout` for `cakeThree`, `layout` for
`cakeFour`), so `isLayoutKey` tells a layout apart and `RenderingControllers` stops at one for
helpers. `indexesLayoutReferences` is false there, so `indexLayoutReferences` returns at once and
no layout has references. The viewBuilder parser, `parseViewBuilderCall`, recognizes only
`setTemplate` and `setTemplatePath`, and the only chain it follows is
`setTemplatePath()->setTemplate()`. A `setTemplate()` chained after any other setter, such as
`setLayout('admin')->setTemplate('view')`, is dropped today.

One structural problem: `TemplateDirStructure.ofSourceFile` gives every non-CakePHP 2 controller
the `cakeFour` structure. The layout directory is `Layout` in CakePHP 3 (`src/Template/Layout/`) and
`layout` in CakePHP 4 and 5 (`templates/layout/`), and `isLayoutKey` compares case-sensitively, so a
Cake 3 controller indexed through the `cakeFour` structure would store `layout/admin` while the
layout file's key is `Layout/admin`. Templates are not affected, since a view file's structure comes
from its templates directory. The helpers log left this to this feature.

## The framework's layout API

Checked in the real sources the vendor spike downloads to `build/cakephp-vendor/` (2.10.24, 3.10.5,
4.6.5, 5.4.3).

| Form | 2 | 3 | 4 | 5 |
|---|---|---|---|---|
| `public $layout` on a controller | Declared, default `'default'` | Undeclared; a declared one is copied into the builder by `createView()`, deprecated | No effect | No effect |
| `$this->layout = 'x'` in a controller | Yes | Through `__set`, deprecated | No | No |
| `$this->render($view, 'x')` | Yes | Yes | Yes | Yes |
| `$this->viewBuilder()->setLayout('x')` | No | Yes | Yes | Yes |
| `$this->viewBuilder()->layout('x')` | No | Yes, deprecated | No | No |
| `$this->setLayout('x')` in a template | No | Yes | Yes | Yes |
| `$this->layout('x')` in a template | No | Yes, deprecated | No | No |
| `$this->layout = 'x'` in a template | Yes, public | Works, protected | Works, protected | Works, protected |

On 3 to 5 `View::$layout` is protected, but a template runs inside the View, so the assignment still
takes effect. `setLayout()` is the documented form.

`_getLayoutFileName` applies `pluginSplit` to the name on all four versions and searches
`_paths($plugin)`, so `Foo.admin` looks in the app's override directory for `Foo`, then `Foo`'s
layouts, then the app's, like templates. A `layoutPath` (`setLayoutPath()`, `$layoutPath`) adds a
subdirectory under the layout directory. On 3 to 5, `_getSubPaths` also tries a routing prefix's
directory first, so an `Admin`-prefixed request renders `templates/Admin/layout/default.php` when it
exists. CakePHP 2 has no prefix directories for layouts.

The assumption suite records that a layout named from a template wraps the output on all four
versions (`testLayoutNamedFromTemplateWraps`, with `$this->layout = 'probe'` on 2 and
`$this->setLayout('probe')` on 3 to 5).

## Plan

1. **CakePHP 2 tests.** Cover "go to related" and the toggle action from a layout with the three
   indexed forms, following `ViewToControllerGotoRelatedTest` (cake5) and `CellToggleActionTest` for
   driving the action. The fixtures have `cake2/app/View/Layouts/{default,ajax,error,flash}.ctp` but
   no controller names a layout, so the tests add controllers with `addFileToProject`, as
   `PluginAwareViewKeysTest` does. Include the plugin cases below.
2. **Controller structure.** `CakeThreeAndLater` has its per-version `layoutDirectory` since #309.
   A controller's path does not tell CakePHP 3 from 4 (`src/Controller/` on both), so index each of
   its layout references under both directories, by the rules of each version. See
   [Session #2](#session-2).

   The same check decides CakePHP 2 with both versions enabled, and gets a Cake 2 plugin controller
   wrong today: `isCakeTwoController` wants the directory above `Controller` to be the Cake 2 app
   directory, and in `app/Plugin/Blog/Controller/` it is the plugin. The controller then takes the
   `cakeFour` structure, as #307 noted for its plugin, and `fullImplicitViewPath` already
   underscores its camel-case action names as if it were CakePHP 3+. Accept a `Controller`
   directory whose parent is a plugin directory as well: one under `Plugin` or `plugins` below the
   Cake 2 app directory, or a configured Cake 2 plugin path, as `cakeTwoPluginName` finds them.
   CakePHP 3+ plugins have `src` between the plugin and `Controller`, so they do not match. No test
   runs with both versions enabled yet, so add one for this.
3. **CakePHP 3 to 5 forms.** Set `indexesLayoutReferences` on `CakeThreeAndLater`. It then holds on
   every structure, so drop the flag and let `layoutDirectory` alone gate the indexer again, as it
   did before #309. The render second argument and `$this->layout` assignments are then indexed by
   the existing code, subject to the version limits below. Add:
   - `viewBuilder()->setLayout()` on 3 to 5 and `viewBuilder()->layout()` with an argument on 3.
     Extend `parseViewBuilderCall` and `ViewBuilderCallInfo`, and pass the calls to
     `indexLayoutReferences` as `VIEW_BUILDER` references. Generalize the chain check to "the
     receiver chain ends at `$this->viewBuilder()`, through any setters", which also fixes
     `setTemplate()` chained after `setLayout()`. `indexViewBuilderCalls` ignores method names it
     does not know, so the new calls pass through it unchanged. The assumption suite's
     `HelpersController` already names its layout this way, as
     `enableAutoLayout(true)->setLayout('helper')->addHelper(...)`.
   - `$this->setLayout()` with a string argument from view files on 3 to 5, and `$this->layout()`
     only on 3, where `View::layout()` exists.
   - Every form requires `$this` as the receiver, or `$this->viewBuilder()` at the root of the
     chain. `map()` already filters the render calls and the layout assignments to `$this`, and
     `isViewBuilderMethodCall` checks the chain root, so the new forms follow the same rule. A
     mailer or view object in a controller, as in `$email->viewBuilder()->setLayout('x')` or
     `$view->render('x', 'y')`, must not produce a layout reference.
   - Limit the controller property forms, `public $layout` and `$this->layout = 'x'` in a controller,
     to CakePHP 2 and 3, where the property has an effect. Cake 3's `__set` maps the assignment to
     `setLayout()`; Cake 4's only handles `components`, and Cake 5 has none. A structure flag like the
     `readsHelpersProperty` #309 added for the `$helpers` property, which Cake 3 reads the same way,
     gates both forms. `indexLayoutReferences` already knows controller files through `isController`,
     so a template's `$this->layout = 'x'` stays indexed on every version. The deprecated `layout()`
     setters, on the builder and the view, take a second flag true only on `cakeThree`.
   - Skip cells. A cell renders with the layout off on 3 to 5, so a `setLayout()` in a cell class
     names nothing, and neither does one in a cell template on 4 and 5. On 3 `Cell::render()` only
     sets the builder's layout to false, so a cell template can name one, as
     `testLayoutNamedInCellTemplateWraps` records. The structure's `cellDirectory` and `isCellKey`,
     from #309, tell a cell template apart.
   - Stop `ContributingSourceWalk` at a layout, by the [isolation rule](#effects-on-variables).
   - Bump `ViewFileIndex.getVersion()` from 24 to 25.
4. **Grouping.** In `ViewToControllerGotoRelatedProvider`, put references from view files under
   "Views" or "Elements", as `ElementToUsagesGotoRelatedProvider` does, and name a `FIELD`
   reference by its class.
5. **CakePHP 3 and 4 tests.** Port the cake5 tests, with the Cake 3 `Layout` directory, the
   deprecated `layout()` setter and the `$layout` property.

Forward navigation, from the string in `setLayout('admin')` or `render()`'s second argument to the
layout file, is a separate change: `TemplateGotoDeclarationHandler` resolves templates by path
without the index, and layouts would need their own branch there. It is not part of this plan.

## Effects on variables

`ContributingSourceWalk` starts from a view file's own references, so from a layout on CakePHP 2 it
reaches the controllers naming the layout today, and a controller reference contributes the `set()`
calls of the method containing it: an action for `render('view', 'admin')`, `beforeFilter()` for
`$this->layout = 'admin'` there, and nothing for a class-level `$layout` field, which has no
method. An element the layout includes gets the same through the layout hop. Nothing names
`default`, so it gets nothing, and the 15-lookup budget trims a layout named by many controllers.

That is neither the union nor the intersection of what renders the layout, and the inspection
suppressor reads the same sources, so one action setting `$var` and naming the layout stops `$var`
being flagged in a layout every other request renders without it. Adding the CakePHP 3 to 5
references would extend this.

**Isolation rule.** The variable walk treats a layout as a file with no references, on every
version. A layout offers no controller variables, and an element it includes gets its passed data
and is decided by its own plugin. This is how the walk treats a CakePHP 3 to 5 layout today, as the
plugin-aware log records, so CakePHP 2 changes to match. The references stay in the index for
navigation; only `ContributingSourceWalk` stops reading them, as `RenderingControllers` already
stops at a layout for helpers. No existing test depends on variables in a layout.

**Later.** A layout's variables are the ones set on every path that renders it: `beforeRender()` on
`AppController` and the base classes, plus whatever every action sets. That needs the default layout
and the whole rendering path of each action, not the explicit references this feature indexes, so it
is its own feature. Completion could show the union with a weaker presentation while the inspection
used the intersection.

`RenderingControllers` stops at a layout on every version and is unaffected: it takes all
controllers there without reading the layout's references, so adding them changes no helper. #309
split indexing from `isLayoutKey` with `indexesLayoutReferences` to land its layout directories
without switching the indexer on; step 3 ends that split, since this feature indexes on every
version.

## Plugins

- **Prefixed names.** `resolveName` splits `Plugin.name` on every version since #307, so
  `setLayout('Blog.admin')` is stored under `layout/admin` with `pluginPrefix` `Blog`, and
  `directlyReaches` checks it against the layout's plugin.
- **Shared keys.** An app and a plugin layout named `admin` share a key. `directReferencesTo` keeps
  a reference by the fallback rule from the plugin-aware log: a `Blog` controller naming `admin`
  reaches `Blog`'s layout when it exists, and the app's when it does not; an app controller never
  reaches `Blog`'s layout without the prefix.
- **Override directory.** `templates/plugin/Blog/layout/admin.php` is `ViewFile(layout/admin, Blog)`
  through `viewFileOf`, so `Blog`'s controllers link to it. As #307 notes, the plugin's own copy
  keeps the same `ViewFile` and lists them too.
- The CakePHP 2 tests can include these cases from the start, since the filter is in place.

## Known limitations

- **The default layout.** Nothing names `default`, so "go to related" from `default.php` lists only
  explicit `setLayout('default')` references, and the toggle action reports no related file.
  Listing every controller, as `RenderingControllers` does for helpers, is a different feature.
- **Prefix layout directories** on 3 to 5 (`templates/Admin/layout/default.php`) are not layout keys
  and get no references.
- **`setLayoutPath()`** is ignored, so a layout named under a layout path is keyed as if it were in
  the layout directory itself. Email layouts and mailers are out of scope; a `src/Mailer` file has
  no templates directory above it and is not indexed.
- **Non-literal names** are skipped, as for templates.
- **Data-view layouts.** `Layouts/json/default.ctp` lists the references to `default`, although a data
  view renders without a layout. Only a layout path reaches that file.
- **`LayoutController` on CakePHP 4 and 5.** A controller's `setLayout('admin')` is also stored under
  `Layout/admin`, which is the key of `templates/Layout/admin.php`, a template of a controller named
  `LayoutController`. That template then lists the controller naming the layout and offers its
  variables.

## Tests

Every test attaches the real framework since #310, through `myFixture.configureByFiles` with no
stub file, so a controller fixture extends the real `Controller` class on each version.

CakePHP 2 first, then cake5, then 3 and 4:

- "Go to related" from a layout lists the action with `render('view', 'admin')`, the method with
  `$this->layout = 'admin'`, and the class with `public $layout = 'admin'` (2 and 3).
- The toggle action from a layout with one reference opens the controller, and from a layout with
  no references reports nothing.
- A template that names the layout is listed under "Views", not "Controllers".
- A layout in a data-view directory (`Layouts/json/default.ctp`) lists the references to `default`,
  which is how the plugin keys it rather than what the framework renders.
- `setLayout('admin')` in an action, in `beforeRender()`, chained before `setTemplate()`, and
  `layout('admin')` on CakePHP 3. The chained `setTemplate()` still links its template.
- Plugins: a plugin controller naming `admin` links to the plugin's layout when it exists and to the
  app's otherwise; an app controller's `Blog.admin` links to `Blog`'s layout; the app's `admin` does
  not list the plugin controller when the plugin has its own.
- CakePHP 3 keys: a controller's `setLayout()` reaches `src/Template/Layout/admin.ctp`.
- Both versions enabled: a Cake 2 plugin controller's `$this->layout = 'admin'` reaches the plugin's
  `Layouts/admin.ctp`, and its camel-case action still reaches its Cake 2 template.
- Negative: `$email->viewBuilder()->setLayout('admin')` and `$view->render('x', 'admin')` in a
  controller, and `$other->setLayout('admin')` in a template, give the layout no reference.
- Version limits, on CakePHP 4 and 5: a controller's `$this->layout = 'admin'` and `public $layout`,
  a template's `$this->layout('admin')` and the builder's `layout('admin')` give the layout no
  reference, while a template's `$this->layout = 'admin'` still does.
- Variables: a layout named from an action does not offer that action's variables, on CakePHP 2
  and 5, and an element the layout includes still gets the data passed to it.

## Implementation Progress

### Session #1

All five plan steps are done and the full test suite passes. Layouts now link back to what names them
on every version, and `ContributingSourceWalk` treats a layout as having no references.

Where the implementation differs from the plan:

- **Layout setters.** `TemplateDirStructure.layoutSetters` is one set of method names, empty on
  CakePHP 2, instead of a flag for the deprecated `layout()` setters. It also keeps `setLayout()` from
  being indexed on CakePHP 2, where the method does not exist.
- **Chain order.** Calls in a chain share a start offset and the outer call was collected first, so
  a `setTemplate()` chained after `setTemplatePath()` lost its path. Generalizing the chain check did not
  cause this, but the `setLayout()->setTemplatePath()->setTemplate()` test found it.
  `findViewBuilderCallsRecursive` now collects receivers first.

The label of implicit-render references in "go to related" is a separate bug, tracked in
[Go to related method names](goto-related-method-names.md).

### Session #2

The first version told CakePHP 3 controllers from later ones by looking for a `Template` directory
while indexing. That is the bug class [view index bugs](view-index-bugs.md) removes for elements: a
controller indexed before the directory existed stored its references under `layout/` and kept them
until the file changed. An element's directory comes from the calling file's own path, but a controller's
path is the same on CakePHP 3 and 4. Composer detection is no answer either, since a project's settings
can override it to support either version or both.

So `TemplateDirStructure.layoutStructuresOf` gives a CakePHP 3+ controller both structures, and
`indexLayoutReferences` runs once for each. A key for the other version is simply never looked up,
and each run applies its own version's rules: `public $layout`, `$this->layout = 'x'` and the builder's
`layout()` are stored only under `Layout/`, so they still name nothing on CakePHP 4 and 5.
This is the alternative the plan first rejected for leaving a wrong key in the index, with the one
collision noted under [Known limitations](#known-limitations). `isCakeThreeController` is gone.

### Session #3

Added `LayoutResolutionTest` to the assumption suite on all four versions, so the table in
[The framework's layout API](#the-frameworks-layout-api) is now run rather than read from source. It
confirmed the table, the version limits and the plugin rules, and corrected two claims:

- **Cell templates on CakePHP 3.** A layout named from a cell template wraps the cell, since
  `Cell::render()` calls `setLayout(false)` on the builder there, where 4 and 5 pass `false` to
  `View::render()`. Those references are now indexed, through
  `TemplateDirStructure.cellTemplatesNameLayouts`. A cell class still names nothing.
- **Data-view layouts.** This log claimed CakePHP 2 renders `Layouts/json/default.ctp` for a JSON request.
  It does not, on any version: `JsonView` renders without a layout, even when one is named
  (`testDataViewRendersWithoutLayout`). A layout path does reach that directory
  (`testLayoutPathSelectsSubdirectory`). The key stripping is shared with templates, where it is right,
  so it stays, and the mismatch is listed under [Known limitations](#known-limitations).

A `render()` with a layout called from a template does name that layout, wrapped again by the outer
one (`testRenderFromTemplateWrapsItsOutputAgainInTheOuterLayout`), so indexing it from any file stands.

### Session #4

Two review fixes:

- **Navigation target.** A layout's `VIEW_BUILDER` reference stored the start of the chain, which
  resolved to `viewBuilder()`. It now stores the offset of the method name, so "go to related" lands on
  `setLayout()`. `setTemplate()` references have the same issue, which predates this feature and is left
  for a separate change.
- **Case-insensitive method names.** PHP method names are case insensitive. The view builder parser now
  matches `viewBuilder()` and its methods ignoring case and keeps the canonical spelling, which also
  covers `setTemplate()` and `setTemplatePath()`, and view layout calls keep the canonical spelling too.
