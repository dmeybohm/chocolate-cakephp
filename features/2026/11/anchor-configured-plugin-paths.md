# Anchor configured plugin paths to an app root

## Status

Planned 2026-11-02, from review feedback on #321
([cake2-configured-plugin-views](cake2-configured-plugin-views.md)). Branched from
`cake2-configured-plugin-views`, since it builds on that branch's shared CakePHP 2 rule; rebase
target is `main` once #321 merges. Implemented; see Implementation Progress.

## The problem

Configured plugin paths are relative to the app root, and the app root may be nested in the
project, so the rules that recognise a configured plugin from a file match its path as a suffix:

- `isDirectoryOfPluginPath` (`CakePlugins.kt`), behind `configuredCakeTwoPluginOfDirectory` and
  `isInConfiguredPlugin`. Since #321 this decides `templatesDirectoryOfViewFile`'s plugin `View`
  directories, `isCakeTwoController`'s configured plugins, `cakeTwoPluginName`, and the layout
  union's project controllers.
- `cakeThreePluginName` (`CakePlugins.kt`), which matches `cakeThreePluginConfigs` inline.

A suffix alone also matches a directory under some other root. With `lib/Shop` configured for
`cake2/lib/Shop`, an unrelated `examples/lib/Shop/View/index.ctp`, where `examples/` is not an
app, is taken as Shop's view and named
`Shop`, and `examples/lib/Shop/Controller/*` as Shop's controllers. The shorter the configured
path, the more it matches: a path of just `Shop` matches every directory named `Shop`.

The lookup side does not have this problem. `pluginAndThemeTemplatePaths` resolves each configured
path from the `AppRootDirectory` it was given, so navigation into a plugin's templates only reaches
the configured one. Recognition is the half that cannot do this, since it starts from a file and has
no app root yet: a plugin outside the app directory, like `lib/Shop`, has no app directory above
it to walk up to.

## Path convention

A configured plugin or theme path is relative to the app root: the directory that contains the app
directory (`cake2AppDirectory` for CakePHP 2, `appDirectory` for 3+). With the app at `cake2/app`
and the plugin at `cake2/lib/Shop`, the path is `lib/Shop`, not `cake2/lib/Shop`; with a CakePHP 5
app at `cake5/src`, its plugins are `plugins/Blog` and `vendor/acme/blog`. When the app root is the
project root, as it usually is, the two readings give the same path.

This is what the code already does: `pluginAndThemeTemplatePaths` resolves each path from the
`AppRootDirectory` of the file it starts from, which `appRootDirectoryFromViewFile` finds by walking
up to a directory containing the app directory, falling back to the project root. The test fixtures
and #321 use it too. The settings form is the odd one out: `PluginForm.form` tells users plugin
paths (line 62) and theme paths (line 104) are "relative to the project root", and
`EditPluginEntryDialog` saves what is typed without validating it.

Settings are project-wide, so a configured path applies relative to every app root in the project.
The lookup side already treats it that way: from a view in a second app, `examples/app`,
navigation looks for `examples/lib/Shop`. Recognition follows it, so `examples/lib/Shop` is that
app's configured plugin when `examples/app` exists. Tying a path to one app root would break
projects with several apps, with no principled way to pick the root.

## The rule

A directory is a configured plugin's directory when its path ends with `"/" + pluginPath`, and the
directory left once that suffix is removed is an app root: it contains the app directory the config's
version uses. For a `CakeTwoPluginConfig` that is `cake2AppDirectory`; for a `CakeThreePluginConfig`,
`appDirectory`. Both are resolved with `findRelativeFile`, as `appRootDirectoryFromViewFile`
does, so nested settings such as `src/app` work.

`cake2/lib/Shop` passes, because `cake2/app` exists. `examples/lib/Shop` fails when `examples/app`
does not exist, and passes when it does, by the path convention above. A config for `app/Plugin/DebugKit/` still passes, since its root is the one
holding `app`.

