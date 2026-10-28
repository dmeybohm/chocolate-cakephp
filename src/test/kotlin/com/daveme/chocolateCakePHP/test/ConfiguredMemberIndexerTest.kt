package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberDataIndexer
import com.daveme.chocolateCakePHP.configuredmemberindex.LoadSource
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberLoad
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl

class ConfiguredMemberIndexerTest : BasePlatformTestCase() {

    private fun index(code: String): Map<String, List<MemberLoad>> {
        myFixture.configureByText("Indexed.php", code.trimIndent())
        val fileContent = FileContentImpl.createByFile(myFixture.file.virtualFile, project)
        return ConfiguredMemberDataIndexer.map(fileContent)
    }

    private fun loadsOf(code: String, fqn: String): List<MemberLoad> {
        val result = index(code)
        assertTrue("Expected key $fqn in ${result.keys}", result.containsKey(fqn))
        return result[fqn]!!
    }

    fun `test each source method maps to its kind`() {
        val loads = loadsOf("""
            <?php
            namespace App\Foo;
            class Configured {
                public function initialize() {
                    ${'$'}this->loadComponent('Flash');
                    ${'$'}this->loadModel('Movies');
                    ${'$'}this->loadHelper('Html');
                    ${'$'}this->addHelper('Form');
                    ${'$'}this->belongsTo('Authors');
                    ${'$'}this->hasOne('Profiles');
                    ${'$'}this->hasMany('Comments');
                    ${'$'}this->belongsToMany('Tags');
                    ${'$'}this->addBehavior('Timestamp');
                }
            }
        """, "\\App\\Foo\\Configured")

        assertEquals(
            listOf(
                "Flash" to MemberKind.COMPONENT,
                "Movies" to MemberKind.TABLE,
                "Html" to MemberKind.HELPER,
                "Form" to MemberKind.HELPER,
                "Authors" to MemberKind.ASSOCIATION,
                "Profiles" to MemberKind.ASSOCIATION,
                "Comments" to MemberKind.ASSOCIATION,
                "Tags" to MemberKind.ASSOCIATION,
            ),
            loads.map { it.alias to it.kind }
        )
        assertTrue(loads.all { it.source == LoadSource.METHOD_CALL && it.methodName == "initialize" })
        assertTrue(loads.all { it.isInitializeLoad })
    }

    fun `test plain, aliased, class constant and plugin loads`() {
        val loads = loadsOf("""
            <?php
            namespace App\Controller;
            use App\Controller\Component\MovieMetadataComponent;
            class ConfiguredController {
                public function initialize(): void {
                    ${'$'}this->loadComponent('MovieMetadata');
                    ${'$'}this->loadComponent('Meta', ['className' => MovieMetadataComponent::class]);
                    ${'$'}this->loadComponent('Legacy', ['className' => 'MovieMetadata']);
                    ${'$'}this->loadComponent('TestPlugin.InsidePlugin');
                    ${'$'}this->loadComponent(${'$'}name);
                    ${'$'}this->loadComponent('Concat' . ${'$'}suffix);
                }
            }
        """, "\\App\\Controller\\ConfiguredController")

        assertEquals(4, loads.size)
        val byAlias = loads.associateBy { it.alias }

        assertEquals("MovieMetadata", byAlias["MovieMetadata"]!!.loadedName)
        assertNull(byAlias["MovieMetadata"]!!.className)

        assertEquals("\\App\\Controller\\Component\\MovieMetadataComponent", byAlias["Meta"]!!.className)
        assertEquals("MovieMetadata", byAlias["Legacy"]!!.className)

        assertEquals("TestPlugin.InsidePlugin", byAlias["InsidePlugin"]!!.loadedName)
        assertNull(byAlias["InsidePlugin"]!!.className)
    }

