# Fix Composer.json Creation Detection

## Problem

Currently, when a blank project is created without `composer.json` or `config/app.php`, the cached autodetected values use `ModificationTracker.NEVER_CHANGED`. This means if the user later adds `composer.json` to the project, the cache will never be invalidated and the CakePHP autodetection won't run.

## Proposed Solutions

### Solution 1: Custom ModificationTracker (Recommended)

Create a custom `ModificationTracker` that increments its modification count when `composer.json` or `config/app.php` are created in the project root.

**Implementation:**

1. Create a new class `CakePhpFilesModificationTracker` that implements `ModificationTracker`:
   - Maintain a `modificationCount` that starts at 0
   - Subscribe to `VirtualFileManager.VFS_CHANGES` via `BulkFileListener` in the constructor
   - In the `after()` method of the listener, check if any `VFileCreateEvent` is for `composer.json` or `config/app.php` in the project root
   - If so, increment `modificationCount`
   - Implement `getModificationCount()` to return the current count

2. Register the tracker as a project-level service

3. Update `CakePhpAutoDetector.autoDetectedValues` to use this tracker when dependencies are empty:
   ```kotlin
   if (dependencies.isEmpty()) {
       val tracker = project.getService(CakePhpFilesModificationTracker::class.java)
       CachedValueProvider.Result.create(result, tracker)
   } else {
       CachedValueProvider.Result.create(result, dependencies)
   }
   ```

**Pros:**
- Properly invalidates cache when files are created
- Clean separation of concerns
- Works for both new projects and existing projects
- Handles file deletion/recreation scenarios

**Cons:**
- Requires a new class
- Slightly more complex implementation

### Solution 2: Use Project Root Directory as Dependency

Instead of `ModificationTracker.NEVER_CHANGED`, add the project root directory itself as a dependency when the files don't exist.

**Implementation:**

Update the code at Settings.kt:117-119:
```kotlin
if (dependencies.isEmpty()) {
    val projectDir = project.guessProjectDir()
    if (projectDir != null) {
        dependencies.add(projectDir)
    }
}
// Always create with dependencies array (even if empty now means project dir)
CachedValueProvider.Result.create(result, dependencies)
```

**Pros:**
- Simple one-line change
- No new classes needed

**Cons:**
- Cache will be invalidated on ANY change to project root (too aggressive)
- May cause performance issues with frequent cache recalculation
- Not semantically correct (we only care about specific files)

### Solution 3: VirtualFilePointer for Non-Existent Files

Use `VirtualFilePointerManager` to create pointers to the expected file paths, even if they don't exist yet. These pointers can be added as dependencies.

**Implementation:**

```kotlin
val dependencies = mutableListOf<Any>()
val projectDir = project.guessProjectDir()

if (projectDir != null) {
    val vpManager = VirtualFilePointerManager.getInstance()

    // Create pointer for composer.json
    val composerPath = projectDir.path + "/composer.json"
    val composerPointer = vpManager.create(composerPath, project, null)
    dependencies.add(composerPointer)

    // Create pointer for config/app.php
    val appConfigPath = projectDir.path + "/config/app.php"
    val appConfigPointer = vpManager.create(appConfigPath, project, null)
    dependencies.add(appConfigPointer)
}

CachedValueProvider.Result.create(result, dependencies)
```

**Pros:**
- Designed for this exact use case (tracking files that may not exist)
- Built-in IntelliJ platform support
- No custom classes needed

**Cons:**
- Need to verify if VirtualFilePointers work as CachedValue dependencies
- May require additional testing

## Recommendation

**Solution 1 (Custom ModificationTracker)** is the most robust and semantically correct solution. It gives precise control over when the cache should be invalidated and only triggers on the specific events we care about (creation of `composer.json` or `config/app.php`).

However, **Solution 3 (VirtualFilePointer)** might be simpler if it works with CachedValueProvider - this should be tested first.

## Implementation Steps for Solution 1

1. Create `src/main/kotlin/com/daveme/chocolateCakePHP/CakePhpFilesModificationTracker.kt`:
   - Implement `ModificationTracker`
   - Subscribe to VFS changes in constructor
   - Filter events for project root `composer.json` and `config/app.php`
   - Track modification count

2. Register the service in `plugin.xml`:
   ```xml
   <extensions defaultExtensionNs="com.intellij">
     <!-- existing extensions -->
     <projectService serviceImplementation="com.daveme.chocolateCakePHP.CakePhpFilesModificationTracker"/>
   </extensions>
   ```

3. Update `Settings.kt` in `CakePhpAutoDetector.autoDetectedValues` to use the tracker when no files exist

4. Add tests to verify:
   - Cache is not recalculated in projects without CakePHP files
   - Cache IS recalculated when composer.json is created
   - Cache IS recalculated when config/app.php is created

## Alternative: Simplest Fix (Current State)

Keep the current implementation with `ModificationTracker.NEVER_CHANGED` and document that users should restart IntelliJ or invalidate caches after adding CakePHP to a project. This is the simplest but provides poor UX.
