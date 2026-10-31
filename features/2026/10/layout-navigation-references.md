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

## Implementation Progress
