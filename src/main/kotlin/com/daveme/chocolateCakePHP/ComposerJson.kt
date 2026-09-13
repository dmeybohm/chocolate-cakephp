package com.daveme.chocolateCakePHP

/**
 * The facts the plugin needs from a project's composer.json.
 */
data class ComposerInfo(
    val cakePhpRequired: Boolean = false,
    val appDirectory: String? = null,
)

private val REQUIRE_PATH = listOf("require")
private val PSR4_PATH = listOf("autoload", "psr-4")

/**
 * Extract CakePHP-related facts from the contents of a composer.json file.
 *
 * - [ComposerInfo.cakePhpRequired] is true when `require` lists `cakephp/cakephp`.
 * - [ComposerInfo.appDirectory] is the directory mapped to [namespace] under
 *   `autoload.psr-4`, with any trailing slash removed, or null if absent.
 *
 * Uses [scanJsonEntries], so malformed input yields whatever facts survive
 * rather than failing. Duplicate keys are last-wins at every level, as in
 * Composer's own `json_decode`: a repeated `require` or `autoload` block
 * replaces the earlier one, and a repeated key inside a block does too.
 */
fun parseComposerJson(contents: String, namespace: String): ComposerInfo {
    val entries = scanJsonEntries(contents)
    val targetNamespace = canonicalNamespace(namespace)
    return ComposerInfo(
        cakePhpRequired = membersOfLastObject(entries, REQUIRE_PATH)
            .any { it.key == "cakephp/cakephp" },
        appDirectory = membersOfLastObject(entries, PSR4_PATH)
            .lastOrNull { canonicalNamespace(it.key) == targetNamespace }
            ?.value
            ?.removeFromEnd("/"),
    )
}

/**
 * The direct members of the last object found at [path], in document order.
 *
 * The scanner emits an object's key (with a null value) before the entries
 * inside it, so the last object at [path] is the run of entries with that
 * exact path that follows the last opener of [path] inside the last object
 * at its parent path. Members of earlier duplicate objects are excluded.
 */
private fun membersOfLastObject(entries: List<JsonEntry>, path: List<String>): List<JsonEntry> {
    var range = entries.indices
    for (depth in 1..path.size) {
        val parentPath = path.subList(0, depth - 1)
        val key = path[depth - 1]
        val opener = (range.last downTo range.first).firstOrNull {
            entries[it].path == parentPath && entries[it].key == key
        } ?: return emptyList()
        // The object ends at the next member of its parent, or at the end of the parent.
        val end = (opener + 1..range.last).firstOrNull { entries[it].path == parentPath }
            ?: range.last + 1
        range = opener + 1 until end
    }
    return range.map { entries[it] }.filter { it.path == path }
}

/**
 * Normalise a namespace for comparison: no leading backslash, exactly one
 * trailing backslash. Applied to both the namespace from config/app.php and
 * the psr-4 key from composer.json, so `\App`, `App`, `App\` and `\App\`
 * all compare equal.
 */
private fun canonicalNamespace(namespace: String): String =
    namespace.trim('\\') + "\\"

