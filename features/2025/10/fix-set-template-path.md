# Fix setTemplatePath Support for TemplateGotoDeclarationHandler

## Problem
The `TemplateGotoDeclarationHandler` needed to support navigation from `$this->viewBuilder()->setTemplate()` calls when a preceding `$this->viewBuilder()->setTemplatePath()` call has set a custom template directory.

## Test Cases
1. **Single setTemplatePath**: When `setTemplatePath('Movie/Nested')` precedes `setTemplate('custom')`, navigation should go to `Movie/Nested/custom.ctp`
2. **Multiple setTemplatePath**: When multiple `setTemplatePath()` calls exist, only the closest preceding one should apply (allowing the path to be changed mid-method)

## Solution Implemented

### 1. Implemented `getTemplatePathPreceeding()` Method
Location: `TemplateGotoDeclarationHandler.kt:222-277`

The method:
1. **Finds the containing method** using `PsiTreeUtil.getParentOfType()` to limit search scope
2. **Collects all setTemplatePath calls** in that method using `PsiTreeUtil.findChildrenOfType()`
3. **Filters for valid calls** - checks that each is `$this->viewBuilder()->setTemplatePath()`
4. **Finds the closest preceding call** by comparing text offsets - selects the highest offset that's still less than the current `setTemplate` call's offset
5. **Extracts the string literal** from the parameter list of the found call

### 2. Updated handleViewBuilderCall() Method
Location: `TemplateGotoDeclarationHandler.kt:179-188`

Modified to:
1. Call `getTemplatePathPreceeding()` to get any preceding template path
2. Extract the `.contents` from the returned `StringLiteralExpression`
3. Prepend "/" to make the path absolute (so the controller path won't be prepended by the view resolution logic)
4. Combine with the template name: `"/${templatePath}/${viewName}"`

### 3. Key Implementation Detail: Absolute Paths
The critical insight was that CakePHP's view resolution logic (in `CakeView.kt`) handles paths differently based on whether `ActionName.isAbsolute` is true (path starts with "/"):
- **Non-absolute paths**: Prepends the controller's view path (e.g., "Movie/")
- **Absolute paths**: Uses the path as-is without prepending controller path

By prepending "/" to the combined path, we ensure that `setTemplatePath('Movie/Nested')` creates an absolute path `/Movie/Nested/custom` rather than letting the system prepend the controller path again.

## Testing
All 17 `TemplateGotoDeclarationTest` tests pass, including:
- CakePHP 3, 4, and 5 versions
- Single `setTemplatePath` followed by `setTemplate`
- Multiple `setTemplatePath` calls where only the closest preceding one applies
- Basic `setTemplate` calls without `setTemplatePath` (unchanged behavior)

## Files Modified
- `src/main/kotlin/com/daveme/chocolateCakePHP/view/TemplateGotoDeclarationHandler.kt`
  - Implemented `getTemplatePathPreceeding()` method
  - Updated `handleViewBuilderCall()` to use the new method
