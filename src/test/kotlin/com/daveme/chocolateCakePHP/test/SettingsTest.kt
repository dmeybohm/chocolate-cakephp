package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.CakeAutoDetectedValues
import com.daveme.chocolateCakePHP.CakePhpAutoDetector
import com.daveme.chocolateCakePHP.CakeThreePluginConfig
import com.daveme.chocolateCakePHP.CakeTwoPluginConfig
import com.daveme.chocolateCakePHP.LegacyPluginConfig
import com.daveme.chocolateCakePHP.PluginConfig
import com.daveme.chocolateCakePHP.SettingsState
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.effectivePluginName
import com.daveme.chocolateCakePHP.findPluginConfigByName
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase

private const val CAKE_REQUIRE = "\"cakephp/cakephp\": \"^5.0\""
private const val APP_PSR4 = "\"App\\\\\": \"src/\""

// Extend from HeavyPlatformTestCase for the per-project settings autodetection state:
class SettingsTest() : HeavyPlatformTestCase() {

    fun `test default constructed settings equals defaults property`() {
        val settings = Settings(project)
        val defaults = Settings.getDefaults(project)
        assertEquals(settings, defaults)
        assertEquals(settings.state, defaults.state)
    }

    fun `test getting settings service`() {
        assertNotNull(Settings.getInstance(this.project))
    }

    // ========== Auto-detection helpers ==========

    private fun composerJson(require: String = CAKE_REQUIRE, psr4: String = APP_PSR4) = """
        {
            "require": {
                $require
            },
            "autoload": {
                "psr-4": {
                    $psr4
                }
            }
        }
    """.trimIndent()

    /**
     * Write a file under the project base directory, creating parent
     * directories as needed. VFS events fire synchronously inside the write
     * action, so the detection cache is already invalidated when this returns.
     */
    private fun writeProjectFile(relativePath: String, content: String): VirtualFile =
        WriteAction.computeAndWait<VirtualFile, RuntimeException> {
            val baseDir = orCreateProjectBaseDir
            val parentPath = relativePath.substringBeforeLast('/', "")
            val parent = if (parentPath.isEmpty()) baseDir
                         else VfsUtil.createDirectoryIfMissing(baseDir, parentPath)!!
            val file = parent.findOrCreateChildData(this, relativePath.substringAfterLast('/'))
            file.setBinaryContent(content.toByteArray())
            file
        }

    private fun deleteProjectFile(file: VirtualFile) =
        WriteAction.runAndWait<RuntimeException> { file.delete(this) }

    private fun detected(): CakeAutoDetectedValues =
        project.getService(CakePhpAutoDetector::class.java).autoDetectedValues

    /**
     * The modification tracker that invalidates the detection cache subscribes
     * to VFS events when it is first created, and it is created lazily by the
     * first detection. Run one detection before writing any files so the
     * tracker exists to see the writes.
     */
    private fun primeDetector() {
        assertFalse(detected().cake3OrLaterPresent)
    }

    // ========== Auto-detection tests ==========

    fun `test autodetect works when composer json is not present`() {
        assertFalse(Settings.getInstance(project).cake3Enabled)
    }

    fun `test autodetect cache invalidates when composer json is created`() {
        val settings = Settings.getInstance(project)
        assertFalse(settings.cake3Enabled)
        primeDetector()

        writeProjectFile("composer.json", composerJson())

        val values = detected()
        assertTrue("CakePHP should be detected after composer.json creation", values.cake3OrLaterPresent)
        assertEquals("src", values.appDirectory)
        assertTrue(settings.cake3Enabled)
    }

    fun `test half-typed composer json keeps detection`() {
        primeDetector()
        writeProjectFile("composer.json", composerJson())
        assertTrue(detected().cake3OrLaterPresent)

        // A new package entry saved before its version is typed.
        writeProjectFile("composer.json", composerJson(require = """
            "php": ">=8.1",
            $CAKE_REQUIRE,
            "cakephp/migrations":
        """.trimIndent()))
        val halfTyped = detected()
        assertTrue("Detection must survive a half-typed composer.json", halfTyped.cake3OrLaterPresent)
        assertEquals("src", halfTyped.appDirectory)

        writeProjectFile("composer.json", composerJson())
        assertTrue(detected().cake3OrLaterPresent)
    }

