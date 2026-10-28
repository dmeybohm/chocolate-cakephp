# Real CakePHP vendor in the test suite (spike)

## Context

The test fixtures stand in for the framework with a hand-rolled stub per version,
`src/test/fixtures/cakeN/vendor/cakephp.php` (44 to 172 lines each). The stubs keep tests fast
and readable, but they encode our own assumptions about the framework's API. When an
assumption is wrong (generics on `Table`/`SelectQuery`, trait namespaces, return types that
moved between minors, Cake 2 magic properties), the tests still pass.

The question for this spike: can the suite load the real, pinned CakePHP source without
making it significantly slower?

## Approach

- **Download, don't vendor.** A Gradle task, `fetchCakePhpVendor`, downloads a pinned tag's
  zip from GitHub, checks its SHA-256, caches the zip under the Gradle user home, and extracts
  only `src/` into `build/cakephp-vendor/5`. The `test` task depends on it and passes the path
  as the `chocolateCakePHP.vendor.cake5` system property. Composer isn't required.
- **Mount as a library, not as fixture files.** `configureByFiles` copies into the light
  fixture's `temp://` filesystem, which is reset between tests, so loading roughly 770
  framework files that way would reindex them for every test. Instead,
  `RealCakeVendorProjectDescriptor` attaches the directory as a module library. The light
  project is reused for as long as consecutive tests share an equal descriptor, so the
  framework is indexed once per descriptor.
- **Benchmark on an existing class.** `ViewVariableRealVendorTest` subclasses the cake5
  `ViewVariableTest` (51 tests), drops the stub from the fixture files, and swaps in the real
  vendor descriptor.

## Implementation Progress

### Session #1

Pinned CakePHP 5.4.3: 766 PHP files under `src/`. Timings for the 51 tests of the cake5
`ViewVariableTest`, on an 8-core machine with a background load average of about 3.7. The
"first test" figure includes test application startup and, for a cold index, indexing the
framework.

| Run | Total | First test | Median |
|---|---|---|---|
| Stub, cold index | 88.7 s | 24.7 s | 1.13 s |
| Real vendor, cold index | 131.9 s | 71.6 s | 1.17 s |
| Real vendor, warm index | 85.4 s | 21.9 s | 1.25 s |
| Stub, after real vendor in same JVM | 47.8 s | 1.35 s | 0.94 s |

One cold real-vendor run measured 209 s for the first test. It did not reproduce and is
treated as noise.

Findings:

- **Indexing is a one-off cost of about 45 to 50 s per Cake version** on a cold index. Indexes
  persist in `build/idea-sandbox/.../system-test/index`, so local reruns pay nothing, but a
  fresh CI checkout pays it every time unless that directory is cached.
- **Per-test cost doesn't change.** Median test time is the same with the real framework
  attached as a library.
- **Switching descriptors is cheap.** A stub-based class running after the real-vendor class in
  the same JVM had a 1.35 s first test.
- **The PHP plugin (2023.2) fails to build stubs for `ORM/Rule/IsUnique.php`.** Type inference
  for `$this->_options = $options + $this->_options` trips `RecursionManager`. The IDE ignores
  this, but the test framework turns it into an error. The test disables the missed-cache and
  recursion-prevention assertions, after loading the test application first, because loading it
  turns them back on. A failed index is persisted ("IDEA has to be restarted"), so a warm-index
  run can hide this; verify any change of this kind on a cold index.
- **`ViewVariableTest` doesn't exercise the framework.** It passes against an empty vendor
  library too, so it is a valid timing benchmark but says nothing about correctness. A
  correctness comparison needs classes that resolve framework members, such as
  `GetTableLocatorTest` or `CustomFinderTest`.

Next steps, if this goes ahead: add a small real-vendor suite per Cake version made of tests
that depend on the framework API, decide whether to cache the sandbox index in CI, and
consider adding `cakephp/chronos` and the PSR packages, which are absent from the extracted
`src/` and leave some framework types unresolved.

### Session #2

Direction change: rather than a hand-picked real-vendor suite, every test runs both ways. CI
will run a stub job and a real-vendor job in parallel, through a matrix in `build.yml`, once
the real job can pass.

- **The switch.** `-PcakeVendor=real` (the default is `stub`) sets the
  `chocolateCakePHP.vendor` system property and fetches the framework. A test opts in by
  declaring `cakeMajorVersion` in its version base class. `BaseTestCase` then returns
  `RealCakeVendorProjectDescriptor.forVersion(...)`, applies the VFS and `RecursionManager`
  setup from session 1, and its `configureByFiles` helper leaves out `*/vendor/cakephp.php`
  (skipping the call entirely if nothing is left). So far only `Cake5BaseTestCase` opts in, and
  the cake5 tests that load the stub call the helper.
- **`getProjectDescriptor()` must be nullable.** `BasePlatformTestCase` returns `null` to mean
  the default descriptor.

Cake 5 results with a warm index: stub mode 297/297 pass in 222 s, and real mode 289/297 pass in
201 s. One earlier real-mode run took two hours because a single test spanned the laptop
suspending: the lid was closed, and `lid-delay-suspend` suspends as root, which bypasses
`systemd-inhibit` block locks on systemd 255. In isolation that test takes 25 s. Treat any
outlier test time as suspect until the journal is checked.

The 8 real-mode failures, in `ConfiguredComponentTest` and `ConfiguredHelperTest`, are a real
plugin defect that the stubs hid. The real `Controller::__get` is declared `@return
Component|Table|null`, and `View::__get` and `Helper::__get` `@return Helper|null`. PhpStorm
unions these into every undeclared property, so a configured `$this->Legacy` resolves to
`MovieMetadataComponent|Component|Table|null|mixed`. With the real framework, completion on
`$this->Legacy->` offered 123 items, every `Table` method among them. With the stub it
offered only `generateMetadata`.

