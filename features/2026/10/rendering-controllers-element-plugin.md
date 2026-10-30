# RenderingControllers element plugin

## Context

Follow-up to the [assumption tests](cakephp-behavior-probes.md) and split out of
[plugin-aware view keys](plugin-aware-view-keys.md). On all four CakePHP versions, an unprefixed
element resolves against the plugin of the controller doing the rendering, not the plugin the
including template lives in:

- `testElementInFallbackTemplateResolvesInControllerPlugin`
- `testElementInPrefixedTemplateResolvesInControllerPlugin`
- `testElementInOtherPluginTemplateResolvesInControllerPlugin`

`RenderingControllers.of` walks from a view file up through the files that include it, and at each
hop it checks `resolvesTo(reference.pluginPrefix ?: including.plugin, current)`. That uses the
including file's location, so it looks wrong for those cases. It is used for CakePHP 2 only, through
`ViewHelperReceivers`, to find the controllers whose helpers apply to a view file.

This comes from reading the code. No failure has been reproduced yet.

## Suspected failures

A plugin controller `Blog` renders the app's `Articles/index` by fallback, and that template calls
`$this->element('shared')`. Both the app and `Blog` have a `shared` element.

- Opening `Blog`'s `shared` element should find `Blog`'s controller, but the hop is checked as the
  app's reference and dropped.
- Opening the app's `shared` element should not find `Blog`'s controller, but the hop is kept and
  the walk continues to the template, which `Blog`'s controller reaches by fallback.

The app controller rendering `render('Blog.x')` is the mirror case, with the element checked as
`Blog`'s when it should be the app's.

Likely effect: helper completion and lookup in these elements use the wrong controller's
configuration. It needs an element name shared by a plugin and the app, so it should be uncommon.

## Plan

1. **Reproduce.** Add failing cake2 tests for the cases above. If they pass, record why here and
   stop.
2. **Check unprefixed references at the class.** A prefixed reference keeps being checked at its
   hop. An unprefixed one from a template or element is carried along the walk and checked against
   the plugin of each controller the walk reaches. Reuse `resolvesTo`.
3. **Shared helper.** Put the walk's rule where
   `ViewVariableIndexService.forEachContributingSource` can reuse it for
   [plugin-aware view keys](plugin-aware-view-keys.md), without changing that walk here.

The visited set in the walk tracks view files alone. A file can now be reached with different
pending references, so step 2 has to decide what it tracks.

## Tests

Fixtures on cake2: an app controller and a plugin controller, an app and a plugin `shared` element,
and an app template reached by fallback.

- Each `shared` element finds only the controllers that really render it.
- Helpers configured on the plugin controller apply in the plugin's element and not the app's.
- A prefixed element reference is unchanged: `element('Blog.shared')` belongs to `Blog` from anywhere.
- An element including another element follows the same rule.

## Implementation Progress
