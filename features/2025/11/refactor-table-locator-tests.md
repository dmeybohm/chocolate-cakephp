# Reorganize TableLocator Tests

**Issue:** [#159](https://github.com/dmeybohm/chocolate-cakephp/issues/159)
**Branch:** `refactor-table-locator-tests`

## Overview

Reorganize the TableLocator tests into 5 distinct test classes across CakePHP versions, making it clearer where new tests should be added and improving coverage tracking.

## Target Test Structure

| Test Class | cake3 | cake4 | cake5 | API Pattern |
|------------|-------|-------|-------|-------------|
| FetchTableTest | - | Yes | Yes | `$this->fetchTable("Table")` |
| GetTableLocatorTest | Yes | Yes | Yes | `$this->getTableLocator()->get("Table")` |
| StaticTableRegistryGetTableLocatorTest | Yes | Yes | Yes | `TableRegistry::getTableLocator()->get("Table")` |
| StaticTableRegistryGetTest | Yes | - | - | `TableRegistry::get("Table")` |
| LocatorAwareTraitTest | Yes | Yes | Yes | `$this->Table` |

## File Operations

**Rename:**
- `cake5/TableLocatorTest.kt` → `cake5/FetchTableTest.kt`
- `cake5/TableLocatorGotoDeclarationTest.kt` → `cake5/FetchTableGotoDeclarationTest.kt`
- `cake4/TableLocatorTest.kt` → `cake4/FetchTableTest.kt`
- `cake4/TableLocatorGotoDeclarationTest.kt` → `cake4/FetchTableGotoDeclarationTest.kt`
- `cake3/TableRegistryTest.kt` → `cake3/StaticTableRegistryGetTest.kt`

**Create:**
- GetTableLocatorTest.kt (cake3, cake4, cake5)
- StaticTableRegistryGetTableLocatorTest.kt (cake3, cake4, cake5)
- LocatorAwareTraitTest.kt (cake3, cake4, cake5)

**Delete:**
- `cake3/TableLocatorTest.kt`

**Keep unchanged:**
- `cake3/TableLocatorGotoDeclarationTest.kt`

## Implementation Progress

### Session #1

Completed full reorganization:

1. **cake5** - Reorganized into FetchTableTest, GetTableLocatorTest, StaticTableRegistryGetTableLocatorTest, LocatorAwareTraitTest. Renamed GotoDeclaration test to FetchTableGotoDeclarationTest.

2. **cake4** - Same structure as cake5.

3. **cake3** - Reorganized into GetTableLocatorTest, StaticTableRegistryGetTableLocatorTest, LocatorAwareTraitTest, StaticTableRegistryGetTest (for deprecated TableRegistry::get() API). TableLocatorGotoDeclarationTest kept as-is.

All tests pass across all versions.
