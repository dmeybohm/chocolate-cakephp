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
- **A named layout** gets the controllers that name it and their subclasses.
- **The default layout** gets every controller it resolves for. An element or template that a layout
  includes follows the same rules, through the walk.
- **`AppController` aliases** need no new code. CakePHP merges them into every subclass with the
  subclass winning (`Controller::_mergeControllerVars`), and the convention type is dropped only
  when every receiver determines the alias. Tests pin this down.
- **Base classes** that are never dispatched don't count: abstract controllers, `AppController` and
  a plugin's `{Plugin}AppController`. Other base classes do count, so a base without an alias adds
  the convention type next to its subclasses' aliases. That's accepted in exchange for removing the
  heuristics.

## Plugins

Each controller a layout could apply to is checked on its own, with the existing
`resolvesTo(reference.pluginPrefix ?: pluginOf(current, controller), current)`. Subclasses are
checked individually because a subclass can be in a different plugin from the class that names the
layout.

| Layout | Controllers kept |
|---|---|
| The app's named layout | The naming controller and subclasses, except those in a plugin with its own layout of that name |
| A plugin's named layout, or the app's override of it | Controllers in that plugin, and any controller naming it with the plugin's prefix |
| The app's default layout | All controllers, except those in a plugin with its own default layout |
| A plugin's default layout, or the app's override of it | That plugin's controllers |

- **A prefix** such as `$this->layout = 'Foo.promo'` applies to the naming controller's subclasses
  too, since they inherit the prefixed name.
- **A plugin's aliases** need no new code: the class chain already passes through
  `{Plugin}AppController` to `AppController`, matching `Controller::_mergeControllerVars`.
- **CakePHP 3 to 5** have no plugin here, as `pluginOf` returns null for them. That arrives with
  plugin-aware view keys and `controller-side-helpers`.

### Known gap until the element fix

Elements keep today's per-hop check, which uses the including file's plugin. The default layout
doesn't avoid this: an app default layout that calls `element('shared')`, rendered by a `Blog`
controller, with `Blog` having its own `shared` element:

- `Blog`'s element is dropped at the hop, so it falls back to `AppController`'s helpers.
- The app's element keeps the hop and gets `Blog`'s controller, which doesn't render it.

The result is the same kind of slightly wrong type described above, so it's accepted until the rule
moves over.

## Layout references

The layout references stay in the view file index. "Go to related" and the toggle action read that
index, and variable tracking in layouts could later start from it. That work would need to know
which actions render a layout, so the class-level heuristics would not carry over.

## Element plugin fix moved

Fixing the walk for unprefixed elements moves to
[plugin-aware view keys](plugin-aware-view-keys.md), which needs the same rule for variables on all
versions. The bug here needs a shared element name and differently aliased controllers on CakePHP 2,
and its effect is limited to the type above. These notes carry over:

- Check the carried references at each exit from the walk: a class reference, a layout's subclasses
  and the default layout.
- A prefixed hop must not clear references already carried: in `element('Blog.a')` where `a` calls
  `element('b')`, `b` still follows the controller's plugin.
- The walk also passes through `$this->layout` assignments and `render()` calls in view files. No
  assumption test covers a layout named from a template.
- Track `(ViewFile, pending references)` as visited, and cache `resolvesTo` within a search.
- The shared helper takes the rendering class's plugin as input, as
  [elements in cell templates](cell-template-element-plugin.md) is still open.

`controller-side-helpers` should drop its layout tracking to match this decision when it next
merges `main`.

## Plan

1. Rewrite `LayoutHelperTest` around the rules above, and add tests for `AppController` aliases.
2. Remove the heuristics from `RenderingControllers`, and `frameworkControllerClass`.
3. Run the full suite.

## Tests

- `AppController` aliases: the alias replaces the convention type in a template, a subclass's alias
  wins in its template and the layout it names, the default layout unions the aliases without the
  convention type, and a controller extending `Controller` directly keeps the convention type.
- Layouts: named, inherited by subclasses, named only by an abstract class, default, and an element
  in a named layout and in the default layout.
- Plugins: each row of the table above. A plugin subclass of an app controller is dropped from the
  app's named layout when the plugin has its own copy. A plugin without its own default layout is in
  the app's default layout's union. `{Plugin}AppController` adds no convention type.

## Implementation Progress
