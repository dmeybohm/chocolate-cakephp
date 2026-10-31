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
