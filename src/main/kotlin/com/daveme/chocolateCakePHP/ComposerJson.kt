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
 * rather than failing.
 */
fun parseComposerJson(contents: String, namespace: String): ComposerInfo {
    val entries = scanJsonEntries(contents)
    val targetNamespace = canonicalNamespace(namespace)
    return ComposerInfo(
        cakePhpRequired = entries.any { it.path == REQUIRE_PATH && it.key == "cakephp/cakephp" },
        appDirectory = entries
            .firstOrNull { it.path == PSR4_PATH && canonicalNamespace(it.key) == targetNamespace }
            ?.value
            ?.removeFromEnd("/"),
    )
}

/**
 * Normalise a namespace for comparison: no leading backslash, exactly one
 * trailing backslash. Applied to both the namespace from config/app.php and
 * the psr-4 key from composer.json, so `\App`, `App`, `App\` and `\App\`
 * all compare equal.
 */
private fun canonicalNamespace(namespace: String): String =
    namespace.trim('\\') + "\\"

