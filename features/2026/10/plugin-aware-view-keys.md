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

A template's variables are found in two steps, and each step collides on its own:

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

Fixing step 1 alone isn't enough, because step 2 merges the variables again by key.

## What #299 added

#299 (CakePHP 2 configuration disambiguation) solved the same problem for CakePHP 2 helper
lookup, at query time rather than in the keys:

- `TemplateDirStructure.pluginOf(file, projectDir, settings)`: the plugin a source file or
  template belongs to. Implemented for CakePHP 2 only; the CakePHP 3+ structure returns null.
- `TemplateDirStructure.resolveName(name)`: splits `Plugin.name`. The CakePHP 3+ structure
  doesn't split.
- `ViewReferenceData.pluginPrefix` / `PsiElementAndPath.pluginPrefix`: the plugin a reference
  names explicitly, as in `render('Blog.Articles/index')`.
- `ViewFile(structure, templateKey, plugin)` and `RenderingControllers.resolvesTo`, which keep a
  reference only when its plugin matches the template's, including the app's override directory
  for a plugin (`templates/plugin/Blog/...`).

## The framework falls back from a plugin to the app

A plugin controller whose template is missing from the plugin renders the app's template of the
same name. The [assumption tests](cakephp-behavior-probes.md) record this on all four versions as
`testPluginControllerFallsBackToAppTemplate`.

So a template can be rendered by a class in a different plugin, and whether it is depends on which
files exist. A reference is kept by this rule, which `resolvesTo` already implements:

| Template lives in | Reference comes from | Kept |
|---|---|---|
| A plugin, or the app's override directory for it | That plugin | Yes |
| A plugin, or the app's override directory for it | The app or another plugin | No |
| The app | The app | Yes |
| The app | A plugin | Only if that plugin has no template of its own |

## Plan

1. **Plugin of a CakePHP 3+ file.** Implement `pluginOf` for the CakePHP 3+ structure from the
   configured plugin paths (`settings.pluginConfigs`), covering `plugins/<Name>/` and vendor
   plugins, and `resolveName` for `Plugin.name` strings. Tests first, cake5, for a controller, a
   cell and a template in each location.
2. **Filter references by plugin (step 1).** Where `referencingElements` results are consumed for
   controllers and cells (`ViewVariableIndexService.forEachContributingSource`, the toggle action,
   `ViewToControllerGotoRelatedProvider`), keep a reference by the rule above, using the
   referencing file's plugin (or its `pluginPrefix`). The plugins do not have to match: a plain
   match would drop the fallback. Reuse `resolvesTo`, and prefer a shared helper next to `ViewFile` over copying the rule. No index version bump.
3. **Plugin in the variable keys (step 2).** Add the plugin to `controllerMethodKey` and
   `cellActionKey`, such as `Blog.Articles:index` and `cell:Blog.Widget:display`, on both the
   indexing side (`ViewVariableASTDataIndexer`, from the source file) and the lookup side (from the
   matched referencing file). The key identifies the class that sets the variables, not the
   template, so the lookup must never take the plugin from the template: an app template reached
   by fallback would look up `Articles:index` and miss the plugin's variables. Bump the view
   variable index version. App keys stay unchanged.
4. **CakePHP 3/4 coverage.** Port the cake5 tests.
5. **CakePHP 2.** Check whether CakePHP 2 controllers collide the same way, since `pluginOf`
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
- A `Plugin.`-prefixed `render()` from an app controller still links to the plugin template.
- An app override template for a plugin (`templates/plugin/Blog/Articles/index.php`) resolves to
  the plugin's controller.

## Implementation Progress