    fun `test removing cakephp from composer json disables detection`() {
        primeDetector()
        writeProjectFile("composer.json", composerJson())
        assertTrue(detected().cake3OrLaterPresent)

        writeProjectFile("composer.json", composerJson(require = "\"laravel/framework\": \"^10\""))
        assertFalse(detected().cake3OrLaterPresent)
    }

    fun `test app directory follows psr-4 change`() {
        primeDetector()
        writeProjectFile("composer.json", composerJson())
        assertEquals("src", detected().appDirectory)

        writeProjectFile("composer.json", composerJson(psr4 = "\"App\\\\\": \"application/\""))
        assertEquals("application", detected().appDirectory)
    }

    fun `test deleting composer json disables detection`() {
        primeDetector()
        val file = writeProjectFile("composer.json", composerJson())
        assertTrue(detected().cake3OrLaterPresent)

        deleteProjectFile(file)
        val values = detected()
        assertFalse(values.cake3OrLaterPresent)
        assertEquals("src", values.appDirectory)
    }

    fun `test namespace is read from config app php`() {
        primeDetector()
        writeProjectFile("composer.json", composerJson(psr4 = "\"Acme\\\\Shop\\\\\": \"apps/shop/\""))
        writeProjectFile("config/app.php", """
            <?php
            return [
                'debug' => true,
                'namespace' => 'Acme\\Shop',
            ];
        """.trimIndent())

        val values = detected()
        assertEquals("Acme\\Shop", values.namespace)
        assertEquals("apps/shop", values.appDirectory)
        assertEquals("\\Acme\\Shop", Settings.getInstance(project).appNamespace)

        // A namespace change is picked up, and the psr-4 lookup follows it.
        writeProjectFile("config/app.php", """
            <?php
            return [
                'namespace' => 'Other',
            ];
        """.trimIndent())
        val changed = detected()
        assertEquals("Other", changed.namespace)
        assertEquals("src", changed.appDirectory)
    }

    fun `test settings getters reflect detection unless forced`() {
        val settings = Settings.getInstance(project)
        primeDetector()
        writeProjectFile("composer.json", composerJson(psr4 = "\"App\\\\\": \"application/\""))

        assertTrue(settings.cake3Enabled)
        assertEquals("application", settings.appDirectory)
        assertEquals("\\App", settings.appNamespace)

        val forced = Settings.getDefaults(project).state.copy()
        forced.cake3ForceEnabled = true
        forced.appDirectory = "forced"
        settings.loadState(forced)
        assertTrue(settings.cake3Enabled)
        assertEquals("forced", settings.appDirectory)

        settings.loadState(Settings.getDefaults(project).state.copy())
    }

    fun `test renaming a file to composer json triggers detection`() {
        primeDetector()
        val file = writeProjectFile("composer.json.txt", composerJson())
        assertFalse(detected().cake3OrLaterPresent)

        WriteAction.runAndWait<RuntimeException> { file.rename(this, "composer.json") }
        assertTrue(detected().cake3OrLaterPresent)
    }

    // ========== Version-aware detection tests ==========

    private fun detectedFor(constraint: String): CakeAutoDetectedValues {
        primeDetector()
        writeProjectFile("composer.json", composerJson(require = "\"cakephp/cakephp\": \"$constraint\""))
        return detected()
    }

    fun `test cakephp 2 constraint detects only cakephp 2`() {
        val values = detectedFor("2.10.*")
        assertTrue(values.cake2Present)
        assertFalse(values.cake3OrLaterPresent)
    }

    fun `test cakephp 4 constraint detects only cakephp 3 or later`() {
        val values = detectedFor("^4.4")
        assertFalse(values.cake2Present)
        assertTrue(values.cake3OrLaterPresent)
    }

    fun `test constraint allowing cakephp 2 and 3 detects both`() {
        val values = detectedFor("^2.10 || ^3.0")
        assertTrue(values.cake2Present)
        assertTrue(values.cake3OrLaterPresent)
    }

    fun `test branch constraint detects only cakephp 3 or later`() {
        val values = detectedFor("dev-master")
        assertFalse(values.cake2Present)
        assertTrue(values.cake3OrLaterPresent)
    }

    fun `test branch alternative alongside cakephp 2 detects both`() {
        val values = detectedFor("dev-master || ^2.10")
        assertTrue(values.cake2Present)
        assertTrue(values.cake3OrLaterPresent)
    }

