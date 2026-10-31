# View index bugs to investigate

## Context

Two bugs found while implementing `controller-side-helpers` (#309). They are recorded here for
possible investigation, and neither has been looked into beyond what's below.

## Element references depend on when a file is indexed

`elementPathPrefixFromSourceFile` looks for the element directory while a file is being indexed, and
`indexElementCalls` stores nothing when it isn't found. So a file indexed before its `element/`
directory exists has no element references until it is next reindexed.

This showed up as a test in `ControllerSideHelperTest` failing intermittently, as the order of
indexing and of creating the directory varied. That test's setup now creates the directory first,
which works around the bug without fixing it.

To find out:

- Whether this happens in real use, such as when adding the first element to a plugin, and what the
  user sees.
- Whether the prefix needs the directory to exist, given that the key could come from the templates
  directory alone.

## The index reads `$helpers` on a view class as a load

`ViewBuilder::build()` always passes the view its helpers, even an empty array, and the view
constructor's `isset()` holds for that, so a `$helpers` property on a view class is replaced whatever
the controller configures. `testControllerBuilderReplacesViewHelpersProperty` in the assumption suite
records this on CakePHP 3, 4 and 5. Only a view built by hand with `new` keeps the property.
`ConfiguredMemberDataIndexer` still indexes the property as a load.

#309 makes helper resolution ignore these loads, in `ViewHelperReceivers` and
`ConfiguredMemberFieldTypeProvider`, so the wrong types should be gone once it merges, and this
branch doesn't have that change yet.

To find out:

- Whether anything else reads these loads after #309.
- Whether that's enough, or whether the indexer should leave them out. It can't always tell a view
  class from its name alone.
