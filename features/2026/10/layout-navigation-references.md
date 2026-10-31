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
and settles most of the plugin questions the first draft of this log raised; the notes below are
from reading the code after that merge.

## What exists today

### CakePHP 2 layout references

`ViewFileDataIndexer.indexLayoutReferences` runs for any file whose structure has a
`layoutDirectory`, which only `TemplateDirStructure.CakeTwo` sets (`Layouts`). It stores:

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
- `RenderingControllers` stops at a layout and takes all controllers, so it no longer reads them.

`directReferencesTo` filters by plugin through `ViewFileResolution.directlyReaches`, so the
collision this log worried about before #307 is handled: an app `admin` layout and a `Blog` `admin`
layout share the key, but each lists only the references that reach it by the framework's rule.

Two details of the current output:

- Every non-cell item is grouped under "Controllers" and named `Class::method()`. A `FIELD`
  reference has no method, so it falls back to the file name. A template that names a layout
  (`$this->layout = 'probe'`, as in `testLayoutNamedFromTemplateWraps`) is listed under
  "Controllers" with the template's file name.
- `canonicalizeFilenameToKey` strips a data-view directory, so `Layouts/json/default.ctp` has the
  key `Layouts/default`, the same as `Layouts/default.ctp`. That matches CakePHP 2, which renders
  `Layouts/json/default.ctp` for a JSON request when the controller names `default`, so a reference
  to `default` reaches both. `Layouts/Emails/html/default.ctp` and `Layouts/js/default.ctp` keep
  their directories in the key, and nothing indexed names them.

### CakePHP 3 to 5

`CakeThreeAndLater` leaves `layoutDirectory` null, so `indexLayoutReferences` returns at once and
`isLayoutKey` is always false. The viewBuilder parser, `parseViewBuilderCall`, recognizes only
`setTemplate` and `setTemplatePath`, and the only chain it follows is
`setTemplatePath()->setTemplate()`. A `setTemplate()` chained after any other setter, such as
`setLayout('admin')->setTemplate('view')`, is dropped today.

One structural problem: `TemplateDirStructure.ofSourceFile` gives every non-CakePHP 2 controller
the `cakeFour` structure. The layout directory is `Layout` in CakePHP 3 (`src/Template/Layout/`) and
`layout` in CakePHP 4 and 5 (`templates/layout/`), and `isLayoutKey` compares case-sensitively, so a
Cake 3 controller indexed through the `cakeFour` structure would store `layout/admin` while the
layout file's key is `Layout/admin`. Templates are not affected, since a view file's structure comes
from its templates directory.

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
2. **Per-version layout directory.** Give `CakeThreeAndLater` a `layoutDirectory` constructor
   argument (`Layout` for `cakeThree`, `layout` for `cakeFour`). Then make `ofSourceFile` tell a
   CakePHP 3 controller from a CakePHP 4 one: walk up to the `Controller` directory and return
   `cakeThree` when its parent has a `Template` child, the way `isCakeTwoController` already checks
   for `templates` and `Template` siblings. The alternative, storing a controller's layout reference
   under both keys, avoids the detection but leaves a wrong key in the index for every reference,
   so prefer the fix. `controller-side-helpers` needs the same constructor argument; whichever lands
   first adds it.
3. **CakePHP 3 to 5 forms.** With `layoutDirectory` set, the render second argument and
   `$this->layout` assignments are indexed by the existing code. Add:
   - `viewBuilder()->setLayout()` on 3 to 5 and `viewBuilder()->layout()` with an argument on 3.
     Extend `parseViewBuilderCall` and `ViewBuilderCallInfo`, and pass the calls to
     `indexLayoutReferences` as `VIEW_BUILDER` references. Generalize the chain check to "the
     receiver chain ends at `$this->viewBuilder()`, through any setters", which also fixes
     `setTemplate()` chained after `setLayout()`. `indexViewBuilderCalls` ignores method names it
     does not know, so the new calls pass through it unchanged.
   - `$this->setLayout()` and `$this->layout()` with a string argument, from view files.
   - Limit the `$layout` field form to CakePHP 2 and 3, where it has an effect, and to controller
     files, not cells.
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

`ContributingSourceWalk` starts from the view file's own references, so from a layout on CakePHP 2
it already reaches the controllers naming the layout. A controller reference contributes the
`set()` calls of the method containing it: an action for `render('view', 'admin')`, `beforeRender()`
for `$this->layout = 'admin'` there, and nothing for a class-level `$layout` field, which has no
method. An element included by the layout gets the same through the layout hop. Adding the CakePHP 3
to 5 references extends this to `setLayout()` there, so completion in a layout and its elements will
start offering variables from the methods that name the layout. This is a side effect to record and
test, not the layout variable tracking the earlier draft put out of scope: that needs the default
layout and every rendering action, and the 15-lookup budget of the walk still applies, so a layout
named by many controllers gets a subset.

`RenderingControllers` stops at a layout and is unaffected. The `layoutDirectory` coupling noted by
`controller-side-helpers`, that setting it switches on indexing as well as `isLayoutKey`, is what
this feature wants, so there is nothing to split; both features need the per-version directories
from step 2.

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
- With CakePHP 2 and 3+ both enabled, a CakePHP 2 plugin controller takes the CakePHP 3+ structure
  in `ofSourceFile`, as #307 records, and would store its layouts under `layout/`.

## Tests

CakePHP 2 first, then cake5, then 3 and 4:

- "Go to related" from a layout lists the action with `render('view', 'admin')`, the method with
  `$this->layout = 'admin'`, and the class with `public $layout = 'admin'` (2 and 3).
- The toggle action from a layout with one reference opens the controller, and from a layout with
  no references reports nothing.
- A template that names the layout is listed under "Views", not "Controllers".
- A layout in a data-view directory (`Layouts/json/default.ctp`) lists the references to `default`.
- `setLayout('admin')` in an action, in `beforeRender()`, chained before `setTemplate()`, and
  `layout('admin')` on CakePHP 3. The chained `setTemplate()` still links its template.
- Plugins: a plugin controller naming `admin` links to the plugin's layout when it exists and to the
  app's otherwise; an app controller's `Blog.admin` links to `Blog`'s layout; the app's `admin` does
  not list the plugin controller when the plugin has its own.
- CakePHP 3 keys: a controller's `setLayout()` reaches `src/Template/Layout/admin.ctp`.
- Variables: a layout named from an action offers that action's variables, and so does an element
  the layout includes.

## Implementation Progress
