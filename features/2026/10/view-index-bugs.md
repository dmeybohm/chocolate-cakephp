# View index bugs

## Context

Two bugs found while implementing `controller-side-helpers` (#309). This log records what each one
is, what the investigation on 2026-10-31 found, and the plan for fixing each. #309 has merged and
this branch has it.

## Bug 1: element references depend on when a file is indexed

### The bug

`elementPathPrefixFromSourceFile` (`ViewFileIndexService.kt`) walks up from the indexed file to a
`templates`, `Template` or `View` directory and then looks for a child named `element`, `Element` or
`Elements`. It returns null when none exists. Both indexers drop every element entry in that case:

- `ViewFileDataIndexer.indexElementCalls` stores no reference for any `$this->element()` call, so
  the element file has no incoming references.
- `ViewVariableASTDataIndexer.indexElementCall` stores no element data, so variables passed to the
  element are unknown inside it.

The index framework only reindexes a file when its own content changes. Creating a sibling directory
changes nothing about the caller, so a template indexed before `element/` existed keeps its empty
entries until something else edits it. In a test this shows up as an order dependency between
indexing and `addFileToProject`; `ControllerSideHelperTest` in `cake5` currently creates two
placeholder element files in `setUpTestFiles()` to work around it.

### What a user sees

Write `echo $this->element('nav');` in a template, then create `templates/element/nav.php` as the
project's first element. Until the template is touched again:

- Go to related from `nav.php` finds no usages (`ElementToUsagesGotoRelatedProvider`).
- Variables passed in the `element()` call are not known in `nav.php`.
- Helpers the rendering controller adds do not resolve in `nav.php`, because
  `RenderingControllers` walks back through the same references.

The same applies to a plugin's first element, and to a fresh checkout where indexing starts before
the working tree is fully populated.

### Why the existence check is unnecessary

The lookup side never consults the directory. It builds the key of an element file from its
physical path with `canonicalizeFilenameToKey`, and where it needs the element directory name it
takes `TemplatesDir.elementDirName`, which is fixed per version: `element` for `CakeFourTemplatesDir`,
`Element` for `CakeThreeTemplatesDir`, `Elements` for `CakeTwoTemplatesDir` (`CakeView.kt:310`,
`ElementToUsagesGotoRelatedProvider.kt:72`). So the only name the index can usefully store is the
version's name, and the existence check adds nothing. Its one extra behaviour, accepting a
wrongly-cased directory such as `templates/Element/` in a CakePHP 4 project, produces keys the
lookup side cannot match anyway, and CakePHP itself does not find such elements on a case-sensitive
filesystem.

Both indexers already resolve the templates directory of the file: `ViewVariableASTDataIndexer`
holds `templatesDir`, and `ViewFileDataIndexer.map` resolves it inside `referencePluginContext`
(through `TemplateDirStructure.ofSourceFile`) and discards it.

The September proposal [resolve-element-paths-at-lookup](../09/resolve-element-paths-at-lookup.md)
would fix this by storing logical element names and resolving the directory at lookup. That is more
than the bug needs: the prefix is derivable at index time from the file's own path, which the index
framework already tracks. The fix below supersedes that proposal for this problem.

### Plan

1. Replace the body of `elementPathPrefixFromSourceFile` so it derives the prefix from the templates
   directory type rather than from a child lookup. Give it the `TemplatesDir` instead of
   `projectDir`: `ViewPathPrefix(templatesDir.elementDirName + "/")`. Remove `findElementDir`.
2. `ViewVariableASTDataIndexer.indexViewFile` passes the `templatesDir` it already has.
3. `ViewFileDataIndexer.indexElementCalls` needs the `TemplatesDir`. Resolve it once in `map()` with
   `templatesDirectoryOfViewFile(project, settings, virtualFile)` and pass it down; a file with no
   templates directory (a controller or cell class) stores no element references, as now.
4. Bump `ViewFileIndex.getVersion()` from 24 to 25 and `ViewVariableIndex.getVersion()` from 21 to
   22, so templates indexed with missing entries are rebuilt on upgrade.
5. Remove the placeholder element files from `cake5/ControllerSideHelperTest.setUpTestFiles()` and
   the comment explaining them.

`indexRenderCalls` keeps using the more lenient `viewPathToTemplatesDirRoot` walk; its prefix is a
directory path and does not depend on siblings. Not touched.

### Tests

- Indexer unit tests, modelled on `ViewVariableASTDataIndexerTest` (drive `map()` with
  `FileContentImpl.createByFile`): a template under `cake5/templates/Movie/` calling
  `$this->element('nav', ['x' => $y])` in a project with no `element` directory. Assert the
  templates directory has no `element` child, then assert `ViewFileDataIndexer` produces the key
  `element/nav` and `ViewVariableASTDataIndexer` produces `elementDataKey("element/nav")`. Repeat
  for `cake3` (`Element/nav`) and `cake2` (`Elements/nav`).
- An end-to-end test in `cake5/ControllerSideHelperTest` that reproduces the intermittent failure
  deterministically: add the controller and the template with the element call, force the template
  to be indexed by querying it (for example `fmtTypesIn("Movie/index")`, or
  `ViewFileIndexService.referencingElementsInSmartReadAction(project, "Movie/index")`), then create
  the element file and assert the controller's helper type resolves in it.
- The existing `ElementToUsagesGotoRelatedTest` and `ViewVariableTest` element cases keep covering
  the normal order.

## Bug 2: the index reads `$helpers` on a view class as a load

### The bug

`ViewBuilder::build()` always passes the view its helpers, even an empty array, and the view
constructor's `isset()` holds for that, so a `$helpers` property on a view class is replaced by
whatever the controller configures. `testControllerBuilderReplacesViewHelpersProperty` in the
assumption suite records this on CakePHP 3, 4 and 5. CakePHP 2's `View::__construct()` copies the
controller's `helpers` as well, and `TemplateDirStructure.CakeTwo` has no view class receivers, so
the question only arises for 3 to 5. Only a view built by hand with `new` keeps the property.

`ConfiguredMemberDataIndexer` indexes the property as a `PROPERTY_ARRAY` load of `$helpers` like it
does for any class, and records a `PropertyDeclaration` for it.

### Readers after #309

| Reader | Handles it | How |
|---|---|---|
| `ViewHelperReceivers.loadedHelpers` / `helperMemberType` | yes | passes `ignoredProperties` returning `$helpers` for view-class receivers |
| `ConfiguredMemberFieldTypeProvider.fieldTypeOnClass` | yes | ignores `$helpers` when the receiver name ends in `View` |
| `ViewHelperInViewTypeProvider.complete`, no-view-file path | no | `configuredHelperType(... viewType(settings).types ...)` with no ignore |
| `ViewHelperInViewCompletionContributor`, no-view-file path | no | `loadedMembers(... viewType(settings).types ...)` with no ignore |
| `ViewHelperGotoDeclarationHandler` | n/a | reads a view file through `ViewHelperReceivers`, otherwise helper classes, where `$helpers` is real |
| `ViewHelperInViewHelperCompletionContributor` | n/a | helper classes only |
| `ViewHelperReceivers.aliasesOfRenderingClasses` | n/a | controllers and cells only |
| controller, table and association readers | n/a | other kinds |

The two readers that still see the load are their fallbacks for a view file that `isCakeViewFile`
accepts but `viewFileOf` returns null for. Both functions use `templatesDirectoryOfViewFile`, and
`viewFileOf` only adds `VfsUtil.getRelativePath` to the templates directory, which holds for any
file under it. So those paths are effectively unreachable, and no wrong types are expected in
reachable code now that #309 is in. What remains is that the rule lives in three call sites, by two
different mechanisms, and two fallbacks miss it.

### Should the indexer leave the property out?

No. The indexer sees one file and the unresolved `superFQN` of each class. It can recognise
`extends \Cake\View\View` but not `extends AppView`, so it would need a name heuristic that is
weaker than the one `ConfiguredMemberFieldTypeProvider` already uses. The indexed fact, that the
class declares `$helpers`, is also true; whether it has an effect depends on the receiver's role in
the framework, which only the chain resolution knows. `resolveChain` already filters both `loads`
and `declarations` by `ignoredProperties`, so the service is the layer that has every input.

### Plan

Move the view-class rule into `ConfiguredMemberService.resolveChain`, so every reader gets it:

1. After computing `chain`, detect whether it ends in `\Cake\View\View`, the way
   `CakeTwoBase.ofChain` does for CakePHP 2: the chain itself, or the `superFQN` of any class in it,
   is `\Cake\View\View` (case-insensitive). `classChain` stops at `frameworkBaseClasses` without
   recording them, so the `superFQN` check is what finds it.
2. When it does, add `HELPERS_PROPERTY` to the ignored properties for that chain. The caller's
   `ignoredProperties` stays for the rules that depend on the template layout, that is the
   controller and cell `$helpers` property that `TemplateDirStructure.readsHelpersProperty`
   governs on CakePHP 3 but not 4 and 5.
3. Drop the now-redundant copies: the `receiver in viewClasses` branch of
   `ViewHelperReceivers.ignoredProperties`, and the `isView` ignore in
   `ConfiguredMemberFieldTypeProvider.fieldTypeOnClass`. `isViewClassName()` stays for choosing
   the member kinds and the CakePHP 2 branch.
4. Helper classes (`\Cake\View\Helper`), cells and controllers are unaffected: their chains do not
   reach `\Cake\View\View`.

The smaller alternative is to pass the view-class ignore in the two fallback readers and leave the
three copies in place. It closes the gaps with less change, but the question this log asked is
where the rule belongs, and the chain resolution is the only place that answers it once.

### Tests

- `ConfiguredMemberService.memberType(project, listOf("\\App\\View\\AppView"), setOf(HELPER), "Fmt")`
  with an `AppView` whose only configuration is `$helpers = ['Fmt' => [...]]` returns null, with no
  `ignoredProperties` argument. This covers the centralised rule without any reader's lambda.
- The same call with `$this->loadHelper('Fmt', ...)` in `AppView::initialize()` still returns the
  class with `hasInitializeLoad`. `test a view class load wins over the controller's` covers the
  reader side already.
- A `$helpers` property on a helper class still loads: keep
  `test an incomplete helpers array keeps convention completion in a helper` and add a complete-array
  case if `ViewHelperInViewHelperCompletionContributor` has none.
- Existing: `test a helpers property on the view class is ignored` in `cake5` and `cake3`
  `ControllerSideHelperTest`. Add the `cake4` equivalent, which is missing.

## Related, not in scope

- `isCakeTwoController` (`ViewFileIndexService.kt`) reads `topDir.children` to tell a CakePHP 2
  controller from a 3+ one while indexing, so a controller indexed before its `templates` directory
  exists can be classified wrongly. Same class of bug, different feature; only with CakePHP 2 enabled.
- `cellDirNameFromSourceFile` (`CakeCell.kt`) looks for a `cell` or `Cell` sibling, but falls back
  to the version's default name when it is missing, so it has no bad outcome in practice.

## Order of work

1. Bug 1 indexer unit tests (expected to fail), then steps 1 to 4 of its plan, then step 5 and the
   end-to-end test. One commit for the fix, one for removing the workaround.
2. Bug 2 service test (expected to fail without a reader's lambda), then its plan, then the `cake4`
   test. One commit.
3. Run `cake5` tests first, then `cake3`, `cake4`, `cake2`, then the full suite once at the end.
