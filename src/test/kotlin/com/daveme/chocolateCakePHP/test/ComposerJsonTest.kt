package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.ComposerInfo
import com.daveme.chocolateCakePHP.parseComposerJson
import junit.framework.TestCase

class ComposerJsonTest : TestCase() {

    fun `test cakephp detected in require`() {
        val info = parseComposerJson("""{"require": {"cakephp/cakephp": "^5.0"}}""", "\\App")
        assertTrue(info.cakePhpRequired)
        assertNull(info.appDirectory)
    }

    fun `test cakephp only in require-dev is not detected`() {
        val info = parseComposerJson("""{"require-dev": {"cakephp/cakephp": "^5.0"}}""", "\\App")
        assertFalse(info.cakePhpRequired)
    }

    fun `test cakephp absent when require has other packages`() {
        val info = parseComposerJson("""{"require": {"laravel/framework": "^10"}}""", "\\App")
        assertFalse(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
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
        assertTrue(info.cakePhpRequired)
        assertEquals("src", info.appDirectory)
    }
}