    fun `test cakephp 2 enabled follows detection unless auto-detect is off`() {
        val settings = Settings.getInstance(project)
        assertFalse(settings.cake2Enabled)
        detectedFor("2.10.*")
        assertTrue(settings.cake2Enabled)
        assertFalse(settings.cake3Enabled)

        val noAutoDetect = Settings.getDefaults(project).state.copy()
        noAutoDetect.cake2AutoDetect = false
        settings.loadState(noAutoDetect)
        assertFalse(settings.cake2Enabled)

        settings.loadState(Settings.getDefaults(project).state.copy())
    }

    fun `test cakephp 2 force enable works without composer json`() {
        val settings = Settings.getInstance(project)
        val forced = Settings.getDefaults(project).state.copy()
        forced.cake2Enabled = true
        forced.cake2AutoDetect = false
        settings.loadState(forced)
        assertTrue(settings.cake2Enabled)
        assertTrue(settings.cake2ForceEnabled)

        settings.loadState(Settings.getDefaults(project).state.copy())
    }

    fun `test cakephp 3 auto-detect off does not hide cakephp 2 detection`() {
        val settings = Settings.getInstance(project)
        val noCake3 = Settings.getDefaults(project).state.copy()
        noCake3.cake3Enabled = false
        settings.loadState(noCake3)
        detectedFor("^2.0")
        assertTrue(settings.cake2Enabled)
        assertFalse(settings.cake3Enabled)

        settings.loadState(Settings.getDefaults(project).state.copy())
    }

    // ========== effectivePluginName() Tests ==========

    fun `test effectivePluginName returns pluginName when set`() {
        val config = CakeThreePluginConfig(pluginName = "MyPlugin", namespace = "\\Vendor\\SomethingElse")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName derives from namespace when pluginName is empty`() {
        val config = CakeThreePluginConfig(namespace = "\\Vendor\\MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles namespace without backslash prefix`() {
        val config = CakeThreePluginConfig(namespace = "Vendor\\MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles simple namespace`() {
        val config = CakeThreePluginConfig(namespace = "MyPlugin")
        assertEquals("MyPlugin", config.effectivePluginName())
    }

    fun `test effectivePluginName handles empty namespace`() {
        val config = CakeThreePluginConfig(namespace = "")
        assertEquals("", config.effectivePluginName())
    }

    // ========== Legacy plugin entry classification ==========

    private fun loadedWith(configure: (SettingsState) -> Unit): Settings {
        val settings = Settings.getInstance(project)
        val state = Settings.getDefaults(project).state.copy()
        configure(state)
        settings.loadState(state)
        return settings
    }

    fun `test legacy entry with a namespace is cakephp 3 whatever its path`() {
        val settings = loadedWith {
            it.cake2Enabled = true
            it.pluginConfigs = listOf(LegacyPluginConfig(namespace = "\\Foo", pluginPath = "app/Plugin/Foo"))
        }
        assertEquals(
            listOf(CakeThreePluginConfig(namespace = "\\Foo", pluginPath = "app/Plugin/Foo")),
            settings.pluginConfigs,
        )
    }

    fun `test legacy entry under the cakephp 2 app directory is cakephp 2 named from its path`() {
        val settings = loadedWith {
            it.pluginConfigs = listOf(LegacyPluginConfig(namespace = "\\", pluginPath = "app/Plugin/DebugKit/"))
        }
        val expected = CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit/")
        assertEquals(listOf(expected), settings.pluginConfigs)
        assertEquals(expected, settings.findPluginConfigByName("DebugKit"))
    }

    fun `test legacy entry follows a custom cakephp 2 app directory`() {
        val settings = loadedWith {
            it.cake2AppDirectory = "legacy"
            it.pluginConfigs = listOf(
                LegacyPluginConfig(pluginName = "Blog", pluginPath = "legacy/Plugin/Blog"),
                LegacyPluginConfig(pluginName = "Other", pluginPath = "app/Plugin/Other"),
            )
        }
        assertEquals(
            listOf(
                CakeTwoPluginConfig(pluginName = "Blog", pluginPath = "legacy/Plugin/Blog"),
                CakeThreePluginConfig(pluginName = "Other", pluginPath = "app/Plugin/Other"),
            ),
            settings.pluginConfigs,
        )
    }

    fun `test legacy entry without a namespace is cakephp 2 when only cakephp 2 is enabled`() {
        val settings = loadedWith {
            it.cake2Enabled = true
            it.pluginConfigs = listOf(LegacyPluginConfig(pluginPath = "plugins/Foo"))
        }
        assertEquals(listOf(CakeTwoPluginConfig(pluginName = "Foo", pluginPath = "plugins/Foo")), settings.pluginConfigs)
    }

    fun `test legacy entry without a namespace is cakephp 3 otherwise`() {
        val settings = loadedWith {
            it.cake2Enabled = true
            it.cake3ForceEnabled = true
            it.pluginConfigs = listOf(LegacyPluginConfig(pluginPath = "plugins/Foo"))
        }
        assertEquals(listOf(CakeThreePluginConfig(pluginPath = "plugins/Foo")), settings.pluginConfigs)
    }

    fun `test cakephp 2 legacy entry with no name and no path is dropped`() {
        val settings = loadedWith {
            it.cake2Enabled = true
            it.pluginConfigs = listOf(LegacyPluginConfig())
        }
        assertEquals(listOf<PluginConfig>(), settings.pluginConfigs)
    }

    fun `test cakephp 2 configs survive a merge with deprecated namespaces`() {
        val settings = loadedWith {
            it.pluginNamespaces = listOf("\\Blog", "\\Shop")
            it.pluginConfigs = listOf(
                CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit"),
                CakeTwoPluginConfig(pluginName = "Search", pluginPath = "app/Plugin/Search"),
                CakeThreePluginConfig(namespace = "\\Blog", pluginPath = "plugins/Blog"),
            )
        }
        assertEquals(
            listOf(
                CakeTwoPluginConfig(pluginName = "DebugKit", pluginPath = "app/Plugin/DebugKit"),
                CakeTwoPluginConfig(pluginName = "Search", pluginPath = "app/Plugin/Search"),
                CakeThreePluginConfig(namespace = "\\Blog", pluginPath = "plugins/Blog"),
                CakeThreePluginConfig(namespace = "\\Shop", pluginPath = ""),
            ),
            settings.pluginConfigs,
        )
    }

    // ========== findPluginConfigByName() Tests ==========

    fun `test findPluginConfigByName finds by pluginName`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            CakeThreePluginConfig(pluginName = "Blog", namespace = "\\Vendor\\BlogPlugin", pluginPath = "plugins/Blog")
        )

        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("Blog", result!!.pluginName)
        assertEquals("plugins/Blog", result.pluginPath)
    }