    fun `test loads outside initialize keep their enclosing method name`() {
        val loads = loadsOf("""
            <?php
            namespace App\Controller;
            class ConfiguredController {
                public function initialize(): void {
                    if (${'$'}x) {
                        ${'$'}this->loadComponent('Meta');
                    } else {
                        ${'$'}this->loadComponent('Meta', ['className' => 'Other']);
                    }
                }
                public function beforeFilter() {
                    ${'$'}this->loadComponent('TestPlugin.Conditional');
                }
                public function index() {
                    ${'$'}this->loadComponent('Meta', ['className' => 'Conditional']);
                    ${'$'}other->loadComponent('NotThis');
                }
            }
        """, "\\App\\Controller\\ConfiguredController")

        assertEquals(
            listOf(
                Triple("Meta", "initialize", null),
                Triple("Meta", "initialize", "Other"),
                Triple("Conditional", "beforeFilter", null),
                Triple("Meta", "index", "Conditional"),
            ),
            loads.map { Triple(it.alias, it.methodName, it.className) }
        )
        assertEquals(listOf(true, true, false, false), loads.map { it.isInitializeLoad })
    }

    fun `test loadModel second argument is the model type, not options`() {
        val loads = loadsOf("""
            <?php
            namespace App\Controller;
            class ConfiguredController {
                public function initialize(): void {
                    ${'$'}this->loadModel('Films', ['className' => 'Movies']);
                    ${'$'}this->loadModel('Movies', 'Table');
                }
            }
        """, "\\App\\Controller\\ConfiguredController")

        assertEquals(
            listOf("Films" to null, "Movies" to null),
            loads.map { it.alias to it.className }
        )
    }

    fun `test helpers property array`() {
        val loads = loadsOf("""
            <?php
            namespace App\View\Helper;
            class ConfiguredHelper {
                protected array ${'$'}helpers = ['MovieFormatter', 'Alias' => ['className' => 'ArtistFormatter'], 'TestPlugin.Shared'];
            }
        """, "\\App\\View\\Helper\\ConfiguredHelper")

        assertEquals(
            listOf(
                Triple("MovieFormatter", "MovieFormatter", null),
                Triple("Alias", "Alias", "ArtistFormatter"),
                Triple("Shared", "TestPlugin.Shared", null),
            ),
            loads.map { Triple(it.alias, it.loadedName, it.className) }
        )
        assertTrue(loads.all {
            it.kind == MemberKind.HELPER && it.source == LoadSource.PROPERTY_ARRAY &&
                    it.methodName == null && it.isInitializeLoad
        })
    }

    fun `test addAssociations leaves`() {
        val loads = loadsOf("""
            <?php
            namespace App\Model\Table;
            class ReviewsTable {
                public function initialize(array ${'$'}config): void {
                    ${'$'}this->addAssociations([
                        'belongsTo' => ['Editors' => ['className' => 'Users'], 'Movies'],
                        'hasOne' => ['Profiles'],
                        'hasMany' => ['Comments', 'TestPlugin.Ratings'],
                        'belongsToMany' => ['Tags' => ['joinTable' => 'reviews_tags']],
                        'unknownKey' => ['Ignored'],
                    ]);
                }
            }
        """, "\\App\\Model\\Table\\ReviewsTable")

        assertEquals(
            listOf(
                Triple("Editors", "Editors", "Users"),
                Triple("Movies", "Movies", null),
                Triple("Profiles", "Profiles", null),
                Triple("Comments", "Comments", null),
                Triple("Ratings", "TestPlugin.Ratings", null),
                Triple("Tags", "Tags", null),
            ),
            loads.map { Triple(it.alias, it.loadedName, it.className) }
        )
        assertTrue(loads.all {
            it.kind == MemberKind.ASSOCIATION && it.source == LoadSource.METHOD_CALL &&
                    it.methodName == "initialize"
        })
    }

    fun `test two classes in one file are keyed separately`() {
        val result = index("""
            <?php
            namespace App\Controller;
            class FirstController {
                public function initialize(): void {
                    ${'$'}this->loadComponent('First');
                }
            }
            class SecondController {
                public function initialize(): void {
                    ${'$'}this->loadComponent('Second');
                }
            }
            class EmptyController {}
        """)

        assertEquals(setOf("\\App\\Controller\\FirstController", "\\App\\Controller\\SecondController"), result.keys)
        assertEquals(listOf("First"), result["\\App\\Controller\\FirstController"]!!.map { it.alias })
        assertEquals(listOf("Second"), result["\\App\\Controller\\SecondController"]!!.map { it.alias })
    }
}
