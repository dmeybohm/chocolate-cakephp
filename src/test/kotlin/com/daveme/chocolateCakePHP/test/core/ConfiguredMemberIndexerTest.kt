package com.daveme.chocolateCakePHP.test.core

import com.daveme.chocolateCakePHP.configuredmemberindex.AssociationType
import com.daveme.chocolateCakePHP.configuredmemberindex.ClassConfiguration
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberExternalizer
import com.daveme.chocolateCakePHP.configuredmemberindex.ConfiguredMemberDataIndexer
import com.daveme.chocolateCakePHP.configuredmemberindex.DeclaredValue
import com.daveme.chocolateCakePHP.configuredmemberindex.LoadSource
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberKind
import com.daveme.chocolateCakePHP.configuredmemberindex.MemberLoad
import com.daveme.chocolateCakePHP.configuredmemberindex.PropertyDeclaration
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileContentImpl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ConfiguredMemberIndexerTest : BasePlatformTestCase() {

    private fun index(code: String): Map<String, ClassConfiguration> {
        myFixture.configureByText("Indexed.php", code.trimIndent())
        val fileContent = FileContentImpl.createByFile(myFixture.file.virtualFile, project)
        return ConfiguredMemberDataIndexer.map(fileContent)
    }

    private fun configurationOf(code: String, fqn: String): ClassConfiguration {
        val result = index(code)
        assertTrue("Expected key $fqn in ${result.keys}", result.containsKey(fqn))
        return result[fqn]!!
    }

    private fun loadsOf(code: String, fqn: String): List<MemberLoad> =
        configurationOf(code, fqn).loads

    private fun declarationsOf(code: String, fqn: String): Map<String, PropertyDeclaration> =
        configurationOf(code, fqn).declarations.associateBy { it.property }

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
        assertEquals(listOf("First"), result["\\App\\Controller\\FirstController"]!!.loads.map { it.alias })
        assertEquals(listOf("Second"), result["\\App\\Controller\\SecondController"]!!.loads.map { it.alias })
    }

    fun `test cake2 property arrays map to their kinds and properties`() {
        val loads = loadsOf("""
            <?php
            class MovieController extends AppController {
                public ${'$'}components = array('Session', 'Meta' => array('className' => 'MovieMetadata'), 'DebugKit.Toolbar');
                public ${'$'}uses = ['Movie', 'TestPlugin.Rating'];
                public ${'$'}helpers = array('Html' => array('className' => 'BootstrapHtml'));
            }
        """, "\\MovieController")

        assertEquals(
            listOf(
                listOf("Html", "BootstrapHtml", "helpers", MemberKind.HELPER),
                listOf("Session", null, "components", MemberKind.COMPONENT),
                listOf("Meta", "MovieMetadata", "components", MemberKind.COMPONENT),
                listOf("Toolbar", null, "components", MemberKind.COMPONENT),
                listOf("Movie", null, "uses", MemberKind.TABLE),
                listOf("Rating", null, "uses", MemberKind.TABLE),
            ),
            loads.map { listOf(it.alias, it.className, it.property, it.kind) }
        )
        assertTrue(loads.all { it.source == LoadSource.PROPERTY_ARRAY && it.isInitializeLoad })
    }

    fun `test cake2 association properties including a comma-separated string`() {
        val loads = loadsOf("""
            <?php
            class Movie extends AppModel {
                public ${'$'}belongsTo = 'Director, TestPlugin.Studio ,';
                public ${'$'}hasOne = array('Poster');
                public ${'$'}hasMany = array('Review' => array('className' => 'Critique'));
                public ${'$'}hasAndBelongsToMany = array('Tag');
            }
        """, "\\Movie")

        assertEquals(
            listOf(
                listOf("Director", "Director", null, "belongsTo"),
                listOf("Studio", "TestPlugin.Studio", null, "belongsTo"),
                listOf("Poster", "Poster", null, "hasOne"),
                listOf("Review", "Review", "Critique", "hasMany"),
                listOf("Tag", "Tag", null, "hasAndBelongsToMany"),
            ),
            loads.map { listOf(it.alias, it.loadedName, it.className, it.property) }
        )
        assertTrue(loads.all { it.kind == MemberKind.ASSOCIATION })
    }

    fun `test declarations are recorded even when they load nothing`() {
        val code = """
            <?php
            class EmptyController extends AppController {
                public ${'$'}uses = false;
                public ${'$'}components = array();
                public ${'$'}helpers = [];
            }
            class TrueController extends AppController {
                public ${'$'}uses = true;
                public ${'$'}components;
            }
            class NullController extends AppController {
                public ${'$'}uses = null;
            }
        """
        val empty = declarationsOf(code, "\\EmptyController")
        assertEquals(
            mapOf(
                "uses" to DeclaredValue.FALSE,
                "components" to DeclaredValue.ARRAY,
                "helpers" to DeclaredValue.ARRAY,
            ),
            empty.mapValues { it.value.value }
        )
        assertTrue(empty.values.all { it.isComplete })
        assertTrue(configurationOf(code, "\\EmptyController").loads.isEmpty())

        val withTrue = declarationsOf(code, "\\TrueController")
        assertEquals(DeclaredValue.TRUE, withTrue["uses"]!!.value)
        assertEquals(DeclaredValue.NULL, withTrue["components"]!!.value)
        assertEquals(DeclaredValue.NULL, declarationsOf(code, "\\NullController")["uses"]!!.value)
    }

    fun `test declarations the reader cannot fully read are incomplete`() {
        val declarations = declarationsOf("""
            <?php
            class PartialController extends AppController {
                public ${'$'}components = array(self::AUTH, 'Session');
                public ${'$'}uses = self::MODELS;
                public ${'$'}helpers = 'Html';
            }
        """, "\\PartialController")

        assertFalse(declarations["components"]!!.isComplete)
        assertEquals(DeclaredValue.ARRAY, declarations["components"]!!.value)
        assertFalse(declarations["uses"]!!.isComplete)
        assertEquals(DeclaredValue.OTHER, declarations["uses"]!!.value)
        assertFalse(declarations["helpers"]!!.isComplete)
        assertEquals(DeclaredValue.STRING, declarations["helpers"]!!.value)
    }

    fun `test a className that cannot be read is marked`() {
        val configuration = configurationOf("""
            <?php
            class AliasController extends AppController {
                public ${'$'}helpers = array(
                    'Html' => array('className' => SOME_CONST),
                    'Form' => array('className' => 'BootstrapForm'),
                    'Time' => array('format' => 'Y'),
                );
            }
        """, "\\AliasController")

        assertEquals(
            listOf(Triple("Html", null, true), Triple("Form", "BootstrapForm", false), Triple("Time", null, false)),
            configuration.loads.map { Triple(it.alias, it.className, it.hasUnreadClassName) }
        )
        assertTrue(configuration.declarations.single().isComplete)
    }

    fun `test with join models in string, dotted and array forms`() {
        val configuration = configurationOf("""
            <?php
            class Movie extends AppModel {
                public ${'$'}hasAndBelongsToMany = array(
                    'Tag' => array('with' => 'MoviesTag'),
                    'Genre' => array('with' => 'TestPlugin.MoviesGenre'),
                    'Actor' => array('with' => array('MoviesActor' => array('role'))),
                    'Award' => array('joinTable' => 'movies_awards'),
                );
            }
        """, "\\Movie")

        assertEquals(
            listOf(
                "Tag" to "Tag",
                "MoviesTag" to "MoviesTag",
                "Genre" to "Genre",
                "MoviesGenre" to "TestPlugin.MoviesGenre",
                "Actor" to "Actor",
                "MoviesActor" to "MoviesActor",
                "Award" to "Award",
            ),
            configuration.loads.map { it.alias to it.loadedName }
        )
        assertTrue(configuration.declarations.single().isComplete)
    }

    fun `test bindModel leaves on this are unioned loads`() {
        val loads = loadsOf("""
            <?php
            class Movie extends AppModel {
                public function initialize() {
                    ${'$'}this->bindModel(array(
                        'hasMany' => array('Review', 'Critic' => array('className' => 'User')),
                        'belongsTo' => array('Studio'),
                        'hasAndBelongsToMany' => array('Tag' => array('with' => 'MoviesTag')),
                        'belongsToMany' => array('Ignored'),
                    ));
                    ${'$'}this->Director->bindModel(array('hasMany' => array('NotThis')));
                }
            }
        """, "\\Movie")

        assertEquals(
            listOf(
                Triple("Review", null, "hasMany"),
                Triple("Critic", "User", "hasMany"),
                Triple("Studio", null, "belongsTo"),
                Triple("Tag", null, "hasAndBelongsToMany"),
                Triple("MoviesTag", null, "hasAndBelongsToMany"),
            ),
            loads.map { Triple(it.alias, it.className, it.property) }
        )
        assertTrue(loads.all {
            it.kind == MemberKind.ASSOCIATION && it.source == LoadSource.BIND_MODEL && !it.isInitializeLoad
        })
    }

    fun `test direct association calls record their type and other loads do not`() {
        val loads = loadsOf("""
            <?php
            namespace App\Foo;
            class Configured {
                public function initialize() {
                    ${'$'}this->belongsTo('Authors');
                    ${'$'}this->hasOne('Profiles');
                    ${'$'}this->hasMany('Comments');
                    ${'$'}this->belongsToMany('Tags');
                    ${'$'}this->loadComponent('Flash');
                    ${'$'}this->loadHelper('Html');
                }
            }
        """, "\\App\\Foo\\Configured")

        assertEquals(
            listOf(
                AssociationType.BELONGS_TO,
                AssociationType.HAS_ONE,
                AssociationType.HAS_MANY,
                AssociationType.BELONGS_TO_MANY,
                null,
                null,
            ),
            loads.map { it.associationType }
        )
    }

    fun `test addAssociations leaves record the type of their key`() {
        val loads = loadsOf("""
            <?php
            namespace App\Foo;
            class Configured {
                public function initialize() {
                    ${'$'}this->addAssociations([
                        'belongsTo' => ['Authors'],
                        'hasOne' => ['Profiles'],
                        'hasMany' => ['Comments' => ['propertyName' => 'notes']],
                        'belongsToMany' => ['Tags'],
                    ]);
                }
            }
        """, "\\App\\Foo\\Configured")

        assertEquals(
            mapOf(
                "Authors" to AssociationType.BELONGS_TO,
                "Profiles" to AssociationType.HAS_ONE,
                "Comments" to AssociationType.HAS_MANY,
                "Tags" to AssociationType.BELONGS_TO_MANY,
            ),
            loads.associate { it.alias to it.associationType }
        )
        assertEquals("notes", loads.first { it.alias == "Comments" }.propertyName)
    }

    fun `test propertyName option literal, absent and unreadable`() {
        val loads = loadsOf("""
            <?php
            namespace App\Foo;
            class Configured {
                const WRITER = 'writer';
                public function initialize() {
                    ${'$'}this->belongsTo('Literal', ['propertyName' => 'writer']);
                    ${'$'}this->belongsTo('Absent', ['className' => 'Users']);
                    ${'$'}this->belongsTo('Unread', ['propertyName' => self::WRITER]);
                    ${'$'}this->loadComponent('Flash', ['propertyName' => 'ignored']);
                }
            }
        """, "\\App\\Foo\\Configured").associateBy { it.alias }

        assertEquals("writer", loads["Literal"]!!.propertyName)
        assertFalse(loads["Literal"]!!.hasUnreadPropertyName)
        assertNull(loads["Absent"]!!.propertyName)
        assertFalse(loads["Absent"]!!.hasUnreadPropertyName)
        assertNull(loads["Unread"]!!.propertyName)
        assertTrue(loads["Unread"]!!.hasUnreadPropertyName)
        assertNull(loads["Flash"]!!.propertyName)
        assertFalse(loads["Flash"]!!.hasUnreadPropertyName)
    }

    fun `test cake2 properties and bindModel record their type`() {
        val loads = loadsOf("""
            <?php
            class Post extends AppModel {
                public ${'$'}belongsTo = array('User');
                public ${'$'}hasOne = array('Detail');
                public ${'$'}hasMany = 'Comment';
                public ${'$'}hasAndBelongsToMany = array('Tag');
                public function bind() {
                    ${'$'}this->bindModel(array(
                        'belongsTo' => array('Boundbelongs'),
                        'hasOne' => array('Boundone'),
                        'hasMany' => array('Boundmany'),
                        'hasAndBelongsToMany' => array('Boundhabtm'),
                    ));
                }
            }
        """, "\\Post")

        assertEquals(
            mapOf(
                "User" to AssociationType.BELONGS_TO,
                "Detail" to AssociationType.HAS_ONE,
                "Comment" to AssociationType.HAS_MANY,
                "Tag" to AssociationType.BELONGS_TO_MANY,
                "Boundbelongs" to AssociationType.BELONGS_TO,
                "Boundone" to AssociationType.HAS_ONE,
                "Boundmany" to AssociationType.HAS_MANY,
                "Boundhabtm" to AssociationType.BELONGS_TO_MANY,
            ),
            loads.associate { it.alias to it.associationType }
        )
    }

    fun `test habtm join model has no association type`() {
        val loads = loadsOf("""
            <?php
            class Post extends AppModel {
                public ${'$'}hasAndBelongsToMany = array(
                    'Tag' => array('with' => 'PostsTag'),
                );
            }
        """, "\\Post").associateBy { it.alias }

        assertEquals(AssociationType.BELONGS_TO_MANY, loads["Tag"]!!.associationType)
        assertNull(loads["PostsTag"]!!.associationType)
    }

    fun `test the externalizer round trips the association fields`() {
        val configuration = configurationOf("""
            <?php
            namespace App\Foo;
            class Configured {
                public function initialize() {
                    ${'$'}this->belongsTo('Literal', ['propertyName' => 'writer']);
                    ${'$'}this->hasMany('Unread', ['propertyName' => self::WRITER]);
                    ${'$'}this->loadComponent('Flash');
                }
            }
        """, "\\App\\Foo\\Configured")

        val bytes = ByteArrayOutputStream()
        ConfiguredMemberExternalizer.save(DataOutputStream(bytes), configuration)
        val read = ConfiguredMemberExternalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals(configuration, read)
    }
}