The remaining root must also not be a plugin itself, judged by its parent being named `plugins`.
A CakePHP 3+ plugin has its own `src`, so without this the decoy comes back one level down: with
`vendor/acme/blog` configured, `plugins/Foo/vendor/acme/blog` leaves `plugins/Foo`, which contains
`src` and would pass. That copy appears when `composer install` is run inside a plugin checked out
under `plugins/`. A CakePHP 2 plugin never contains `app`, so this only changes what CakePHP 3+
configs accept, but it applies to both. Only the directory-name test is used: excluding roots that
are configured plugins would make the rule call itself, for a nesting that is unlikely.

For settings entered under the form's old wording, a remaining root that is the IDE project
directory itself is also accepted. With the app nested at `cake2/app`, a user who followed "relative
to the project root" entered `cake2/lib/Shop`; cutting that off leaves the project directory, which
contains no `app`, so the anchor alone would stop recognising a config that the suffix match
accepts today. Lookup does not resolve such a path (it looks for `cake2/cake2/lib/Shop`), which is
left as it is; see Not in scope.

The check reads the VFS, as the old project-directory lookup did. It anchors to the app root, not
the IDE project directory, so it holds under a nested root; the project directory is used only for
the legacy paths above.

## Plan

1. Assumption tests on CakePHP 3, 4 and 5, before the plugin code, each with a row in the
   catalogue in `src/test/php/README.md`:
   - A plugin loaded with an explicit `path` outside `plugins/` renders its own template. Extend
     `testPluginLoadedWithExplicitPathRendersItsTemplate`, today CakePHP 2 only, to 3, 4 and 5 with
     a plugin at `{root}/packages/Pathed`, as the CakePHP 2 fixture has: `Plugin::load()` with
     `path` on 3, `addPlugin()` with `path` on 4 and 5, rendering from `{path}/src/Template` on 3
     and `{path}/templates` on 4 and 5. The cake5 `vendor/acme/blog` tests rely on this.
   - A plugin whose only copy is inside another plugin's `vendor/`, at
     `plugins/Foo/vendor/acme/blog`, is not found when loaded by name with no path: expected
     `MissingPluginException`. This backs the plugin-root exclusion: CakePHP looks only in the
     plugin paths and the Composer plugin manifest, so such a copy is never the plugin it loads.

   The path convention, its application under every app root, and the fallback for paths entered
   relative to the project root are the plugin's own settings rules, not CakePHP behaviour:
   `CakePlugin::load()` and `addPlugin()` take absolute paths. They need no assumption test.
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
   - cake2: with both `cake2/app` and `examples/app` present, `examples/lib/Shop` is recognised as
     `Shop`, pinning that a configured path applies under every app root. This one passes before
     the change too; it guards the convention.
   - cake2: a config entered relative to the project root, `cake2/lib/Shop`, is still recognised
     with the app at `cake2/app`.
3. Replace `isDirectoryOfPluginPath` with a check that takes the config's app directory setting
   and verifies the remaining root contains it and is not under a `plugins` directory. Route `cakeThreePluginName`'s inline suffix match
   through the same function, so the CakePHP 2 and 3+ rules cannot drift apart again.
   `isInConfiguredPlugin` picks the app directory by the config's version.
4. Reword the settings UI so it gives one instruction: paths are relative to the app root, the
   directory containing `app` or `src`, which is usually the project root. That covers the plugin
   and theme explanations in `PluginForm.form` (lines 62 and 104), and the field labels "Plugin
   Path from Project Root" in `EditPluginEntryDialog.form` (line 78) and "Theme Path from Project
   Root" in `EditThemeEntryDialog.form` (line 27), which become "... from App Root".
5. Bump the `ViewFileIndex` and `ViewVariableIndex` versions, since plugin recognition reaches
   their keys through `templatesDirectoryOfViewFile`, `isCakeTwoController` and
   `pluginOfClassFile`. After #321 they are at 29 and 23.
6. Targeted tests per version, then the full suite before opening the PR.

## Not in scope

- The directory-name clauses (`Plugin`/`plugins` for CakePHP 2, `plugins` and the override
  directory for 3+). They name a plugin from its location, not from a config, and match how
  CakePHP itself searches.
- Making lookup resolve paths entered relative to the project root. Recognition keeps accepting
  them, but navigation into such a plugin already fails and still will. Migrating saved settings,
  or validating paths in `EditPluginEntryDialog`, would be the fix.
- Paths containing `..`. A suffix with `..` never matches a VFS path, so such a plugin is not
  recognised, as it is not today.
