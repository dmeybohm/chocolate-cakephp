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

## Session #2: Fix Duplicated Data View Actions

### Problem

After fixing the "Nothing to show" issue, data view actions (e.g.,
"Create JSON View File", "Create XML View File") appeared duplicated
in the popup during remote development.

### Root Cause

`createViewActionPopupFromAllViewPaths()` adds one popup action per
entry in `dataViewPaths`. But `dataViewPaths` contains one `ViewPath`
per (extension × template directory). When multiple template directories
exist, each extension produces duplicate entries with the same label.

`defaultViewPaths` already avoids this by using `.first()`, but
`dataViewPaths` and `otherViewPaths` iterated all entries.

### Fix

Added `.distinctBy { it.label }` to both `otherViewPaths` and
`dataViewPaths` in `createViewActionPopupFromAllViewPaths()` in
`ViewNavigationPopup.kt`. Users who need a non-default template
directory can use "Create Custom View File".

### QEMU Test Environment

Added `dev/qemu/` scripts to set up a QEMU/KVM VM for simulating
JetBrains Gateway remote development:
- `setup-vm.sh` - Downloads Ubuntu 24.04 cloud image, creates
  cloud-init config with SSH key and JDK 17
- `start-vm.sh` - Boots the VM with KVM, SSH on port 2222
