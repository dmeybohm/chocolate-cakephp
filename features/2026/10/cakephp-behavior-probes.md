# CakePHP behavior probes

## Context

Plugin features are built on assumptions about what the framework does at runtime: which
directories a view name resolves through, whether a plugin's templates are found without a
`Plugin.` prefix, what `$uses = false` loads, how an element falls back from a plugin to the
app. Those assumptions are usually made from memory or from reading one version's source, and
across four major versions they are sometimes wrong. When one is wrong the plugin's tests still
pass, because the tests encode the same assumption.

The `real-cakephp-vendor-spike` branch addressed the static half of this: the test suite can now
index the real framework source so that *types* match what PhpStorm would see. This feature
covers the dynamic half: a harness that runs small PHP scripts against real, pinned CakePHP 2,
3, 4 and 5 installs and records what each version actually does, so a question can be answered
by running a probe before a feature is designed around the answer.

The first question to answer is the one that prompted this: does CakePHP render a plugin's
views when the template name is not prefixed with the plugin name, and which version does what.

## Goals

- One command answers a behavior question for every supported major version, from a clean
  checkout, with no PHP on the host.
- Each answer is recorded next to the question, with the framework version it was observed on,
  so it can be cited from a feature log and re-checked when a pin moves.
- Adding a question is cheap: a directory with a short Markdown file and one PHP script per
  version it applies to.

Non-goals: this is not a test suite for the plugin, it does not run from Gradle, and it does not
replace the real-vendor test mode. It answers "what does the framework do", not "what does the
plugin do".

## Design

### Why containers

No single PHP release runs all four frameworks:

| CakePHP | Pinned | PHP supported |
|---|---|---|
| 2.10.24 | same as `build.gradle.kts` | 5.3 to 7.4; breaks on 8 |
| 3.10.5 | same | 5.6 to 8.1 |
| 4.6.5 | same | 7.4 to 8.4 |
| 5.4.3 | same | 8.1 to 8.4 |

So the harness runs each version in a container. Two images cover everything: PHP 7.4 for
Cake 2 and 3, PHP 8.3 for Cake 4 and 5. Podman is the runtime, rootless, which has the side
effect that files written by `composer install` inside the container are owned by the host
user. Docker works as a fallback because the two share a command line; the runner picks
`$CONTAINER_RUNTIME`, else `podman`, else `docker`. GitHub's `ubuntu-latest` runners ship with
both.

The pinned versions are the same four that `build.gradle.kts` fetches for the real-vendor test
mode, so the static and dynamic checks describe the same releases. Keeping the two lists in
step is a manual step for now, noted in the README; generating one from the other is not worth
the coupling yet.

### Layout

Everything lives under `dev/cake-probes/`, beside the other developer tooling in `dev/`:

```
dev/cake-probes/
  README.md                how to run, how to add a probe
  run.sh                   the runner (see below)
  images/
    php74/Containerfile    php:7.4-cli + intl + composer
    php83/Containerfile    php:8.3-cli + intl + composer
  cake2/                   one minimal app per major version
    composer.json          cakephp/cakephp 2.10.24
    composer.lock
    bootstrap.php          loads the framework, points it at a probe's fixtures
    app/                   the smallest config the version needs to boot
  cake3/  cake4/  cake5/   same shape, each version's own layout
  probes/
    plugin-view-unprefixed/
      question.md          the question, and the recorded answer per version
      expected             one `cakeN=<answer>` per line, once an answer is accepted
      cake2.php            the probe for Cake 2
      cake3.php  cake4.php  cake5.php
      fixtures/
        cake2/             templates, plugins and config the probe needs, in Cake 2 layout
        cake3/  cake4/  cake5/
```

`vendor/` directories are ignored by git; `composer.lock` files are committed so the resolved
dependency set is reproducible.

### Images

Each Containerfile starts from the official `php:<ver>-cli` image, installs the `intl`
extension (Cake 3 and later require it; `mbstring` and `pdo_sqlite` are already built in), and
copies the `composer` binary from the `composer:2` image. The runner builds an image when it is
missing, tagged `chocolate-cake-probes:php74` and `:php83`. Nothing else is installed: probes
that need a database use SQLite in memory.

### Per-version apps and bootstraps

Each `cakeN/` directory is the smallest thing that version will boot as an application, with
no database, no sessions and caching sent to a temporary directory. The bootstrap for each
version takes two things from the environment: `PROBE_DIR`, the probe's directory, and the
version number it is for. It loads the framework and then points the framework's path
configuration at `$PROBE_DIR/fixtures/cakeN/`:

- Cake 2: `App::build()` adds `View`, `Plugin`, `Controller` and `Model` paths under the
  fixtures directory, ahead of the skeleton `app/`.
- Cake 3: `Configure::write('App.paths.templates', ...)` and `App.paths.plugins`.
- Cake 4 and 5: the same `App.paths` keys, and plugins are added through `Plugin::getCollection()`
  or the `Application` class as the probe needs.

Probes declare any controllers, helpers or models they need inline in the probe script, in the
`App` namespace (or the global namespace for Cake 2), before the framework asks for them. A
class that is already declared short-circuits autoloading in every version, so no per-probe
autoloader is needed. Fixtures are only the files the framework must find on disk: templates,
elements, layouts and plugin directories.

### The probe contract

A probe is a PHP script that prints a single answer line on stdout, of the form:

```
ANSWER: <short value>
```

