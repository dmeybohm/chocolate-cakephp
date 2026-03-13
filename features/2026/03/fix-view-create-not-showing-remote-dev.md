# Fix "Create View File" Popup Showing "Nothing to show" in Remote Development

## Problem

When using PhpStorm remote development (JetBrains Gateway), clicking the
cake/plus gutter icon to create a view file shows a popup with "Nothing to
show" instead of the expected action items. This works fine in local
development.

## Root Cause

`DataManager.getInstance().getDataContext(e.component)` does not include
`CommonDataKeys.PROJECT` in remote dev because the UI component lives on
the client while project data is on the backend.
`CreateViewFileAction.update()` checks `event.getData(CommonDataKeys.PROJECT)`
and disables the action when it's null, causing all actions to be hidden.

## Solution

Wrap the DataContext with `SimpleDataContext` that explicitly adds
`CommonDataKeys.PROJECT` before passing it to `createActionGroupPopup()`.
The `setParent(parentContext)` call preserves all original context keys as
a fallback, so local behavior is unchanged.

### Files Changed

1. **`NavigateToViewPopupHandler.kt`** - Moved `val project = elt?.project`
   before DataContext creation, wrapped context with SimpleDataContext
   adding PROJECT key.

2. **`ToggleBetweenControllerAndViewAction.kt`** - In `tryToNavigateToView()`,
   wrapped the async DataContext with SimpleDataContext adding PROJECT key
   (already available in scope).
