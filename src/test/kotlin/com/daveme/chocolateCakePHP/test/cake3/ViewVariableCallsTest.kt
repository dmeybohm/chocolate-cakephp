package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.view.viewvariableindex.*
import com.jetbrains.php.lang.psi.resolve.types.PhpType
import java.io.*

class ViewVariableCallsTest : Cake3BaseTestCase() {
    private val root = "cake3/src/Template"
    private val extension = "ctp"
    private val elementKey = "Element/review"
    private val settings get() = Settings.getInstance(project)

    override fun setUpTestFiles() {
        myFixture.configureByFiles("cake3/vendor/cakephp.php")
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
            ${'$'}this->element('review');
        """)
        val vars = variables()
        assertEquals(setOf("title", "first", "second", "data"), vars.keys)
        assertEquals(setOf("string"), vars["title"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("string"), vars["first"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("string"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "title").concreteTypeNames())
    }

    fun `test set reaches rendered elements but not the setting file`() {
        caller("${'$'}this->set('title', 1); ${'$'}this->element('review');")
        assertFalse(variables("Movie/review").containsKey("title"))
        assertFalse(ViewVariableIndexService.variableExistsInViewPath(project, settings, "Movie/review", "title"))
        assertTrue(variables().containsKey("title"))
    }

    fun `test comments between arguments are ignored`() {
        caller("""
            ${'$'}this->set('fromSet', /* c */ 1);
            ${'$'}this->element('review', /* vars */ ['title' => 'x']);
        """)
        assertTrue(exists("title"))
        assertEquals(setOf("int"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "fromSet").concreteTypeNames())
    }

    private fun controller(body: String) {
        val declaration = "namespace App\\Controller; class MovieController extends \\Cake\\Controller\\Controller"
        myFixture.addFileToProject("cake3/src/Controller/MovieController.php",
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

    fun `test compact ignores same-named variable inside a closure`() {
        controller("${'$'}this->set('movie', 42);")
        caller("""
            ${'$'}format = function (\DateTime ${'$'}movie) { return ${'$'}movie; };
            ${'$'}this->element('review', compact('movie'));
        """)
        assertEquals(setOf("int"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "movie").concreteTypeNames())
    }

    fun `test compact ignores same-named assignment after the call`() {
        controller("${'$'}this->set('movie', 42);")
        caller("""
            ${'$'}this->element('review', compact('movie'));
            ${'$'}movie = new \\DateTime();
        """)
        assertEquals(setOf("int"), ViewVariableIndexService
            .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, elementKey, "movie").concreteTypeNames())
    }

    fun `test indirect compact name is case insensitive`() {
        caller("""
            ${'$'}title = 'Example';
            ${'$'}data = COMPACT('title');
            ${'$'}this->element('review', ${'$'}data);
        """)
        assertEquals(setOf("title"), variables().keys)
        assertTrue(exists("title"))
        assertEquals(setOf("string"), variables()["title"]!!.phpType.concreteTypeNames())
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

    private fun typeOf(name: String, key: String = elementKey) = ViewVariableIndexService
        .lookupVariableTypeFromViewPathInSmartReadAction(project, settings, key, name).concreteTypeNames()

    fun `test set after element call is not visible`() {
        caller("${'$'}this->set('early', 1); ${'$'}this->element('review'); ${'$'}this->set('late', 2);")
        assertEquals(setOf("early"), variables().keys)
        assertTrue(exists("early"))
        assertFalse(exists("late"))
        assertEquals(emptySet<String>(), typeOf("late"))
    }

    fun `test set after render call is not visible`() {
        myFixture.addFileToProject("$root/Movie/other.$extension", "<?php echo 1;")
        caller("${'$'}this->set('early', 1); ${'$'}this->render('other'); ${'$'}this->set('late', 2);")
        assertEquals(setOf("early"), variables("Movie/other").keys)
        assertFalse(ViewVariableIndexService.variableExistsInViewPath(project, settings, "Movie/other", "late"))
        assertEquals(emptySet<String>(), typeOf("late", "Movie/other"))
    }

    fun `test repeated element calls union what each call sees`() {
        caller("""
            ${'$'}this->element('review');
            ${'$'}this->set('x', 1);
            ${'$'}this->element('review');
            ${'$'}this->set('x', 'a');
        """)
        assertEquals(setOf("int"), variables()["x"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int"), typeOf("x"))
    }

    fun `test value overwritten between element calls unions both types`() {
        caller("""
            ${'$'}this->set('x', 1);
            ${'$'}this->element('review');
            ${'$'}this->set('x', 'a');
            ${'$'}this->element('review');
        """)
        assertEquals(setOf("int", "string"), variables()["x"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int", "string"), typeOf("x"))
    }

    fun `test separate rendering files union their possible types`() {
        myFixture.addFileToProject("$root/Movie/first.$extension",
            "<?php ${'$'}this->set('x', 1); ${'$'}this->element('review');")
        myFixture.addFileToProject("$root/Movie/second.$extension",
            "<?php ${'$'}this->set('x', 'a'); ${'$'}this->element('review');")

        assertEquals(setOf("int", "string"), variables()["x"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int", "string"), typeOf("x"))
    }

    fun `test repeated render calls do not exhaust the lookup budget`() {
        controller("${'$'}this->set('fromController', 1);")
        myFixture.addFileToProject("$root/Element/outer.$extension", "<?php " + "${'$'}this->element('review');".repeat(4))
        caller("${'$'}this->element('outer');".repeat(4))
        assertTrue(exists("fromController"))
        assertTrue(variables().containsKey("fromController"))
        assertEquals(setOf("int"), typeOf("fromController"))
    }

    fun `test nearer and outer set on one render path union their types`() {
        myFixture.addFileToProject("$root/Element/outer.$extension",
            "<?php ${'$'}this->set('x', 1); ${'$'}this->element('review');")
        caller("${'$'}this->set('x', 'outer'); ${'$'}this->element('outer');")

        assertEquals(setOf("int", "string"), variables()["x"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int", "string"), typeOf("x"))
    }

    fun `test element data and inherited variable union their types`() {
        caller("${'$'}this->set('title', 1); ${'$'}this->element('review', ['title' => 'text']);")
        assertEquals(setOf("int", "string"), variables()["title"]!!.phpType.concreteTypeNames())
        assertEquals(setOf("int", "string"), typeOf("title"))
    }

    fun `test nested elements apply the cutoff at each render`() {
        myFixture.addFileToProject("$root/Element/outer.$extension", "<?php ${'$'}this->element('review');")
        caller("${'$'}this->set('a', 1); ${'$'}this->element('outer'); ${'$'}this->set('b', 2);")
        assertEquals(setOf("a"), variables().keys)
        assertFalse(exists("b"))
    }

    fun `test set from another file with the same view key is not visible`() {
        caller("${'$'}this->set('app', 1); ${'$'}this->element('review');")
        myFixture.addFileToProject("$root/Movie/json/review.$extension", "<?php ${'$'}this->set('leak', 1);")
        assertEquals(setOf("app"), variables().keys)
        assertFalse(exists("leak"))
        assertEquals(emptySet<String>(), typeOf("leak"))
    }

    fun `test inspection warns for variable set after element call`() {
        caller("${'$'}this->set('title', 1); ${'$'}this->element('review'); ${'$'}this->set('late', 2);")
        myFixture.enableInspections(com.jetbrains.php.lang.inspections.PhpUndefinedVariableInspection::class.java)
        myFixture.configureByFilePathAndText("$root/$elementKey.$extension", """
            <?php
            echo ${'$'}title;
            echo <error descr="Undefined variable '${'$'}late'">${'$'}late</error>;
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
