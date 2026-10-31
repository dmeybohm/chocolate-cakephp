# Plugin-aware view keys

## Context

Follow-up to [cell support](cell-support.md) (#297), from its review. An app class and a plugin
class with the same name share index keys, so a template of one picks up the variables and
references of the other. This predates cells: plugin controllers already collide with app
controllers of the same name. The cell-support log's Session #5 describes how the reviewer found
it, and Session #6 notes that #299's `pluginPrefix` is a starting point.

Example: `src/Controller/ArticlesController.php` and the `Blog` plugin's
`plugins/Blog/src/Controller/ArticlesController.php`, each with an `index()` action. Opening
`templates/Articles/index.php` offers the plugin action's variables as well as the app's, and
`plugins/Blog/templates/Articles/index.php` offers the app's. The same goes for an app
`WidgetCell` and a plugin `WidgetCell`.

This is only wrong when the plugin has its own template. Without one, the plugin's controller
renders the app's template, so both controllers really do reach it and it should offer both sets
of variables (see [the fallback](#the-framework-falls-back-from-a-plugin-to-the-app)).

## Where the keys collide

A template's variables are found in two steps, and each step collides on its own. Element data
collides a third way:

1. **View file index: template → referencing files.** `ViewFileDataIndexer` keys a reference by the
   template path relative to the referencing file's templates directory, so a plugin controller's
   implicit render of `index` and the app controller's are both under `Articles/index`, and a
   plugin cell's under `cell/Widget/display` like the app cell's. `referencingElements` for either
   template returns both classes.
   - Effect: "go to related" and the toggle action from a template list both classes.
2. **View variable index: referencing file → variables.** `controllerMethodKey` gives
   `Articles:index` and `cellActionKey` gives `cell:Widget:display` for both classes, so the
   `set()` calls of both are stored under one key.
   - Effect: completion, variable types and undefined-variable suppression mix the two, even
     when step 1 returns only the right class.
3. **View variable index: element → data passed to it.** `elementDataKey` gives
   `element-data:element/shared` for the data array of every `element('shared', [...])` call in
   the project, whichever plugin the call or the element is in. `lookupRawVars` reads that key
   across the whole project, once, before the walk, so data passed to `Blog`'s `shared` element is
   offered in the app's `shared` element, and the other way round.
   - Effect: the same as step 2, for element data.
   - Since #299 the two indexes also disagree on a prefixed name, on CakePHP 2: the view file index
     stores `element('Blog.shared', [...])` under `element/shared` with `pluginPrefix` `Blog`, but
     `ViewVariableASTDataIndexer` keys its data under `element-data:element/Blog.shared`, which no
     lookup reads.

Fixing step 1 alone isn't enough, because steps 2 and 3 merge the variables again by key.

## What #299 added

#299 (CakePHP 2 configuration disambiguation) solved the same problem for CakePHP 2 helper
lookup, at query time rather than in the keys:

- `TemplateDirStructure.pluginOf(file, projectDir, settings)`: the plugin a source file or
  template belongs to. Implemented for CakePHP 2 only; the CakePHP 3+ structure returns null.
- `TemplateDirStructure.resolveName(name)`: splits `Plugin.name`. The CakePHP 3+ structure
  doesn't split.
- `ViewReferenceData.pluginPrefix` / `PsiElementAndPath.pluginPrefix`: the plugin a reference
  names explicitly, as in `render('Blog.Articles/index')`.
- `ViewFile(structure, templateKey, plugin)` and `RenderingControllers.resolvesTo`, which decide
  whether a reference reaches a template, including the app's override directory for a plugin
  (`templates/plugin/Blog/...`).

### The override directory's key

`canonicalizeFilenameToKey` keeps the override directory in a template's key, so
`templates/plugin/Blog/Articles/index.php` is `plugin/Blog/Articles/index`, while the references that
reach it are stored under `Articles/index`: `Blog`'s controller's implicit render, and
`render('Blog.Articles/index')` with `pluginPrefix` `Blog`. Only `ViewHelperReceivers.viewFileOf`
strips the prefix, and only when `pluginOf` knows the plugin. So helper lookup finds the plugin's
controllers for an override template on CakePHP 2, and nothing else does, on any version: "go to
related" from a template and from an element, the toggle action, completion, the type provider, the
inspection suppressor and the lookup that resolves a passed value as a variable of its own file all
query the literal key, and so does the walk in `forEachContributingSource` when it expands an
override template as an ancestor.

#299 put the rule in `viewFileOf` because helper lookup was its scope; its log calls
`ViewHelperReceivers` the one entry point for helpers, and nothing there decides that the other entry
points stay literal. The rule isn't about helpers. A `ViewFile` is the template key and plugin a view
file has, and that is what every entry point needs. So `ViewFile` and `viewFileOf` move out of
`ViewHelperReceivers` into their own file, as a pure move, and each entry point builds a `ViewFile`
from the file and uses its `templateKey` to find references and its `plugin` for the filter below.
The `view-set:` key can stay literal, since a view-set source is read from its own file.

### The type provider's signature

`ViewVariableTypeProvider` defers its lookup to `complete()` through a signature of the CakePHP
version, the literal template key and the variable name. Once an app and a plugin template both have
the key `Articles/index`, the signature can't tell them apart, and the lookup it makes has no plugin
to filter by. #299 solved this for helpers: `ViewHelperInViewTypeProvider` encodes the `ViewFile`
through `ViewHelperReceivers.encode` and decodes it in `complete()`. The variable provider should
carry the `ViewFile` the same way, through the same `encode` and `decode`, which move with
`ViewFile`. The structure id replaces the version field, and a signature in the old format decodes to
null, since `v` is no structure id. The recursion key in
`lookupVariableTypeFromViewPathInSmartReadAction` is the key and variable name, and becomes the
`ViewFile` and variable name.

## How element data came to be keyed this way

Element data was keyed by element path in September, in
[support-element-data-variables](../09/support-element-data-variables.md) and
[its second part](../09/support-element-data-variables-part2.md). The decisions since, in order:

- `cd6d687` introduced the `element-data:` keys with no plugin, and the log listed prefixed element
  calls being keyed verbatim as a known, pre-existing limitation.
- `b3d2b28` narrowed element-data lookups from all scope to project scope, so that `element()` calls
  in vendor plugins stopped leaking variables into same-named app elements. The comment on
  `lookupRawVars` records that this matches the reverse walk.
- The second part (`f78347b`) hit the same collision for `view-set:` keys and noted that project
  scope was not enough, because an app and a project-local plugin are both project content. It fixed
  view-set lookups by reading only the physical file the walk found each reference in, and left
  element data as it was: its records are alternative calls to the same element, and
  plugin-qualified element resolution was put out of scope, deferred to
  [resolve-element-paths-at-lookup](../09/resolve-element-paths-at-lookup.md), which still lists
  plugin-qualified names as a detail to settle.

So nothing rules out plugin-aware element data. It was deferred, and the view-set fix is the
precedent: element data belongs to one `element()` call, which the walk already visits as a
reference with its file and offset.

## The framework falls back from a plugin to the app

A plugin controller whose template is missing from the plugin renders the app's template of the
same name. The [assumption tests](cakephp-behavior-probes.md) record this on all four versions as
`testPluginControllerFallsBackToAppTemplate`. A `Plugin.name` render does the same, and so does a
plugin cell on CakePHP 3 to 5 (`testPrefixedRenderFallsBackToAppTemplate`,
`testPluginCellFallsBackToAppTemplate`).

So a template can be rendered by a class in a different plugin, and whether it is depends on which
files exist. A reference is kept by this rule, which `resolvesTo` already implements:

| Template lives in | Reference comes from | Kept |
|---|---|---|
| A plugin, or the app's override directory for it | That plugin | Yes |
| A plugin, or the app's override directory for it | The app or another plugin | No |
| The app | The app | Yes |
| The app | A plugin | Only if that plugin has no template of its own |

An app cell does not reach a plugin's template (`testAppCellDoesNotReachPluginTemplate`), which the
second row covers. So does a reference that names a different plugin: `render('Other.x')` from
`Blog` finds `Other`'s template, then the app's, and never `Blog`'s, on all four versions
(`testRenderOfOtherPluginDoesNotFallBackToCallingPlugin`). Elements and cells behave the same.

### Unprefixed elements follow the rendering class

An unprefixed element resolves against the plugin of the controller doing the rendering, not the
plugin the including template lives in. All three cases hold on all four versions:

| Rendering controller | Template containing `element('shared')` | Element rendered | Test |
|---|---|---|---|
| Plugin `Blog` | The app's, by fallback | `Blog`'s | `testElementInFallbackTemplateResolvesInControllerPlugin` |
| The app | `Blog`'s, by `render('Blog.x')` | The app's | `testElementInPrefixedTemplateResolvesInControllerPlugin` |
| Plugin `Blog` | `Other`'s, by `render('Other.x')` | `Blog`'s | `testElementInOtherPluginTemplateResolvesInControllerPlugin` |

An unprefixed element in a cell's template follows the cell, not the controller or the template that
called `cell()`, on CakePHP 3, 4 and 5, as recorded in [its own log](cell-template-element-plugin.md)
(`testElementInFallbackCellTemplateResolvesInCellPlugin`,
`testElementInCellCalledFromOtherPluginTemplateResolvesInCellPlugin`,
`testElementInAppCellCalledFromPluginResolvesInApp`). So a walk reaching a cell checks these
references against the cell's plugin.

So the rule above can't be checked one hop at a time when a walk passes through an including file.
An unprefixed reference from a template or element has no plugin of its own until the walk reaches
the class. A prefixed reference can still be checked at its hop.

`RenderingControllers` has this problem today, from reading the code and not from a reproduced
failure, tracked in [its own log](rendering-controllers-element-plugin.md). It checks `reference.pluginPrefix ?: including.plugin` at each hop, on CakePHP 2 only, so a
`Blog` controller rendering an app template by fallback is not found as rendering `Blog`'s element
from that template, and is found as rendering the app's element it does not use.

#305 changed how `RenderingControllers` treats layouts: any layout gets all controllers, and its walk
stops there. So when moving it to the shared helper:

- Carried references filter that union. `Blog`'s `shared` element reached through a layout gets only
  `Blog`'s controllers, and the app's gets all controllers except `Blog`'s. The union for the layout
  itself stays unfiltered.
- Share only the plugin rule, not the walk. `forEachContributingSource` doesn't stop at layouts, and
  shouldn't for variables.

### `cell()` names do not follow the calling file

Templates and elements look in the calling file's plugin first. Cell classes do not: an unprefixed
`cell('Gadget')` always means the app's cell, even inside a template of the plugin that has a
`GadgetCell` (`testPluginTemplateDoesNotReachOwnPluginCellUnprefixed`,
`testPluginTemplateRendersAppCellUnprefixed`). `cellClassFqn` already resolves names this way.

So the plugin of a caller-side `cell()` reference is its prefix or the app, never the plugin of the
file it appears in. The view file index can't express that yet: `ViewFileDataIndexer` parses the
cell's plugin but leaves `pluginPrefix` unset on `CELL_RENDER` references. Filtering them now would
go wrong both ways. `cell('Blog.Widget')` in an app template would count as the app's, and
`cell('Widget')` in a plugin template would count as the plugin's.

## Plan

1. **Plugin of a CakePHP 3+ file.** Implement `pluginOf` for the CakePHP 3+ structure from the
   configured plugin paths (`settings.pluginConfigs`), covering `plugins/<Name>/`, vendor plugins
   and the app's override directory (`templates/plugin/<Name>/`, `src/Template/Plugin/<Name>/` on
   3), as `cakeTwoPluginName` already covers `View/Plugin/<Name>/`. Add `resolveName` for
   `Plugin.name` strings. Tests first, cake5, for a controller, a cell and a template in each
   location.
2. **Filter references by plugin (step 1).** Where `referencingElements` results are consumed for
   controllers and cells (`ViewVariableIndexService.forEachContributingSource`, the toggle action,
   `ViewToControllerGotoRelatedProvider` and `ElementToUsagesGotoRelatedProvider`), keep a
   reference by the rule above, using the
   referencing file's plugin (or its `pluginPrefix`). The plugins do not have to match: a plain
   match would drop the fallback. Reuse `resolvesTo`, and prefer a shared helper next to
   `ViewFile` over copying the rule. Every entry point, including the walk's ancestor expansion,
   gets its key and plugin from a `ViewFile` built from the file, as
   [above](#the-override-directorys-key), so an override template is found by the references stored
   under its plugin key. The variable lookups take a `ViewFile` in place of the key string, and the
   type provider carries it in its signature, as [above](#the-type-providers-signature). For
   `CELL_RENDER` references, store the cell's plugin in
   `pluginPrefix` and treat no prefix as the app, not the calling file's plugin. That is the one
   change here that needs a view file index version bump. When the walk in
   `forEachContributingSource` passes through an including file, carry its unprefixed references
   along and check them against the plugin of the class it reaches, as
   [above](#unprefixed-elements-follow-the-rendering-class) requires. The three navigation entry
   points list direct references and don't walk, so they check each reference at its hop only. From
   an element, that lists an unprefixed `element('shared')` in an app template under the app's
   element even when a plugin controller renders that template by fallback. Following each usage to
   its controller would need a walk per usage, and is left out. Move `RenderingControllers`
   to the same helper, following
   [the notes from its simplification](rendering-controllers-element-plugin.md#element-plugin-fix-moved).
3. **Plugin in the variable keys (step 2).** Add the plugin to `controllerMethodKey` and
   `cellActionKey`, such as `Blog.Articles:index` and `cell:Blog.Widget:display`, on both the
   indexing side (`ViewVariableASTDataIndexer`, from the source file) and the lookup side (from the
   matched referencing file). The key identifies the class that sets the variables, not the
   template, so the lookup must never take the plugin from the template: an app template reached
   by fallback would look up `Articles:index` and miss the plugin's variables. Bump the view
   variable index version. App keys stay unchanged.
4. **Element data per reference (step 3 of the collisions).** Read the data for an element
   reference from the file and call offset the reference names, as `ViewSet` reads `set()` calls
   from its file, instead of reading `elementDataKey` across the project once before the walk. The
   data then follows its reference: it is kept or dropped with it by step 2's filter, including a
   carried reference decided at the class. Each reference stays its own source, so alternative
   calls still union. Both indexers record the call node's start offset, and a file's record for
   one key is a list of calls with their offsets, so the read picks the call whose offset the
   reference names. On the indexing side, split the `Plugin.` prefix in
   `ViewVariableASTDataIndexer` through the same `ReferencePluginContext` the view file index uses,
   so `element('Blog.shared', [...])` is stored under `element-data:element/shared` like its
   reference. The view variable index version bump in step 3 covers this. Today's up-front read
   counts against no lookup budget, and the per-reference read shouldn't either.
5. **CakePHP 3/4 coverage.** Port the cake5 tests.
6. **CakePHP 2.** Check whether CakePHP 2 controllers collide the same way, since `pluginOf`
   already works there. Include it if the fix is just the key change, otherwise leave it noted.

The controller-side line marker goes from a class to its templates by path and doesn't use
these references.

The plugin stays out of the view file index keys. A plugin controller's implicit render has to
stay under `Articles/index` so that opening the app's template finds it through the fallback. With
`Blog.Articles/index`, that template would have to query the key of every plugin.

## Tests

Fixtures: an app and a plugin controller of the same name with one action each setting a
different variable, plus an app and a plugin cell of the same name. For each pair:

- When the plugin has its own template, completion in each template offers only its own class's
  variables.
- When the plugin has no template of its own, the app's template lists both classes and offers
  both sets of variables. Adding the plugin's template drops the plugin's class from the app's.
- "Go to related" and the toggle action list only the matching class.
- A `Plugin.`-prefixed `render()` from an app controller still links to the plugin template. When
  the plugin has no such template, it links to the app's template instead.
- `cell('Blog.Widget')` in an app template is a usage of the plugin's cell template, and of the
  app's when the plugin has none. `cell('Widget')` in a plugin template is a usage of the app's
  cell template, even when the plugin has a template at the same path.
- An app override template for a plugin (`templates/plugin/Blog/Articles/index.php`) resolves to
  the plugin's controller: "go to related", the toggle action and completion from it find `Blog`'s
  controller and its variables, and not the app's. Today they find nothing, on every version.
- Data passed by `element('shared', [...])` in a `Blog` template is offered in `Blog`'s `shared`
  element and not in the app's, and the other way round.
- One app template, rendered only by the app's controller, calls both
  `element('Blog.shared', ['blogOnly' => 1])` and `element('shared', ['appOnly' => 1])`. After
  prefix splitting the two records share a key and a file, so only the offset tells them apart:
  `Blog`'s element gets `blogOnly` alone and the app's gets `appOnly` alone. A single call would
  show an offset mismatch, as missing data; the second call also catches a read that takes every
  call under the shared key and file. The template must not be rendered by a `Blog` controller,
  since by fallback its unprefixed call would then legitimately reach `Blog`'s element too.
- The type provider gives a variable set by both classes under different types its own class's type
  in each template, which checks the signature carries the plugin through `complete()`.
- From `Blog`'s `shared` element, element usages list `element('Blog.shared')` calls from anywhere
  and `element('shared')` calls from `Blog`'s templates, and not those from the app's templates;
  from the app's `shared`, the reverse. An element in the override directory finds its usages.
- A `render('Other.x')` from a plugin controller links to `Other`'s template or the app's, and
  never to the calling plugin's.
- An element in an app template that a plugin controller renders by fallback gets the plugin
  controller's variables through the plugin's element, not through the app's element of that name.
- On CakePHP 2, an element reached through a layout: the plugin's element gets only the plugin's
  controllers, and the app's element of that name gets all controllers except the plugin's.
- A prefixed hop keeps the references already carried: in `element('Blog.a')` where `a` calls
  `element('b')`, `b` follows the controller's plugin.
- An assumption test for a layout named from a template, which the walk passes through and no
  assumption test covers yet.

## Implementation Progress
