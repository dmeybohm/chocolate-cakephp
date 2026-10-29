package com.daveme.chocolateCakePHP

/**
 * The facts the plugin needs from a project's composer.json.
 */
data class ComposerInfo(
    val cakePhpMajorVersions: Set<Int>? = null,
    val appDirectory: String? = null,
)

private val REQUIRE_PATH = listOf("require")
private val PSR4_PATH = listOf("autoload", "psr-4")

/**
 * Extract CakePHP-related facts from the contents of a composer.json file.
 *
 * - [ComposerInfo.cakePhpMajorVersions] is the set of major versions the
 *   `cakephp/cakephp` constraint in `require` allows, or null if absent.
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
        cakePhpMajorVersions = membersOfLastObject(entries, REQUIRE_PATH)
            .lastOrNull { it.key == "cakephp/cakephp" }
            ?.let { constraintMajorVersions(it.value ?: "") },
        appDirectory = membersOfLastObject(entries, PSR4_PATH)
            .lastOrNull { canonicalNamespace(it.key) == targetNamespace }
            ?.value
            ?.removeFromEnd("/"),
    )
}

private val ALTERNATIVE_SEPARATOR = Regex("""\|\|?""")
private val LEADING_MAJOR_VERSION = Regex("""^[<>=!^~\s]*v?(\d+)""")

/**
 * The major versions a Composer version constraint allows, read from the
 * lower bound of each `||` alternative. Alternatives that do not start with
 * a version number, such as `dev-master` or `*`, contribute nothing.
 */
fun constraintMajorVersions(constraint: String): Set<Int> =
    constraint.split(ALTERNATIVE_SEPARATOR)
        .mapNotNull { LEADING_MAJOR_VERSION.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
        .toSet()

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

