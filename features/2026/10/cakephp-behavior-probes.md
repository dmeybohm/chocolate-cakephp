# CakePHP assumption tests

## Context

The plugin is built on assumptions about what the framework does at runtime: which directories
a template name resolves through, whether a plugin's templates are found without a `Plugin.`
prefix, what `$uses = false` loads, how an element falls back from a plugin to the app, which
variables a `set()` call makes visible in a layout. Each assumption is baked into an index, a
type provider or a navigation handler, and across four major versions some of them are wrong.
When one is wrong the plugin's own tests still pass, because the fixtures encode the same
assumption.

The `real-cakephp-vendor-spike` branch covered the static half of this: the test suite can
index the real framework source so that the *types* the plugin sees are the framework's. This
feature covers the dynamic half: a suite of integration tests, written against real, pinned
CakePHP 2, 3, 4 and 5 installs, that encodes every framework behavior the plugin depends on and
verifies it. The suite is a dependency of the plugin in the same sense the vendor pins are.
When a feature relies on a framework behavior, the behavior is asserted here first, on every
version it applies to, and the feature log links the test.

The first assumption to pin down is the one that prompted this: whether CakePHP renders a
plugin's templates when the name is not prefixed with the plugin, and how each version differs.

## Goals

- One command, `scripts/run-cakephp-assumption-tests.sh test`, runs the whole suite against
  every supported major version from a clean checkout, with no PHP on the host. It builds the
  images and installs the dependencies it finds missing.
- Every assumption is one test method with a name that states the behavior, in a class named
  for the plugin area that depends on it, so that a feature log or a code comment can cite it.
- The suite catches drift: when a pinned framework release moves, a behavior change fails a
  test before any plugin feature is built on the old behavior.
- Adding an assumption is cheap: a test method per version, and whatever templates or plugin
  directories it needs on disk.

Non-goals: the suite does not test the plugin, does not run from Gradle, and does not replace
the real-vendor test mode. It asserts what the framework does, not what the plugin does with it.

## Design

### Why containers

No single PHP release runs all four frameworks:

| CakePHP | Pinned | PHP constraint in its `composer.json` |
|---|---|---|
| 2.10.24 | same as `build.gradle.kts` | `>=5.3.0,<8.0.0` |
| 3.10.5 | same | `>=5.6.0,<8.0.0` |
| 4.6.5 | same | `>=7.4.0,<9` |
| 5.4.3 | same | `>=8.2` |

Cake 2 and 3 exclude PHP 8 outright, and Cake 5 needs 8.2. So each version runs in a
container. Two images cover everything: PHP 7.4 for Cake 2 and 3, PHP 8.3 for Cake 4 and 5.
Podman is the runtime, rootless, so files written by
`composer install` inside the container are owned by the host user. Docker works as a fallback
because the two share a command line; the runner picks `$CONTAINER_RUNTIME`, else `podman`,
else `docker`. GitHub's `ubuntu-latest` runners ship with both.

The pinned versions are the same four that `build.gradle.kts` fetches for the real-vendor test
mode, so the static and dynamic checks describe the same releases. The suite installs from its own
`composer.lock`, so a pin moved in one place and not the other would go unnoticed; the runner's
`check-pins` subcommand compares the two lists, and CI runs it (see below).

### Why PHPUnit and the framework's own test harness

Each version ships a test harness that already solves the bootstrapping problem, and each
container pins its own PHPUnit, so the usual version conflict never arises:

- Cake 3, 4 and 5: `Cake\TestSuite\TestCase` sets the app namespace, loads plugins and
  overrides `App.paths`. `IntegrationTestTrait` (Cake 3.7+) issues a request to a controller
  and asserts on the rendered body, so a template resolution assumption is tested through the
  real dispatcher. Each version's `composer.json` pins the PHPUnit range its release declares in
  `require-dev`: `^5.7.14|^6.0` for 3.10.5, `^8.5 || ^9.3` for 4.6.5 and
  `^11.5.3 || ^12.1.3 || ^13.0` for 5.4.3.
- Cake 2: `lib/Cake/TestSuite` provides `CakeTestCase` and `ControllerTestCase`, whose
  `testAction()` renders through the real `Dispatcher`. 2.10.24 declares PHPUnit `^3.7`, but
  `CakeTestCase` extends the underscore-named `PHPUnit_Framework_TestCase`, which exists
  through PHPUnit 5.7, and 5.7 runs on PHP 7.4. Pin `^5.7` and confirm in step 2.

An assertion is the recorded assumption. When a pin moves and a test fails, PHPUnit's failure
message is the drift report, and a missing template exception already lists the paths searched.
When a behavior is unknown, the test is written as the assumption the plugin would like to make,
and the failure says what the framework does instead.

### Layout

The suite lives under `src/test/php/`, the Gradle place for test sources in a language the
build has no source set for. Gradle ignores it, and it sits next to `src/test/fixtures`, the
hand-written stubs these tests keep honest. The runner lives in `scripts/` with the other
project scripts:

