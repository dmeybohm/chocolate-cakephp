# Layout navigation references

> **Note:** This is a minimal log summarizing an earlier discussion, with no investigation behind it
> yet. Flesh it out before starting.

## Context

CakePHP 2 layout references are in the view file index, and "go to related" and the toggle action
read that index, so a layout should already link to the controllers that name it. That hasn't been
tested. CakePHP 3 to 5 don't have these references.

[The `RenderingControllers` simplification](rendering-controllers-element-plugin.md) stops helper
lookup from reading the references, so navigation becomes their main use, which is why this is its
own change.

## Plan

1. **CakePHP 2 tests.** Cover layout navigation using the existing references.
2. **CakePHP 3 to 5 references.** Index `viewBuilder()->setLayout()` and `layout()`, and the
   deprecated `$this->layout` on CakePHP 3. Today the indexer reads only the CakePHP 2 forms: the
   `$layout` property, `$this->layout` assignments and `render()`'s second argument. This needs an
   index version bump.

## Things to watch

- `layoutDirectory` controls both layout indexing and `isLayoutKey`. `controller-side-helpers` needs
  `isLayoutKey` on CakePHP 3 to 5, and setting `layoutDirectory` there also switches on the existing
  indexing. Either split the two uses or coordinate the changes.
- Variable tracking in layouts could later start from these references, but is out of scope. It also
  needs to know which actions render a layout, and has to deal with the 15-lookup limit in
  `forEachContributingSource`.

## Plugins

These come from reading the code, and haven't been tested.

- **`Plugin.name` layouts on CakePHP 3 to 5.** `resolveName` doesn't split prefixes there, so
  `setLayout('Foo.admin')` would keep the whole name and get the wrong key. Step 1 of
  [plugin-aware view keys](plugin-aware-view-keys.md) adds the split. Either wait for it, or leave
  prefixed layouts out and say so.
- **App and plugin layouts share keys.** The plugin stays out of the index keys, so an app and a
  plugin controller naming `admin` are stored under the same key. Going to related from either
  `admin` layout would list both, even when the plugin has its own copy. Templates already collide
  this way. Step 2 of the plugin-aware plan is where the toggle action and
  `ViewToControllerGotoRelatedProvider` start filtering by plugin, so reuse that filter.
- **CakePHP 2 tests shouldn't record the collision as correct.** Those two providers don't appear to
  filter by plugin yet. Limit the first tests to layouts without a same-named plugin copy, plus the
  fallback case, where a plugin controller naming a layout only the app has links to it. Add the
  collision cases along with the filter.

### Ordering

The CakePHP 2 tests can go ahead now. The CakePHP 3 to 5 references are easiest after steps 1 and 2
of the plugin-aware plan. They could land earlier if prefixed layouts are left out and the collision
is accepted as a known limitation.

## Implementation Progress
