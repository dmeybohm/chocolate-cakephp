# Remove the stub vendor

## Context

Follow-up to the [real CakePHP vendor spike](real-cakephp-vendor-spike.md) and the
[`__get` dilution fix](fix-magic-get-type-dilution.md). Since that work, every test runs two
ways: against a hand-written stub of the framework, `src/test/fixtures/cakeN/vendor/cakephp.php`
(8 files, about 1,200 lines in all), or against a pinned real release attached as a module
library with `-PcakeVendor=real`. CI runs both as a matrix.

The stub mode no longer earns its keep:

- The stubs encode our own assumptions about the framework. The spike found 8 plugin defects
  the stubs hid (session 2), and a Cake 2 stub declaring a `findById()` the real framework only
  has through `__call` (session 6).
- 140 references to the stub file across 110 test classes, a `configureByFiles` helper in
  `BaseTestCase` whose only job is to drop them in real mode, and `assumeStubVendor` /
  `usesRealVendor` guards.
- Speed is a wash. The full suite on a warm index was 7 m 34 s stub against 7 m 27 s real. On a
  cold index real mode pays roughly 45 to 50 s per Cake version for indexing, which is what CI
  pays today in the real job anyway.
- Every new test has to be written to pass both ways, with a reason string for any skip.

## What has to survive

Two Cake 2 tests only run in stub mode because they test the plugin when the framework is not
indexed, which is a real user scenario (vendor excluded, or a project opened without it):

- `cake2/FrameworkNotIndexedHelperTest`: a layout finds a direct `Controller` subclass when the
  framework class is not indexed.
- `cake2/ConfiguredControllerTest > test without the framework and declarations convention stays`.

The mode switch is replaced by a per-class opt-out: a test class declares that it attaches no
framework, and gets the default light project with no library.

## Plan

1. **`BaseTestCase`.** Drop the `isEnabled` check; a test with a `cakeMajorVersion` always gets
   `RealCakeVendorProjectDescriptor` unless it overrides a new `attachesFramework` to `false`.
   Remove `usesRealVendor`, `assumeStubVendor` and the `configureByFiles` helper. Callers go back
   to `myFixture.configureByFiles`.
2. **`RealCakeVendorProjectDescriptor`.** Remove `isEnabled`. When the Gradle system property is
   not set, fall back to `build/cakephp-vendor/<major>` so tests run from the IDE's JUnit runner
   once the vendor has been fetched, and say which Gradle task fetches it otherwise.
3. **Fixtures.** Delete the 8 stub files. The other fixture content under `cake3/vendor` (the
   `acme/vendored` and `test/test_plugin` packages) is real test data and stays.
4. **Tests.** Remove the stub path from every `configureByFiles` call, and the call itself where
   the stub was the only file. Cake 2 `ConfiguredControllerTest` loses `withFramework()`; its
   no-framework test moves to a `FrameworkNotIndexedControllerTest` that opts out, sharing the
   fixture helpers through a `ConfiguredControllerFixtureTestCase`, the way `HelperFixtureTestCase`
   already works. `FrameworkNotIndexedHelperTest` opts out too. The Cake 5
   `ConfiguredComponentTest` assumption on `usesRealVendor` becomes unconditional.
5. **Build.** Drop the `cakeVendor` property. The `test` task always depends on the fetch tasks
   and sets the per-version path properties. The `chocolateCakePHP.vendor` property goes.
6. **CI.** Collapse the `build.yml` matrix to one job, keeping the release zip cache and the
   coverage upload.

`AssumptionAwareJUnit38Runner` stays: it makes `Assume` work correctly under the Vintage engine
for any future test, and is not tied to the vendor mode.

## Implementation Progress
