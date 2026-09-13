package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.view.viewvariableindex.*
import java.io.*
import com.jetbrains.php.lang.psi.resolve.types.PhpType

abstract class ViewVariableCallsTestBase : BaseTestCase() {
    abstract val version: Int
    private val root get() = when (version) {
        2 -> "cake2/app/View"
        3 -> "cake3/src/Template"
        else -> "cake$version/templates"
    }
    private val extension get() = if (version < 4) "ctp" else "php"
    private val elementKey get() = if (version < 4) "Element/review" else "element/review"
    private val settings get() = Settings.getInstance(project)

    override fun setUp() {
        super.setUp()
        val state = Settings.getDefaults(project).state.copy()
        state.cake2Enabled = version == 2
        state.cake3ForceEnabled = version != 2
        state.cake3Enabled = version != 2
        state.cake2AppDirectory = "app"
        state.appDirectory = when (version) { 3 -> "src"; else -> "src$version" }
        settings.loadState(state)
        myFixture.configureByFiles("cake$version/vendor/cakephp.php")
        myFixture.addFileToProject("$root/$elementKey.$extension", "<?php echo \$title;")
    }

    // The PHP plugin namespaces primitives and can retain an unknown marker alongside inferred types.
    private fun PhpType.concreteTypeNames() = types.map { it.removePrefix("\\") }.filterNot { it == "?" }.toSet()

    private fun caller(code: String) = myFixture.addFileToProject("$root/Movie/review.$extension", "<?php " + code)
    private fun variables(key: String = elementKey) =
        ViewVariableIndexService.lookupVariablesFromViewPathInSmartReadAction(project, settings, key)
    private fun exists(name: String) =
        ViewVariableIndexService.variableExistsInViewPath(project, settings, elementKey, name)

    fun `test reused array container retains both calls`() {
        caller("""
            ${'$'}data = ['first' => 1];
            ${'$'}this->element('review', ${'$'}data);
            ${'$'}data = ['second' => 2];
            ${'$'}this->element('review', ${'$'}data);
        """)
        assertEquals(setOf("first", "second"), variables().keys)
        assertTrue(exists("first"))
        assertFalse(exists("data"))
    }

    fun `test literal key and container name do not collide`() {
        caller("""
            ${'$'}this->element('review', ['data' => 1]);
            ${'$'}data = ['second' => 2];
            ${'$'}this->element('review', ${'$'}data);
        """)
        assertEquals(setOf("data", "second"), variables().keys)
        assertTrue(exists("data"))
    }

    fun `test later literal key does not erase container`() {
        caller("""
            ${'$'}data = ['second' => 2];
            ${'$'}this->element('review', ${'$'}data);
            ${'$'}this->element('review', ['data' => 1]);
        """)
        assertEquals(setOf("data", "second"), variables().keys)
    }

    fun `test container name is not passed but a matching real key is`() {
        caller("""
            ${'$'}data = ['title' => 'Example'];
            ${'$'}this->element('review', ${'$'}data);
            ${'$'}actual = ['actual' => 1];
            ${'$'}this->element('review', ${'$'}actual);
            ${'$'}this->element('review', ${'$'}unknown);
        """)
        assertFalse(exists("data"))
        assertFalse(exists("unknown"))
        assertTrue(exists("title"))
        assertTrue(exists("actual"))
    }

    fun `test reused compact containers retain names and union types`() {
        caller("""
            ${'$'}first = 1;
            ${'$'}data = compact('first');
            ${'$'}this->element('review', ${'$'}data);
            ${'$'}second = 'text';
            ${'$'}data = compact('second');
            ${'$'}this->element('review', ${'$'}data);
            ${'$'}this->element('review', ['first' => 'text']);
        """)
        val vars = variables()
        assertEquals(setOf("first", "second"), vars.keys)
        assertEquals(setOf("int", "string"), vars["first"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int", "string"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "first").concreteTypeNames())
    }

    fun `test set merges expanded entries in call order`() {
        caller("""
            ${'$'}this->set('title', 1);
            ${'$'}data = ['title' => 'text', 'first' => 1];
            ${'$'}this->set(${'$'}data);
            ${'$'}data = ['second' => 2];
            ${'$'}this->set(${'$'}data);
            ${'$'}this->set('data', 3);
            ${'$'}this->set('first', 'last');
        """)
        val vars = variables("Movie/review")
        assertEquals(setOf("title", "first", "second", "data"), vars.keys)
        assertEquals(setOf("string"), vars["title"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("string"), vars["first"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("string"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, "Movie/review", "title").concreteTypeNames())
    }

    private fun controller(body: String) {
        val app = if (version == 2) "app" else if (version == 3) "src" else "src$version"
        val declaration = if (version == 2) "class MovieController extends AppController"
            else "namespace App\\Controller; class MovieController extends \\Cake\\Controller\\Controller"
        myFixture.addFileToProject("cake$version/$app/Controller/MovieController.php",
            "<?php $declaration { public function review() { $body } }")
    }

    fun `test controller set preserves expanded calls and concrete overrides`() {
        controller("""
            ${'$'}data = ['first' => 1];
            ${'$'}this->set(${'$'}data);
            ${'$'}data = ['second' => 2];
            ${'$'}this->set(${'$'}data);
            ${'$'}this->set('data', 'literal');
            ${'$'}this->set('first', 'last');
        """)
        caller("echo 1;")
        val vars = variables("Movie/review")
        assertEquals(setOf("first", "second", "data"), vars.keys)
        assertEquals(setOf("string"), vars["first"]!!.phpType.concreteTypeNames())
    }

    fun `test compact forwards inherited variable without local reference`() {
        controller("${'$'}this->set('movie', 42);")
        caller("${'$'}this->element('review', compact('movie'));")
        assertEquals(setOf("int"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "movie").concreteTypeNames())
    }

    fun `test inspection warns only for unpassed container`() {
        caller("${'$'}data = ['title' => 'Example']; ${'$'}this->element('review', ${'$'}data);")
        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)
        myFixture.configureByFilePathAndText("$root/$elementKey.$extension", """
            <?php
            echo ${'$'}title;
            echo <error descr="Undefined variable '${'$'}data'">${'$'}data</error>;
        """.trimIndent())
        myFixture.checkHighlighting(true, false, false)
    }

    fun `test ordered calls survive serialization`() {
        val raw = RawViewVar("data", VarKind.VARIABLE_ARRAY, 20, VarHandle(SourceKind.LOCAL, "data", 20))
        val records = ViewVariablesWithRawVars(mutableListOf(
            ViewVariableCall(10, listOf(raw)),
            ViewVariableCall(40, listOf(raw.copy(offset = 50, varHandle = raw.varHandle.copy(offset = 50)))),
            ViewVariableCall(60, listOf(raw.copy(varKind = VarKind.ARRAY)))))
        val bytes = ByteArrayOutputStream()
        ViewVariableRawVarsExternalizer.save(DataOutputStream(bytes), records)
        val restored = ViewVariableRawVarsExternalizer.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals(records, restored)
        assertEquals(records.hashCode(), restored.hashCode())
    }
}