Root cause, from the PHP plugin 232.8660.205 bytecode: for a field signature
(`#P#C\Class.Name`), `PhpIndexImpl.getMembers` calls `PhpClassImpl.findFieldsByName`. Only when
no field is found does it fall back to the class's `__get` method, and that method's return type
joins the union. Type providers can only add types, and the PHP plugin has no extension point
for contributing class members, so the plugin cannot suppress the fallback directly. A
`@property` tag on the class makes `findFieldsByName` succeed. A probe in real mode confirmed
that `@property \App\Controller\Component\MovieMetadataComponent $Legacy` on the controller
narrows `$this->Legacy` to exactly that class. This is the approach the community
`cakephp-ide-helper` tool takes.

### Session #3

The fix for the `__get` dilution gets its own branch and feature log. On this branch, the 8
affected tests are skipped in real mode.

- **Skipping.** `BaseTestCase.assumeStubVendor(reason)` calls `Assume.assumeTrue`. JUnit 4's
  `JUnit38ClassRunner`, which the Vintage engine uses for these JUnit 3 style classes, reports
  any exception as a failure, `AssumptionViolatedException` included. So `BaseTestCase` is
  annotated `@RunWith(AssumptionAwareJUnit38Runner::class)` (the annotation is inherited). That
  runner wraps the stock adapting listener and reports assumption violations as assumption
  failures, which Gradle records as skipped. Cake 5 real mode: 8 skipped, 0 failed.

Newest PHP plugin API check (263.7307.44, PhpStorm 2026.3), for the fix branch:

- `PhpIndexImpl.getMembers` is unchanged. A field signature still falls back to the class's
  `__get` whenever `PhpClassImpl.findFieldsByName` finds nothing, and there is still no
  extension point for contributing class members. Type providers still only add types.
- The one new extension point that touches type resolution is `typeInferenceExclusion`
  (`PhpTypeInferenceExclusion.isExcludedFromTypeInference(PsiElement)`, absent in 232).
  `PhpTypeInfo.getTypeCached` and `getTypeFromAST` return `PhpType.EMPTY` for excluded
  elements. Its only built-in use excludes whole compiled Twig templates. Excluding a configured
  `$this->Field` reference might drop PhpStorm's own `#P` field signature, and with it the
  `__get` fallback. Whether plugin type providers survive the short-circuit can't be read from
  the bytecode, and it needs the test platform raised from 2023.2. It is also a blunt switch,
  and it would be consulted mid-inference, where index lookups risk recursion.
- Remaining options for the fix branch: that experiment on 2026.3 (which may raise the minimum
  IDE version), a `@property` quick-fix of the kind `cakephp-ide-helper` generates, or filtering
  completion results only.

### Session #4

Cake 4 joined real mode with CakePHP 4.6.5 (711 PHP files under `src/`). `build.gradle.kts` now
registers one `fetchCake<N>Vendor` task per entry in a `cakeReleases` map. `Cake4BaseTestCase`
declares `cakeMajorVersion = 4`, and the cake4 tests that load the stub use the `configureByFiles`
helper.

Three version tests extend `BaseTestCase` directly, so they never picked up the version from a
base class: `cake5/NestedAppDirectoryTest`, `cake5/AutoDetectionGatingTest` and
`cake4/NestedAppDirectoryTest`. Until now they ran against the stubs even in real mode. Each now
declares its version itself.

Results: stub mode cake4 204/204 pass. Real mode, cake4 and cake5 in one run: cake5 297 with 8
skipped, cake4 204 with 10 failures. Switching between the two library descriptors in one JVM
caused no problems, and the first index of the Cake 4 source raised no indexing errors.

All 10 Cake 4 failures are the same `__get` dilution, with Cake 4's own declared types:
`Controller::__get` returns `RepositoryInterface|null` (for the old `loadModel()` magic),
`View::__get` returns `Helper|null`, and `Helper::__get` returns `Helper|null|void`. On top of
the component and helper tests, it hits two `ConfiguredTableTest` cases for `loadModel`. On Cake 4
the leak into component completion is `RepositoryInterface`'s `find`/`get`/`save`/`delete`.
These are skipped in real mode like the Cake 5 ones, so real mode cake4 is 204 with 10 skipped.

### Session #5

Cake 3 joined real mode with CakePHP 3.10.5 (617 PHP files under `src/`, the same layout as 4
and 5). `Cake3BaseTestCase` and `cake3/NestedAppDirectoryTest` declare `cakeMajorVersion = 3`.

`cake3/ControllerLineMarkerTest` uses the files that `configureByFiles` returns
(`files.last()`), so the `BaseTestCase` helper now returns `Array<PsiFile>` like the fixture
method, and an empty array when nothing is left. No test indexes the result by position, so
dropping the stub doesn't shift anything a test depends on.

Results: stub mode cake3 244/244 pass. Real mode cake3 244 with 10 failures, and the first index
of the Cake 3 source raised no indexing errors. The 10 are the same test names as on Cake 4,
again the `__get` dilution, with Cake 3's types: `Controller::__get` declares `bool|object`.
`View::__get` declares only `mixed`, so PhpStorm also infers `ViewBlock|array` from its body.
`Helper::__get` declares `Helper|null`, plus an inferred `array`. Skipped in real mode, which
leaves real mode cake3 at 244 with 10 skipped.
