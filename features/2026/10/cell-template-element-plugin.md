# Elements in cell templates

## Context

Follow-up to the [assumption tests](cakephp-behavior-probes.md) and split out of
[plugin-aware view keys](plugin-aware-view-keys.md). An unprefixed element in a controller's template
resolves against the plugin of the controller doing the rendering, not the plugin the template lives
in. The plugin-aware plan generalizes that to the class a walk reaches, which includes cells, but
nothing has tested a cell.

A cell renders in its own view, so its element may follow the cell's plugin rather than the plugin
of the controller or template that called `cell()`. From reading `CellTrait::_createCell`, the view
builder's plugin is set from the prefix in the `cell()` name, which suggests it does. This is
unverified.

## Open question

Which plugin does an unprefixed element resolve against inside a cell's template, on CakePHP 3, 4
and 5?

## Plan

1. **Assumption tests.** Add these to `CellTest` on each version, written as the assumption that an
   element follows the cell. Cells don't exist on CakePHP 2. Use a `shared` element present in the
   app, `Probe` and `Other`.

   | Caller | Cell | Cell template lives in | Assumed element |
   |---|---|---|---|
   | App template | `Probe.Gadget` | `Probe` | `Probe`'s |
   | App template | `Probe.Gadget` | The app, by fallback | `Probe`'s |
   | `Other` plugin template | `Probe.Gadget` | `Probe` | `Probe`'s |
   | `Probe` plugin template | `Lamp` (app) | The app | The app's |

   Also cover `$this->cell('Probe.Gadget')->render('other')`, in case naming a template changes it.
2. **Record the results.** Add the catalogue rows to the assumption tests README and the findings
   here.
3. **Update the plugin-aware plan.** If elements follow the cell, the plan's rule holds as written
   and only needs the tests cited. If not, change how
   `ViewVariableIndexService.forEachContributingSource` treats a walk reaching a cell.

This is an investigation of the framework. Any plugin code change belongs to the plugin-aware work.

## Implementation Progress
