# Anchor configured plugin paths to an app root

## Status

Planned 2026-11-02, from review feedback on #321
([cake2-configured-plugin-views](cake2-configured-plugin-views.md)). Branched from
`cake2-configured-plugin-views`, since it builds on that branch's shared CakePHP 2 rule; rebase
target is `main` once #321 merges. No code yet.

## The problem

Configured plugin paths are relative to the app root, and the app root may be nested in the
project, so the rules that recognise a configured plugin from a file match its path as a suffix:

- `isDirectoryOfPluginPath` (`CakePlugins.kt`), behind `configuredCakeTwoPluginOfDirectory` and
  `isInConfiguredPlugin`. Since #321 this decides `templatesDirectoryOfViewFile`'s plugin `View`
  directories, `isCakeTwoController`'s configured plugins, `cakeTwoPluginName`, and the layout
  union's project controllers.
- `cakeThreePluginName` (`CakePlugins.kt`), which matches `cakeThreePluginConfigs` inline.

A suffix alone also matches a directory under some other root. With `lib/Shop` configured for
`cake2/lib/Shop`, an unrelated `examples/lib/Shop/View/index.ctp` is taken as Shop's view and named
`Shop`, and `examples/lib/Shop/Controller/*` as Shop's controllers. The shorter the configured
path, the more it matches: a path of just `Shop` matches every directory named `Shop`.

The lookup side does not have this problem. `pluginAndThemeTemplatePaths` resolves each configured
path from the `AppRootDirectory` it was given, so navigation into a plugin's templates only reaches
the configured one. Recognition is the half that cannot do this, since it starts from a file and has
no app root yet: a plugin outside the app directory, like `lib/Shop`, has no app directory above
it to walk up to.

## The rule

A directory is a configured plugin's directory when its path ends with `"/" + pluginPath`, and the
directory left once that suffix is removed is an app root: it contains the app directory the config's
version uses. For a `CakeTwoPluginConfig` that is `cake2AppDirectory`; for a `CakeThreePluginConfig`,
`appDirectory`. Both are resolved with `findRelativeFile`, as `appRootDirectoryFromViewFile`
does, so nested settings such as `src/app` work.

`cake2/lib/Shop` passes, because `cake2/app` exists. `examples/lib/Shop` fails, because
`examples/app` does not. A config for `app/Plugin/DebugKit/` still passes, since its root is the one
holding `app`.

The remaining root must also not be a plugin itself, judged by its parent being named `plugins`.
A CakePHP 3+ plugin has its own `src`, so without this the decoy comes back one level down: with
`vendor/acme/blog` configured, `plugins/Foo/vendor/acme/blog` leaves `plugins/Foo`, which contains
`src` and would pass. That copy appears when `composer install` is run inside a plugin checked out
under `plugins/`. A CakePHP 2 plugin never contains `app`, so this only changes what CakePHP 3+
configs accept, but it applies to both. Only the directory-name test is used: excluding roots that
are configured plugins would make the rule call itself, for a nesting that is unlikely.

The check reads the VFS, as the old project-directory lookup did; it does not depend on the IDE
project directory, so it holds under a nested root.

## Plan

1. No assumption test is needed. What a configured path is relative to is the plugin's own
   convention, not CakePHP's: `CakePlugin::load()` takes an absolute `path`, and
   `testPluginLoadedWithExplicitPathRendersItsTemplate` already pins that a plugin loaded from any
   path renders its own templates. Confirm with the settings form (`PluginForm`) that paths are
   saved relative to the app root and cannot point above it; if they can, record that case here.
2. Tests, expected to fail first:
   - cake2: with `lib/Shop` configured under `cake2/`, a view at `examples/lib/Shop/View/...` has no
     templates directory and no plugin name, and a controller at `examples/lib/Shop/Controller/...`
     is not indexed as a CakePHP 2 controller or counted by `isInConfiguredPlugin`.
   - cake2: the existing `ConfiguredPluginViewsTest`, `ConfiguredPluginTest` and
     `BothVersionsLayoutNavigationTest` cases keep passing, as the positive side.
   - cake5 (then cake3/cake4 if cheap): the same decoy for `cakeThreePluginName` and the CakePHP 3+
     branch of `isInConfiguredPlugin`, using a configured plugin path beside an `src` directory and
     an unrelated copy elsewhere in the project.
   - A nested `cake2AppDirectory` (`src/app`) still recognises its configured plugin.
   - cake5: with `vendor/acme/blog` configured, files under `plugins/Foo/vendor/acme/blog`, where
     `plugins/Foo` has its own `src`, are not named `blog` by `cakeThreePluginName` and are not
     counted by `isInConfiguredPlugin`, while the copy under the app root still is.
3. Replace `isDirectoryOfPluginPath` with a check that takes the config's app directory setting
   and verifies the remaining root contains it and is not under a `plugins` directory. Route `cakeThreePluginName`'s inline suffix match
   through the same function, so the CakePHP 2 and 3+ rules cannot drift apart again.
   `isInConfiguredPlugin` picks the app directory by the config's version.
4. Bump the `ViewFileIndex` and `ViewVariableIndex` versions, since plugin recognition reaches
   their keys through `templatesDirectoryOfViewFile`, `isCakeTwoController` and
   `pluginOfClassFile`. After #321 they are at 29 and 23.
5. Targeted tests per version, then the full suite before opening the PR.

## Not in scope

- The directory-name clauses (`Plugin`/`plugins` for CakePHP 2, `plugins` and the override
  directory for 3+). They name a plugin from its location, not from a config, and match how
  CakePHP itself searches.
- Cost: the extra lookup runs only for directories whose path already ends with a configured path,
  so it should not show up in indexing. Revisit only if profiling says otherwise.