```
scripts/
  run-cakephp-assumption-tests.sh   build | install | test, per version or all
src/test/php/
  README.md                 how to run, how to add an assumption, the catalogue (below)
  images/
    php74/Containerfile     php:7.4-cli + intl + composer
    php83/Containerfile     php:8.3-cli + intl + composer
  cake2/                    the smallest app Cake 2 will boot, in Cake 2 layout
    composer.json           cakephp/cakephp 2.10.24 and its PHPUnit
    composer.lock
    phpunit.xml
    app/
      Config/               core.php, bootstrap.php, database.php (SQLite test datasource)
      Controller/  Model/  View/  Plugin/   fixtures the tests need
      Test/Case/            the test classes
  cake3/                    same shape in Cake 3 layout: src/, plugins/, tests/
  cake4/  cake5/            same in Cake 4/5 layout: src/, templates/, plugins/, tests/
```

`vendor/` and `tmp/` directories are ignored by git; `composer.lock` files are committed so the
resolved dependency set is reproducible. Each app has one `Probe` plugin, with a controller, a
few templates and elements, and the app-side override directory for it, because most template
and element assumptions need a plugin on both sides.

Tests that need models use SQLite in memory through `pdo_sqlite`, which the official images
include, with the framework's own fixture mechanism. No MySQL or Postgres.

### Test organisation

Test classes are named for the plugin area whose code depends on the assumptions, and the same
class and method names are used in every version where the assumption applies:

| Class | Plugin code that depends on it |
|---|---|
| `TemplateResolutionTest` | `view/TemplateDirStructure`, `TemplateGotoDeclarationHandler`, `controller/ControllerMethodLineMarker`, `CreateViewFileAction` |
| `ElementResolutionTest` | `view/ElementGotoDeclarationHandler`, `ElementToUsagesGotoRelatedProvider` |
| `ViewVariableTest` | `view/viewvariableindex`, `ViewVariableTypeProvider`, `UndefinedViewVariableInspectionSuppressor` |
| `ConfiguredMemberTest` | `configuredmemberindex`, `ControllerFieldTypeProvider`, the helper and component completion contributors |
| `ModelAssociationTest` | the nested `$this->Movie->Director` path in `ControllerFieldTypeProvider`, table locator resolution |
| `CellTest` (Cake 3+) | the `cell-support` branch |

A method asserts one behavior and is named for it, for example
`testPluginControllerRendersUnprefixedTemplateFromPlugin`. Where versions disagree, each
version's class asserts its own behavior under the same method name, and the README catalogue
records the difference in one line. Where a behavior does not exist on a version (cells on
Cake 2), the class or method is absent there and the catalogue says so.

Each test class carries a doc comment naming the plugin area in one sentence. The per-method
rationale goes in the README catalogue, not in the test, so the test files stay readable as a
list of assertions.

### The catalogue

The README holds a table, one row per assumption: the behavior in plain words, the test method,
which versions it holds on, and the plugin feature or feature log that relies on it. It is the
index a feature log links to. Rows are added when the test is added, so the table and the suite
cannot drift apart without a reviewer noticing.

### The runner

`scripts/run-cakephp-assumption-tests.sh` has four subcommands, the first three taking an
optional list of versions. It finds the suite relative to its own location, so it runs from any
directory. The name deliberately does not end in `-test.sh`, because `scripts/run-all-tests.sh`
executes every file matching that pattern as a shell test.

- `build` builds the two images, tagged `chocolate-cake-assumptions:php74` and `:php83`.
- `install [2 3 4 5]` runs `composer install` in each version directory inside its image,
  with the host Composer cache mounted so repeated installs are quick.
- `test [2 3 4 5] [-- phpunit args]` is the one command for a clean checkout. It first runs
  `build` for any image the runtime does not have and `install` for any version whose
  `vendor/` directory is missing, then runs `vendor/bin/phpunit` in each version directory
  inside its image, with `src/test/php` mounted read-write at `/suite`, forwarding any
  extra arguments (a `--filter`, `--testdox`). It prints each version's PHPUnit output under a
  heading and exits non-zero if any version fails.
- `check-pins` reads the version from each `cakeN/composer.json` and the matching `CakeRelease`
  entry in `build.gradle.kts`, and exits non-zero if any pair differs. It needs no container.

The whole tree is mounted rather than one version, so `composer install` writes `vendor/` back
to the host and the per-version apps can share a `Probe` plugin fixture if that turns out to be
worth it.

### CI

A separate workflow, `cake-assumptions.yml`, runs on pushes and pull requests that touch
`src/test/php/**`, `scripts/run-cakephp-assumption-tests.sh` or `build.gradle.kts`. A first job
runs `check-pins`, so a framework pin moved in the Gradle build without the matching
`composer.json` change fails before anything is downloaded. The suite then runs as a four-way
version matrix with `fail-fast: false`. Each job builds its image, restores `vendor/` from a
cache keyed on that version's `composer.lock`, and runs the suite. It is kept out of
`build.yml` so the plugin build is not slowed by framework downloads, and so a failing
assumption surfaces on its own PR rather than blocking an unrelated one.

