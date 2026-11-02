# CakePHP 2 controller detection depends on sibling directories while indexing

## Status

Recorded 2026-10-31 while planning [view-index-bugs](view-index-bugs.md). Not investigated beyond
what is written here.

## The bug

`isCakeTwoController` (`ViewFileIndexService.kt`) decides whether a controller file is a CakePHP 2
controller by walking up to its `Controller` directory, checking that the directory above matches
the configured CakePHP 2 app directory, and then reading `topDir.children` and
`topSourceDir.children` to confirm that no `templates` or `Template` directory exists beside it.

Its only caller is `lookupControllerFileInfo`, which `TemplateDirStructure.ofSourceFile` calls for
controllers when both CakePHP 2 and 3 are enabled, to choose the CakePHP 2 structure or the 3+ one.
The indexers reach that while indexing a controller: `ViewFileDataIndexer` through
`referencePluginContext` (how a `Plugin.name` reference is split) and `layoutStructuresOf` (which
versions' layouts a controller can name), and the view variable index through `pluginOfClassFile`
(which plugin the controller belongs to).

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
- What the user would see; probably layout and plugin-prefixed references stored under the wrong
  version's keys, so navigation from those calls misses until the controller is edited.
- Whether the version can be decided from the controller file's own path and the settings alone, or
  whether it needs the templates directory and so belongs at lookup time.

## Implementation Progress

### Session #1

Merged `origin/main` twice (2026-11-02). What changed for this bug:

- #309 (`view-index-bugs`) fixed the element directory dependency by deriving the prefix from the
  file's own templates directory, not a sibling lookup. It did not touch `isCakeTwoController`.
- 3b3ed077 added `isCakeTwoPluginDirectory`, so CakePHP 2 plugin controllers are now recognised from
  their path and settings alone. The sibling `templates`/`Template` check is still there for app
  controllers, so the bug remains for them.
- #317 ([goto-related-method-names](goto-related-method-names.md)) pinned with
  `testCamelCaseActionRendersImplicitTemplate` that CakePHP 2 underscores an implicit template name
  as 3+ does, so `fullImplicitViewPath` now always underscores and `indexImplicitRender` no longer
  calls `lookupControllerFileInfo`. That removed the implicit render keys from this bug. The
  `ofSourceFile` uses described above are what is left.

An assumption test for the same behaviour was added here and then removed again, as it duplicated
the one from #317. No other assumption test applies: what is left depends on how the project is laid
out and configured, not on what CakePHP does at runtime.
