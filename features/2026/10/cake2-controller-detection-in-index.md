# CakePHP 2 controller detection depends on sibling directories while indexing

## Status

Recorded 2026-10-31 while planning [view-index-bugs](view-index-bugs.md). Not investigated beyond
what is written here.

## The bug

`isCakeTwoController` (`ViewFileIndexService.kt`) decides whether a controller file is a CakePHP 2
controller by walking up to its `Controller` directory, checking that the directory above matches
the configured CakePHP 2 app directory, and then reading `topDir.children` and
`topSourceDir.children` to confirm that no `templates` or `Template` directory exists beside it.

`ViewFileDataIndexer` reaches it through `lookupControllerFileInfo`, which `TemplateDirStructure.ofSourceFile`
calls for controllers when both CakePHP 2 and 3 are enabled, and `indexImplicitRender` uses the
result to choose between the CakePHP 2 and 3+ view path conventions (`fullImplicitViewPath` converts
the action name to underscores only for 3+).

The index framework reindexes a file only when its own content changes. The existence of a sibling
`templates` or `Template` directory is not part of the file, so a controller indexed before that
directory exists is classified as a CakePHP 2 controller and keeps that classification until it is
edited. The same class of bug as the element directory dependency in `view-index-bugs`, in a
different feature.

## When it applies

Only when CakePHP 2 is enabled alongside CakePHP 3+, and only for a project whose CakePHP 2 app
directory setting also matches a directory in the 3+ layout. With CakePHP 2 alone every controller is
treated as a CakePHP 2 one without this check.

## Not yet known

- Whether a real project hits this: it needs a fresh checkout or a new project where controllers
  are indexed before the templates directory exists, under mixed-version settings.
- What the user would see; probably implicit render references stored under the wrong key, so
  controller-to-view navigation misses until the controller is edited.
- Whether the version can be decided from the controller file's own path and the settings alone, or
  whether it needs the templates directory and so belongs at lookup time.

## Implementation Progress

### Session #1

Merged `origin/main` (2026-11-02). What changed for this bug:

- #309 (`view-index-bugs`) fixed the element directory dependency by deriving the prefix from the
  file's own templates directory, not a sibling lookup. It did not touch `isCakeTwoController`.
- 3b3ed077 added `isCakeTwoPluginDirectory`, so CakePHP 2 plugin controllers are now recognised from
  their path and settings alone. The sibling `templates`/`Template` check is still there for app
  controllers, so the bug remains for them.

Added the assumption test `testCamelCaseActionResolvesToImplicitTemplate` on 2 to 5. It was written
expecting CakePHP 2 to keep the action name as it is, as `fullImplicitViewPath` assumes, and it
failed: CakePHP 2.10.24 looked for `Widgets/camel_case.ctp`. `View::_getViewFileName` runs
`Inflector::underscore` on any template name without a slash, the same as 3+. The test now records
that, and it passes on every version.

What follows from that:

- The implicit render key does not depend on the version. `fullImplicitViewPath` could always
  underscore, which removes one of the two places the indexer reads `isCakeTwo`.
- The other place remains: `TemplateDirStructure.ofSourceFile` (via `ReferencePluginContext` in
  `ViewFileDataIndexer` and `pluginOfClassFile` in the view variable index) still uses
  `lookupControllerFileInfo` to pick the CakePHP 2 structure for a controller. That structure decides
  how plugins are named, so the sibling directory dependency stays in the index until it is fixed too.
- A separate bug, not limited to the index: CakePHP 2 navigation and view creation never underscore
  (`addViewFilenameExtension`'s `CakeTwoTemplatesDir` branch, and the `isCakeTwo` flag in
  `fullImplicitViewPath`). A CakePHP 2 action `fooBar` is linked to `fooBar.ctp`, but CakePHP renders
  `foo_bar.ctp`. Lowercase-only action names are unaffected. This probably needs its own branch.