    fun `test findPluginConfigByName backwards compat derives from namespace`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            CakeThreePluginConfig(namespace = "\\Vendor\\MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        // Should find by deriving plugin name from namespace
        val result = settings.findPluginConfigByName("MyPlugin")
        assertNotNull(result)
        assertEquals("\\Vendor\\MyPlugin", (result as CakeThreePluginConfig).namespace)
    }

    fun `test findPluginConfigByName backwards compat with simple namespace`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            CakeThreePluginConfig(namespace = "Blog", pluginPath = "plugins/Blog")
        )

        val result = settings.findPluginConfigByName("Blog")
        assertNotNull(result)
        assertEquals("Blog", (result as CakeThreePluginConfig).namespace)
        assertEquals("plugins/Blog", result.pluginPath)
    }

    fun `test findPluginConfigByName returns null when no match`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            CakeThreePluginConfig(pluginName = "MyPlugin", pluginPath = "plugins/MyPlugin")
        )

        val result = settings.findPluginConfigByName("NonExistent")
        assertNull(result)
    }

    fun `test findPluginConfigByName pluginName takes precedence over namespace derivation`() {
        val settings = Settings.getInstance(project)
        settings.state.pluginConfigs = listOf(
            CakeThreePluginConfig(pluginName = "DebugKit", namespace = "\\CakePHP\\DebugKit", pluginPath = "vendor/cakephp/debug_kit")
        )

        // Should find by explicit pluginName
        val result = settings.findPluginConfigByName("DebugKit")
        assertNotNull(result)
        assertEquals("DebugKit", result!!.pluginName)

        // Should NOT find by namespace-derived name since pluginName is set
        val resultByNamespace = settings.findPluginConfigByName("CakePHP\\DebugKit")
        assertNull(resultByNamespace)
    }

}