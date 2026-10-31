# Simplify RenderingControllers layouts

## Context

This began as a plan to fix `RenderingControllers` for unprefixed elements, which resolve against
the plugin of the rendering controller rather than the including file
(`testElementInFallbackTemplateResolvesInControllerPlugin` and its siblings). Reviewing that plan
raised the question of whether the class's precision is worth its cost.

`RenderingControllers` only serves helper lookup on CakePHP 2. It changes a result only when a
helper is aliased, and a wrong answer gives a slightly wrong type: an extra union member, or a
missing alias. Most of its code decides which controllers render a layout, inferring from actions,
templates and `render()` calls whether a base class renders pages of its own.

## Decision

- **Templates and elements** keep the walk back to their rendering controllers, as
  `controller-side-helpers` plans to reuse it.
- **Any layout** gets all subclasses of `\Controller`, plus `AppController` and its subclasses. The
  walk stops at a layout, so an element or template that a layout includes gets them too.
- **`AppController` aliases** need no new code. CakePHP merges them into every subclass with the
  subclass winning (`Controller::_mergeControllerVars`), and the convention type is dropped only
  when every receiver determines the alias. Tests pin this down.
- **Base classes** get no special handling. With an alias on `AppController` they inherit it, and
  without one the convention type is almost certainly in the union already.

### What this gives up

- `$this->Html` in a layout used by one controller, such as `admin.ctp`, offers every alias in the
  project, and go-to-declaration may list several targets.
- `HtmlHelper` stays in layout unions unless every controller determines the alias. One controller
  that bypasses `AppController` without an alias, such as in a third-party plugin, keeps it in every
  layout even when `AppController` sets the alias. Templates aren't affected.

## Plugins

Layouts have no plugin check: a plugin's layout also gets the app's aliases, and a plugin prefix on
a layout name doesn't matter. This keeps a plugin's own aliases available in every layout, at the
cost of the extra types above.

Templates and elements keep `resolvesTo` as it is. CakePHP 3 to 5 have no plugin here, as `pluginOf`
returns null for them. That arrives with plugin-aware view keys and `controller-side-helpers`.

### Plugins that don't extend `AppController`

Layouts use `\Controller` rather than `AppController` as their root. If
`FooAppController extends Controller`, `BarsController`'s alias would otherwise disappear even from
Foo's own layout, since a layout no longer looks at the controllers naming it. A missing alias stops
its methods completing, while the extra `HtmlHelper` from a controller without an alias is accurate,
as that controller does render with it.

The two lookups are always combined, with no fallback branch, so results don't depend on whether the
first found anything.

Whether `getAllSubclasses("\\Controller")` needs the framework class to be indexed is unknown, as it
may only read the `extends` name. A test settles it:

- If the alias is found without the framework class, use the `\Controller` lookup alone.
- If not, keep both lookups and accept the limitation: when the framework isn't indexed, a controller
  extending `Controller` directly isn't found for layouts, so its alias is missing there. This needs
  both conditions at once, and finding those controllers would mean scanning all class names.

### Known gap until the element fix

Elements keep today's per-hop check, which uses the including file's plugin. Stopping the walk at a
layout doesn't avoid this: an app layout that calls `element('shared')`, rendered by a `Blog`
controller, with `Blog` having its own `shared` element:

- `Blog`'s element is dropped at the hop, so it falls back to `AppController`'s helpers.
- The app's element keeps the hop, and so gets all controllers, including `Blog`'s, which doesn't
  render it.

The result is the same kind of slightly wrong type described above, so it's accepted until the rule
moves over.

## Layout references

The CakePHP 2 layout references stay in the view file index. Helper lookup no longer reads them, but
"go to related" and the toggle action read that index, and variable tracking in layouts could later
start from it. That work would need to know which actions render a layout, so the removed
class-level heuristics would not carry over.

## Element plugin fix moved

Fixing the walk for unprefixed elements moves to
[plugin-aware view keys](plugin-aware-view-keys.md), which needs the same rule for variables on all
versions. The bug here needs a shared element name and differently aliased controllers on CakePHP 2,
and its effect is limited to the type above. These notes carry over:

- Check the carried references wherever the walk reaches a class, including at a layout, where they
  filter the union. `Blog`'s `shared` element reached through a layout gets only `Blog`'s
  controllers, and the app's gets all controllers except `Blog`'s. The union for the layout itself
  stays unfiltered.
- A prefixed hop must not clear references already carried: in `element('Blog.a')` where `a` calls
  `element('b')`, `b` still follows the controller's plugin.
- The walk also passes through `$this->layout` assignments and `render()` calls in view files. No
  assumption test covers a layout named from a template.
- Track `(ViewFile, pending references)` as visited, and cache `resolvesTo` within a search.
- The shared helper takes the rendering class's plugin as input, as elements in cell templates are
  still an open question. That log is `features/2026/10/cell-template-element-plugin.md` on the
  `cell-template-element-plugin` branch, and isn't on this branch until that one merges.

## Controller-side helpers

`controller-side-helpers` should follow the same layout rule when it next merges `main`. It would
need to recognize layout files, use the version's framework controller class, and take
`AppController` from the configured app namespace if both lookups are kept, but no
longer needs layout names, layout paths or `setLayout()` for helpers. Layout references on CakePHP 3
to 5 would be their own change, for navigation.

## Plan

1. Rewrite `LayoutHelperTest` around the rules above, and add tests for `AppController` aliases.
2. Replace the layout handling in `RenderingControllers` with the rule above, and remove the
   heuristics and `defaultLayoutKey`. `frameworkControllerClass` stays, for the layout lookup.
3. Run the targeted tests, `LayoutHelperTest` and the other CakePHP 2 helper tests, then the full
   suite.

## Tests

- `AppController` aliases: the alias replaces the convention type in a template, and a subclass's
  alias wins in its template. A controller extending `Controller` directly keeps the convention type
  in its template.
- Layout unions, with separate fixtures: when every controller inherits from `AppController`, a
  layout unions the aliases without the convention type. With a direct `Controller` subclass without
  an alias, it unions the aliases plus `HtmlHelper`.
- Layouts: a named layout, the default layout, a data view layout and a layout in a subdirectory all
  get the same union. So does an element in a layout, and an element included from both a layout and
  a template.
- Templates: a template rendered by one controller still gets only that controller's helpers, and so
  does an element only its templates include.
- Plugins: a plugin's layout gets the union, including the plugin's aliases. A plugin controller's
  template gets only that controller's helpers. An app override of a plugin layout still counts as a
  layout. A plugin whose base class extends `Controller` keeps its alias in layouts.
- Framework not indexed: a plugin controller extending `Controller`, with no `Controller` class in
  the fixture, and whether its alias reaches a layout. The result decides between the lookups above,
  and the test stays to record it.
- Existing tests that change: `test a plugin's default layout gets the plugin's controllers` and
  `test a plugin prefixed layout gets the app controller that names it` now expect the union.

## Implementation Progress
