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