- Cost: the extra lookup runs only for directories whose path already ends with a configured path,
  so it should not show up in indexing. Revisit only if profiling says otherwise.

## Implementation Progress

### Session #1

Added the assumption tests of step 1, in `TemplateResolutionTest` on CakePHP 3, 4 and 5. Both pass,
and agree with the plan.

- `testPluginLoadedWithExplicitPathRendersItsTemplate` now runs on every version. The apps add a
  `Pathed` plugin with `addPlugin('Pathed', ['path' => ROOT/packages/Pathed/])`, routed at
  `/pathed`, and `/pathed/trinkets/index` renders `pathed:Trinkets/index` from
  `packages/Pathed/src/Template` on 3 and `packages/Pathed/templates` on 4 and 5. So a configured
  CakePHP 3+ plugin's templates are its own wherever it sits, which the cake5 `vendor/acme/blog`
  tests rely on. (CakePHP 3.10 has `addPlugin()` too, so all three use it.)
- `testPluginInsideAnotherPluginsVendorDirectoryIsNotFound`: `plugins/Nester` is a plugin with its
  own `vendor/acme/nested` and a `vendor/cakephp-plugins.php` listing it, as `composer install`
  inside the plugin would leave. `addPlugin('Acme/Nested')` throws `MissingPluginException`
  ("could not be found"), while `Nester` itself loads from `plugins/Nester/`. CakePHP does not
  search a plugin's own `vendor` or read its manifest, so such a copy is never a plugin it loads,
  which is what the plugin-root exclusion assumes.

The full assumption suite passes on 2, 3, 4 and 5. The one PHPUnit warning on CakePHP 4 is
`ViewHelperTest::testControllerHelpersPropertyRaisesWarning`'s existing deprecation notice. No
plugin code yet.

### Session #2

Implemented steps 2 to 5.

- Tests: `AnchoredPluginPathTest` on cake2 and cake5. On cake2, with `lib/Shop` configured under
  `cake2/`, `examples/lib/Shop` is not the plugin (no templates directory, no plugin name, not in a
  configured plugin, not a CakePHP 2 controller), and the guards pass: the app root's own copy,
  `examples/lib/Shop` once `examples/app` exists, a nested `src/app` setting, and a path entered as
  `cake2/lib/Shop`. On cake5, with `vendor/acme/blog` configured, the copies at
  `examples/vendor/acme/blog` and `plugins/Foo/vendor/acme/blog` are not `Blog`, while the app root's
  is. Only the three decoy tests failed first; the guards passed before and after.
- Rule: `isDirectoryOfConfiguredPlugin` in `CakePlugins.kt` replaces `isDirectoryOfPluginPath`. It
  takes the config, finds the root by walking up as many directories as the path has components,
  accepts it if it is the project directory, and otherwise requires it to hold the config's app
  directory and not to sit under `plugins`. `configuredCakeTwoPluginOfDirectory`,
  `isInConfiguredPlugin` and `cakeThreePluginName` all use it.
- The CakePHP 2 controller check needs the project directory for the legacy fallback, so
  `lookupControllerFileInfo`, `isCakeTwoController` and `isCakeTwoPluginDirectory` now take it.
- Settings UI: both explanations in `PluginForm.form` and both edit dialog labels now say the app
  root.
- `ViewFileIndex` is at 30 and `ViewVariableIndex` at 24.
- `test configured vendor plugin is a plugin` in the cake3, cake4 and cake5 `PluginAwareViewKeysTest`
  had no file under the app directory, so its fixture had no app root. Each now adds an
  `Application.php` under the app source directory, as a real app has.

- The full suite then failed in `cake5/ControllerSideHelperTest`'s two tests of a configured
  plugin attached as a library (from #319). Their fixture writes `vendor/test/test_plugin` to a
  temporary directory outside the project with no app directory beside it, so the anchor found no
  app root. In a real project Composer's `vendor/` sits under the app root, beside `src`, so
  `libraryController` now also creates the app directory in the temporary directory. The full suite
  then passed (1792 tests).

One behaviour to note: a configured plugin of an app nested in the project is now recognised only
when the app directory setting matches the app's layout. When the app root is the project
directory, the fallback accepts it regardless.