## First assumptions

### `TemplateResolutionTest`

The question that motivated the suite, split into the cases the plugin's view navigation
depends on. Every template prints a marker naming its own file, so each assertion is on the
marker that was rendered, or on the paths listed by the missing template exception.

1. A controller in plugin `Probe` renders its `index` action with no explicit template. The
   plugin's own `index` template renders.
2. The same controller calls `render('other')`, unprefixed. The plugin's `other` renders.
3. A template exists both in the plugin and in the app's override location for the plugin
   (`app/View/Plugin/Probe/` on Cake 2, `src/Template/Plugin/Probe/` on 3,
   `templates/plugin/Probe/` on 4 and 5). The app override wins.
4. An app controller, not in a plugin, calls `render('index')` where the template exists only
   inside the plugin. This is the case in doubt. The assumption as written is that it does not
   fall through, and the failure on any version that does will say so.
5. The same app controller with `render('Probe.index')`, prefixed, renders the plugin template.
6. A prefixed route (`Admin`) resolves to the prefix subdirectory, on each version's layout.

### `ElementResolutionTest`

From a plugin template, `$this->element('item')` unprefixed: plugin first, then app. From an
app template, an unprefixed element never reaches a plugin, and `Probe.item` does. Shares the
`Probe` plugin fixture.

### Candidates for the next pass

Assumptions the recent feature logs lean on, to be added as the corresponding plugin code is
touched:

- `$uses = true`, `false`, `null` and `array()` on a Cake 2 controller, with and without a
  `loadModel()` call on top (`configuration-code-disambiguation-cake2`).
- A helper or component configured with `className` is reachable under its alias only, not under
  the class's conventional name.
- Variables passed to `set()` are visible in the layout and in elements; variables passed in an
  element's data array are visible only inside that element (`support-element-data-variables`).
- A `hasAndBelongsToMany` `with` join model is loadable as a property on Cake 2.

## Implementation steps

1. Images and runner: the two Containerfiles, the `build` subcommand of
   `scripts/run-cakephp-assumption-tests.sh`, runtime detection. Verify both
   images build rootless with Podman.
2. The four apps with `composer.json`, the PHPUnit range each release declares, the committed
   locks, a `phpunit.xml` and test bootstrap per version, and one `FrameworkVersionTest` per
   version asserting `Configure::version()` matches the pin, and the `check-pins` subcommand.
   This proves each harness boots and gives the `test` subcommand its first green run.
3. The `Probe` plugin fixture and the app-side override directory in each version's layout.
4. `TemplateResolutionTest` on Cake 5 first, then 4, 3 and 2. Record each version's answers in
   the catalogue as they are learned.
5. `ElementResolutionTest`.
6. README with the catalogue, `.gitignore` entries, and the `cake-assumptions.yml` workflow.

Commit after each step.

## Open questions

Both questions from planning were settled during implementation.

- Cake 2 deprecation notices did not reach the PHPUnit output. `core.php` sets the error level to
  `E_ALL & ~E_DEPRECATED`, and a test can change that level when a deprecation is under test.
- `IntegrationTestTrait` needs only a small `Application` class and a routes file, so all of
  Cake 3, 4 and 5 test through the real dispatcher. Cake 3 also calls `useHttpServer(true)`.

## Implementation Progress

### Session #1

All six steps are implemented, and the suite passes on all four versions.

Findings from the first run, also recorded in the README catalogue:

- The case in doubt is settled. An app controller's unprefixed `render()` does not reach a plugin's
  template on any version, and the `Plugin.name` form does.
- A plugin controller finds its own unprefixed template, and the app override wins, on every version.
- A plugin controller whose template is missing from the plugin falls back to the app's template
  of the same name, on every version.
- Cake 2 differs for a missing element. It raises a notice that renders into the page and the rest
  of the page continues, while Cake 3, 4 and 5 throw `MissingElementException`.
- Cake 2 has no prefix subdirectory. An `admin` route renders `Widgets/admin_index`.

Changes from the plan:

- The PHP 7.4 image is based on Debian 11, whose packages have moved to `archive.debian.org`, so
  the Containerfile points apt there.
- Composer now refuses to resolve packages with security advisories. The Cake 2 and 3 manifests
  allow the advisories on their locked packages by ID, including the transitive `symfony/yaml` and
  `laminas-diactoros`. Blocking stays on, so a new advisory stops the next resolve for review.
- Cake 5.3 and later deprecate plugins without a plugin class, so Cake 5 has `Probe\ProbePlugin`.
- Cake 2's `CakeTestCase::expectException()` also asserts on the message, so the Cake 2 tests use
  `setExpectedException()`.
- The fixtures are separate per version. Sharing one `Probe` plugin was not worth it, because the
  template extensions and directory layouts differ.

The `cake-assumptions.yml` workflow has not run on GitHub yet.
