package com.daveme.chocolateCakePHP.test.core

import com.daveme.chocolateCakePHP.ComposerInfo
import com.daveme.chocolateCakePHP.constraintMajorVersions
import com.daveme.chocolateCakePHP.hasUnversionedAlternative
import com.daveme.chocolateCakePHP.parseComposerJson
import junit.framework.TestCase

class ComposerJsonTest : TestCase() {

    fun `test cakephp detected in require`() {
        val info = parseComposerJson("""{"require": {"cakephp/cakephp": "^5.0"}}""", "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertNull(info.appDirectory)
    }

    fun `test cakephp only in require-dev is not detected`() {
        val info = parseComposerJson("""{"require-dev": {"cakephp/cakephp": "^5.0"}}""", "\\App")
        assertNull(info.cakePhpMajorVersions)
    }

    fun `test cakephp absent when require has other packages`() {
        val info = parseComposerJson("""{"require": {"laravel/framework": "^10"}}""", "\\App")
        assertNull(info.cakePhpMajorVersions)
    }

    fun `test app directory from psr-4 with trailing slash removed`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": "src/"}}}""", "\\App")
        assertEquals("src", info.appDirectory)
    }

    fun `test app directory from psr-4 without trailing slash`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": "application"}}}""", "\\App")
        assertEquals("application", info.appDirectory)
    }

    fun `test autoload-dev psr-4 is not used`() {
        val json = """{"autoload-dev": {"psr-4": {"App\\": "tests/"}}}"""
        assertNull(parseComposerJson(json, "\\App").appDirectory)
    }

    fun `test namespace without leading backslash matches`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": "src/"}}}""", "App")
        assertEquals("src", info.appDirectory)
    }

    fun `test namespace with trailing backslash matches`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": "src/"}}}""", "App\\")
        assertEquals("src", info.appDirectory)
    }

    fun `test namespace with leading and trailing backslash matches`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": "src/"}}}""", "\\App\\")
        assertEquals("src", info.appDirectory)
    }

    fun `test psr-4 key with leading backslash matches`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"\\App\\": "src/"}}}""", "\\App")
        assertEquals("src", info.appDirectory)
    }

    fun `test psr-4 key without trailing backslash matches`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App": "src/"}}}""", "\\App")
        assertEquals("src", info.appDirectory)
    }

    fun `test duplicate psr-4 key is last-wins`() {
        val json = """{"autoload": {"psr-4": {"App\\": "src/", "App\\": "app/"}}}"""
        assertEquals("app", parseComposerJson(json, "\\App").appDirectory)
    }

    fun `test duplicate autoload block is last-wins`() {
        val json = """
            {
                "autoload": {"psr-4": {"App\\": "src/"}},
                "autoload": {"psr-4": {"Other\\": "lib/"}}
            }
        """.trimIndent()
        assertNull(parseComposerJson(json, "\\App").appDirectory)
    }

    fun `test duplicate require block is last-wins`() {
        val json = """
            {
                "require": {"cakephp/cakephp": "^5.0"},
                "require": {"php": ">=8.1"}
            }
        """.trimIndent()
        assertNull(parseComposerJson(json, "\\App").cakePhpMajorVersions)
        val reversed = """
            {
                "require": {"php": ">=8.1"},
                "require": {"cakephp/cakephp": "^5.0"}
            }
        """.trimIndent()
        assertNotNull(parseComposerJson(reversed, "\\App").cakePhpMajorVersions)
    }

    fun `test require that is not an object is not detected`() {
        assertNull(parseComposerJson("""{"require": "cakephp/cakephp"}""", "\\App").cakePhpMajorVersions)
    }

    fun `test custom nested namespace and directory`() {
        val json = """{"autoload": {"psr-4": {"Acme\\Shop\\": "apps/shop/src/"}}}"""
        assertEquals("apps/shop/src", parseComposerJson(json, "\\Acme\\Shop").appDirectory)
    }

    fun `test app directory null when namespace not in psr-4`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"Other\\": "lib/"}}}""", "\\App")
        assertNull(info.appDirectory)
    }

    fun `test app directory null when psr-4 value is not a string`() {
        val info = parseComposerJson("""{"autoload": {"psr-4": {"App\\": ["src/", "lib/"]}}}""", "\\App")
        assertNull(info.appDirectory)
    }

    fun `test error after require keeps both facts`() {
        val json = """
            {
                "require": {"cakephp/cakephp": "^5.0"},
                "autoload": {"psr-4": {"App\\": "src/"}},
                "scripts": {"test": }
            }
        """.trimIndent()
        val info = parseComposerJson(json, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("src", info.appDirectory)
    }

    fun `test missing value directly above cakephp keeps both facts`() {
        val json = """
            {
                "require": {
                    "php":
                    "cakephp/cakephp": "^5.0"
                },
                "autoload": {"psr-4": {"App\\": "src/"}}
            }
        """.trimIndent()
        val info = parseComposerJson(json, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("src", info.appDirectory)
    }

    fun `test half-typed entry inside require keeps both facts`() {
        val json = """
            {
                "require": {
                    "php": ">=8.1",
                    "cakephp/cakephp": "^5.0",
                    "cakephp/migrations":
                },
                "autoload": {"psr-4": {"App\\": "src/"}}
            }
        """.trimIndent()
        val info = parseComposerJson(json, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("src", info.appDirectory)
    }

    fun `test unterminated string above autoload keeps both facts`() {
        val json = """
            {
                "require": {
                    "cakephp/cakephp": "^5.0,
                    "cakephp/migrations": "^4.0"
                },
                "autoload": {"psr-4": {"App\\": "app/src/"}}
            }
        """.trimIndent()
        val info = parseComposerJson(json, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("app/src", info.appDirectory)
    }

    fun `test extra closing brace above require keeps both facts`() {
        val json = """
            {
                "autoload": {"psr-4": {"App\\": "app/src/"}}},
                "require": {"cakephp/cakephp": "^5.0"}
            }
        """.trimIndent()
        val info = parseComposerJson(json, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("app/src", info.appDirectory)
    }

    fun `test empty and invalid input yields empty info`() {
        assertEquals(ComposerInfo(), parseComposerJson("", "\\App"))
        assertEquals(ComposerInfo(), parseComposerJson("   ", "\\App"))
        assertEquals(ComposerInfo(), parseComposerJson("{ this is not json", "\\App"))
        assertEquals(ComposerInfo(), parseComposerJson("[1, 2, 3]", "\\App"))
    }

    fun `test realistic composer json`() {
        val jsonString = """
            {
                "name": "cakephp/app",
                "description": "CakePHP skeleton app",
                "homepage": "https://cakephp.org",
                "type": "project",
                "license": "MIT",
                "require": {
                    "php": ">=8.1",
                    "cakephp/cakephp": "^5.0.1",
                    "cakephp/migrations": "^4.0.0",
                    "cakephp/plugin-installer": "^2.0",
                    "mobiledetect/mobiledetectlib": "^4.8.03"
                },
                "require-dev": {
                    "cakephp/bake": "^3.0.0",
                    "cakephp/cakephp-codesniffer": "^5.0",
                    "cakephp/debug_kit": "^5.0.0",
                    "josegonzalez/dotenv": "^4.0",
                    "phpunit/phpunit": "^10.1.0"
                },
                "suggest": {
                    "cakephp/repl": "Console tools for a REPL interface for CakePHP applications.",
                    "dereuromark/cakephp-ide-helper": "After baking your code, this keeps your annotations in sync with the code.",
                    "markstory/asset_compress": "An asset compression plugin which provides file concatenation and a flexible filter system for preprocessing and minification.",
                    "phpstan/phpstan": "PHPStan focuses on finding errors in your code without actually running it."
                },
                "autoload": {
                    "psr-4": {
                        "App\\": "src/"
                    }
                },
                "autoload-dev": {
                    "psr-4": {
                        "App\\Test\\": "tests/",
                        "Cake\\Test\\": "vendor/cakephp/cakephp/tests/"
                    }
                },
                "scripts": {
                    "post-install-cmd": "App\\Console\\Installer::postInstall",
                    "post-create-project-cmd": "App\\Console\\Installer::postInstall",
                    "check": [
                        "@test",
                        "@cs-check"
                    ],
                    "cs-check": "phpcs --colors -p",
                    "cs-fix": "phpcbf --colors -p",
                    "stan": "phpstan analyse",
                    "test": "phpunit --colors=always"
                },
                "prefer-stable": true,
                "config": {
                    "sort-packages": true,
                    "allow-plugins": {
                        "cakephp/plugin-installer": true,
                        "dealerdirect/phpcodesniffer-composer-installer": true
                    }
                }
            }
        """.trimIndent()
        val info = parseComposerJson(jsonString, "\\App")
        assertNotNull(info.cakePhpMajorVersions)
        assertEquals("src", info.appDirectory)
    }

    fun `test major versions of cakephp constraint in require`() {
        val info = parseComposerJson("""{"require": {"cakephp/cakephp": "2.10.*"}}""", "\\App")
        assertEquals(setOf(2), info.cakePhpMajorVersions)
    }

    fun `test branch constraint gives no major versions`() {
        val info = parseComposerJson("""{"require": {"cakephp/cakephp": "dev-master"}}""", "\\App")
        assertEquals(emptySet<Int>(), info.cakePhpMajorVersions)
    }

    fun `test branch alternative alongside a version is reported`() {
        val info = parseComposerJson("""{"require": {"cakephp/cakephp": "dev-master || ^2.10"}}""", "\\App")
        assertEquals(setOf(2), info.cakePhpMajorVersions)
        assertTrue(info.cakePhpUnversionedAlternative)
    }

    fun `test unversioned alternatives`() {
        assertTrue(hasUnversionedAlternative("dev-master"))
        assertTrue(hasUnversionedAlternative("dev-master || ^2.10"))
        assertTrue(hasUnversionedAlternative("^5.0 || *"))
        assertTrue(hasUnversionedAlternative(""))
        assertFalse(hasUnversionedAlternative("^4.4 || ^5.0"))
        assertFalse(hasUnversionedAlternative("2.10.*"))
    }

    fun `test constraint major versions for single alternatives`() {
        assertEquals(setOf(2), constraintMajorVersions("2.10.*"))
        assertEquals(setOf(2), constraintMajorVersions("~2.9"))
        assertEquals(setOf(2), constraintMajorVersions("^2.0"))
        assertEquals(setOf(2), constraintMajorVersions(">=2.5,<3.0"))
        assertEquals(setOf(2), constraintMajorVersions(">=2.5 <3.0"))
        assertEquals(setOf(2), constraintMajorVersions("v2.10.0"))
        assertEquals(setOf(4), constraintMajorVersions("4.4.*"))
        assertEquals(setOf(5), constraintMajorVersions("  >= 5.0  "))
    }

    fun `test constraint major versions for multiple alternatives`() {
        assertEquals(setOf(4, 5), constraintMajorVersions("^4.4 || ^5.0"))
        assertEquals(setOf(2, 3), constraintMajorVersions("^2.10||^3.0"))
        assertEquals(setOf(3, 4), constraintMajorVersions("^3.0 | ^4.0"))
    }

    fun `test constraint without leading version gives no major versions`() {
        assertEquals(emptySet<Int>(), constraintMajorVersions("dev-master"))
        assertEquals(emptySet<Int>(), constraintMajorVersions("*"))
        assertEquals(emptySet<Int>(), constraintMajorVersions("@dev"))
        assertEquals(emptySet<Int>(), constraintMajorVersions(""))
        assertEquals(setOf(5), constraintMajorVersions("dev-master || ^5.0"))
    }
}