followed by any detail it wants to show, such as the list of paths the framework searched.
Anything printed before the `ANSWER:` line is treated as detail too. The script exits non-zero
only when it could not run (framework failed to boot, fixture missing); an unexpected framework
behavior is still an answer. Answers are short tokens or paths, not sentences, so that they can
be compared.

A probe file that is missing for a version means the question does not apply to it, and the
runner reports `n/a`.

### Recording answers

`question.md` holds the question in plain words, what each sub-question is checking, and a
table of the answer per version with the framework version it was observed on. The `expected`
file holds the accepted answers in machine form, one `cakeN=<value>` per line. Until a question
has an `expected` file the runner shows its answers as `new`; once it has one, each version
shows `ok` or `drift`, and the runner exits non-zero on any `drift`. That is what makes the
harness useful when a pin moves to a newer release: run it, and any behavior change shows up
before a feature is built on the old answer.

The answers themselves are the deliverable. A feature log that depends on one should link the
probe directory rather than restating the behavior from memory.

### The runner

`dev/cake-probes/run.sh` has three subcommands:

- `run.sh build` builds the two images.
- `run.sh install [cakeN...]` runs `composer install` in each version directory inside its
  image, with the host Composer cache mounted so repeated installs are quick. `run` does this
  implicitly when a `vendor/` directory is missing.
- `run.sh [--version N] [probe...]` runs every probe (or the named ones) for every version (or
  the named one), each as `php /probes/cakeN/bootstrap.php /probes/probes/<name>/cakeN.php`
  inside the right image, with `dev/cake-probes` mounted read-write at `/probes` and `PROBE_DIR`
  set. It prints one row per probe and version, with the answer and `ok`/`drift`/`new`/`n/a`, and
  `--verbose` shows each probe's detail lines.

The whole `dev/cake-probes` tree is mounted rather than just one version, so a probe can read a
sibling fixture or a shared helper, and so `composer install` writes `vendor/` back to the host.

### CI

A separate workflow, `cake-probes.yml`, runs on pushes and pull requests that touch
`dev/cake-probes/**`. It builds the images, restores the four `vendor/` directories from a cache
keyed on the `composer.lock` files, and runs every probe. A `drift` fails the job. It is kept
out of `build.yml` so the plugin build is not slowed by a framework download, and so a probe
that is still `new` does not block a plugin PR.

## First probes

### `plugin-view-unprefixed`

The question that motivated the harness, split into the cases the plugin cares about:

1. A controller that belongs to plugin `Probe` renders its `index` action with no explicit
   template name. Does the framework find `Probe`'s own `index` template?
2. The same controller calls `render('other')`, unprefixed. Does that resolve in the plugin's
   template directory?
3. The same controller renders a template that exists only in the app's override location for
   the plugin (`app/View/Plugin/Probe/...` on Cake 2, `src/Template/Plugin/Probe/...` on 3,
   `templates/plugin/Probe/...` on 4 and 5). Which one wins when both exist?
4. An app controller (not in a plugin) calls `render('index')` where the template exists only
   inside the plugin. Does the framework fall through into the plugin without a prefix?
5. The same app controller with `render('Probe.index')`, prefixed, as the control case.

Each probe renders with a view whose templates each print a marker naming the file they are, so
the answer is the marker that was rendered, or `missing` with the paths listed in the missing
template exception. Expected shape of the answer: cases 1 to 3 and 5 render; case 4 is the one
in doubt and is probably `missing` on every version, but that is what the probe is for.

### `element-plugin-fallback`

From a plugin view, `$this->element('item')` with no prefix: plugin first, then app, or app
only? And from an app view, does an unprefixed element name ever reach a plugin? Same shape as
above and shares the fixture tree.

### Candidates for later

Questions that have come up in recent feature logs, to be turned into probes when a feature
needs them:

- `$uses = true` versus `false`, `null` and `array()` on a Cake 2 controller, and whether
  `loadModel()` on top of each still populates the property.
- Whether a helper configured with `className` is also available under its original alias.
- Template path for prefixed routes (`Admin` prefix) across versions.
- Which variables set with `set()` in a controller are visible inside elements, layouts and
  cells on each version.

## Implementation steps

1. Images and runner skeleton: the two Containerfiles, `run.sh build`, and the runtime
   detection. Verify both images build rootless.
2. The four per-version apps with `composer.json`, the committed locks, and a bootstrap that
   boots the framework and renders nothing. A trivial `framework-version` probe that prints
   `Configure::version()` proves each bootstrap, and gives the harness its first `expected`
   file.
3. The probe contract in `run.sh`: discovery, `n/a`, `ANSWER:` parsing, `expected` comparison,
   exit status, `--verbose`.
4. `plugin-view-unprefixed` on Cake 5 first, then 4, 3 and 2, fixing each bootstrap's path
   configuration as it goes. Record the answers in `question.md` and `expected`.
5. `element-plugin-fallback`.
6. README, the `.gitignore` entries, and the `cake-probes.yml` workflow.

Commit after each step.

## Open questions

- Cake 2 on PHP 7.4 emits deprecation notices from a few framework files. The bootstrap should
  set `error_reporting` so they do not end up interleaved with probe output, but a probe should
  still be able to turn them back on when the deprecation itself is the question.
- Whether `question.md` should carry the answers or only link to `expected`. The current plan
  has both, with `question.md` as the readable record and `expected` as the machine one; if that
  drifts in practice, fold the table into a comment in `expected`.
