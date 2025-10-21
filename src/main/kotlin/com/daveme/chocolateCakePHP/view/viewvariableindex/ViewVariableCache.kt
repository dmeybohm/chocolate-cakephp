package com.daveme.chocolateCakePHP.view.viewvariableindex

import com.daveme.chocolateCakePHP.PhpFilesModificationTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Cache for view variable existence checks.
 *
 * This cache stores boolean results indicating whether a variable is defined
 * for a given view file. The cache is automatically invalidated when:
 * - The view file itself changes
 * - Any PHP file in the project changes (via PhpFilesModificationTracker)
 *
 * If the PhpFilesModificationTracker is not armed (project is not a CakePHP project),
 * the cache bypasses itself and performs direct lookups to avoid stale data.
 */
@Service(Service.Level.PROJECT)
class ViewVariableCache(private val project: Project) {

    companion object {
        private val VARIABLE_CACHE_KEY = Key.create<CachedValue<MutableMap<String, Boolean>>>("CHOCOLATE_CAKEPHP_VIEW_VARIABLE_CACHE")
    }

    /**
     * Checks if a variable is defined for the given view file.
     *
     * If the phpTracker is not armed (not a CakePHP project), bypasses caching
     * and performs a direct lookup to avoid returning stale data.
     *
     * @param psiFile The view file to check
     * @param filenameKey The canonical filename key for the view
     * @param variableName The name of the variable to check
     * @param phpTracker Modification tracker for PHP files
     * @param lookupFn Function to perform the actual lookup if not cached
     * @return true if the variable is defined, false otherwise
     */
    fun isVariableDefined(
        psiFile: PsiFile,
        filenameKey: String,
        variableName: String,
        phpTracker: ModificationTracker,
        lookupFn: (String, String) -> Boolean
    ): Boolean {
        // Check if the tracker is actually armed
        // If tracker is null or not armed, bypass caching and do direct lookup
        val tracker = phpTracker as? PhpFilesModificationTracker
        if (tracker == null || !tracker.isArmed) {
            // Not a CakePHP project or invalid tracker - don't use cache, just do direct lookup
            return lookupFn(filenameKey, variableName)
        }

        val cachedValuesManager = CachedValuesManager.getManager(project)

        // Get or create the cache for this file
        val variableCache = cachedValuesManager.getCachedValue(
            psiFile,
            VARIABLE_CACHE_KEY,
            {
                CachedValueProvider.Result.create(
                    mutableMapOf<String, Boolean>(),
                    psiFile,  // Invalidate when the view file changes
                    phpTracker  // Invalidate when any PHP file changes
                )
            },
            false
        )

        // Create a cache key for this specific variable lookup
        val cacheKey = "$filenameKey::$variableName"

        // Return cached value if present, otherwise compute and cache
        if (variableCache.containsKey(cacheKey)) {
            return variableCache[cacheKey]!!
        }

        val result = lookupFn(filenameKey, variableName)
        variableCache[cacheKey] = result
        return result
    }
}
