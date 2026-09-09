package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.intellij.icons.AllIcons
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.AssignmentExpression
import com.jetbrains.php.lang.psi.elements.FieldReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference
import com.jetbrains.php.lang.psi.elements.Variable

class ControllerLineMarkerTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
    }

    fun `test that line markers contain one entry for each type of view`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/json/movie.ctp",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(2, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "movie.ctp", containingDir = "json"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker navigates to explicit render() calls`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/json/movie.ctp",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->render("artist");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(3, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "movie.ctp", containingDir = "json"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker navigates to explicit render() calls when nested`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/json/movie.ctp",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/custom/nested.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->render("custom/nested");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(3, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "nested.ctp", containingDir = "custom"),
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "movie.ctp", containingDir = "json"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker adds markers to render calls`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/json/movie.ctp",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/custom/nested.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->render("custom/nested");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val methodReference = PsiTreeUtil.findChildOfType(myFixture.file, MethodReference::class.java, false)
        assertNotNull(methodReference)

        val markers = calculateLineMarkers(methodReference!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "nested.ctp", containingDir = "custom"),
        )
        assertEquals(expected, infos)
    }

    fun `test that a line marker is added to the method name when a corresponding view file doesn't exist`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/artist.ctp", // create Template dir
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java, false)
        assertNotNull(method?.nameIdentifier)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val path = markers.first().icon.toString()
        assertEquals(path, AllIcons.Actions.AddFile.toString())
    }

    fun `test that a line marker is added next to render call when a corresponding view file doesn't exist`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/artist.ctp", // create Template dir
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->render("custom/nested");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val methodReference = PsiTreeUtil.findChildOfType(myFixture.file, MethodReference::class.java, false)
        assertNotNull(methodReference?.firstChild?.firstChild)

        val markers = calculateLineMarkers(methodReference!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val path = markers.first().icon.toString()
        assertEquals(path, AllIcons.Actions.AddFile.toString())
    }

    fun `test that line marker is added to viewBuilder setTemplate calls`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplate("artist");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        // Find the viewBuilder() method reference, then get the $this variable from it
        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRef = allMethodRefs.find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        // Get the $this variable element (which is what the marker is placed on)
        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker is added to chained viewBuilder calls`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate("custom");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        // Find the viewBuilder() method reference, then get the $this variable from it
        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRef = allMethodRefs.find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        // Get the $this variable element (which is what the marker is placed on)
        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker handles chained calls with extra whitespace`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')  ->  setTemplate("custom");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRef = allMethodRefs.find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker handles chained calls with comment between`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Admin/edit.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplatePath('Admin') /* some comment */ ->setTemplate("edit");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRef = allMethodRefs.find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "edit.ctp", containingDir = "Admin"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker handles chained calls with newline`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Reports/Monthly/summary.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()
                    ->setTemplatePath('Reports/Monthly')
                    ->setTemplate("summary");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRef = allMethodRefs.find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "summary.ctp", containingDir = "Monthly"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker respects preceding setTemplatePath in same method`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested');
                ${'$'}this->viewBuilder()->setTemplate("custom");
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        // Find the second viewBuilder() call (the one before setTemplate)
        val allMethodRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
        val viewBuilderRefs = allMethodRefs.filter { it.name == "viewBuilder" }
        assertEquals(2, viewBuilderRefs.size)
        val secondViewBuilderRef = viewBuilderRefs[1]

        // Get the $this variable element from the second viewBuilder call
        val markers = calculateLineMarkers(secondViewBuilderRef.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker is not added to setTemplatePath calls`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested');
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val methodReference = PsiTreeUtil.findChildOfType(myFixture.file, MethodReference::class.java, false)
        assertNotNull(methodReference)

        val markers = calculateLineMarkers(methodReference!!.firstChild!!.firstChild!!,
            ControllerMethodLineMarker::class)
        // Should not create a marker for setTemplatePath alone
        assertEquals(0, markers.size)
    }

    fun `test that line marker is added to view field assignments`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->view = "artist";
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        // Find all AssignmentExpressions and locate the one with $this->view
        val allAssignments = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
        val viewAssignment = allAssignments.find {
            val fieldRef = it.variable as? FieldReference
            fieldRef?.name == "view"
        }
        assertNotNull(viewAssignment)

        // The marker should be on the $this variable element
        val fieldRef = viewAssignment!!.variable as FieldReference
        val thisVariable = fieldRef.classReference as Variable
        val markers = calculateLineMarkers(thisVariable.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that line marker is added to view field assignments with nested path`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                ${'$'}this->view = "Nested/custom";
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        val allAssignments = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
        val viewAssignment = allAssignments.find {
            val fieldRef = it.variable as? FieldReference
            fieldRef?.name == "view"
        }
        assertNotNull(viewAssignment)

        val fieldRef = viewAssignment!!.variable as FieldReference
        val thisVariable = fieldRef.classReference as Variable
        val markers = calculateLineMarkers(thisVariable.firstChild!!,
            ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val items = gotoRelatedItems(markers.first())
        assertEquals(1, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
        )
        assertEquals(expected, infos)
    }

    // Ternary / match expression tests (issue #280)

    private fun configureMovieController(body: String, vararg templates: String) {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            *templates,
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie() {
                $body
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)
    }

    fun `test that render line marker lists both branches of a ternary`() {
        configureMovieController(
            "${'$'}this->render(${'$'}this->request->is('ajax') ? 'artist' : 'film_director');",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )

        val renderRef = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .find { it.name == "render" }
        assertNotNull(renderRef)

        val markers = calculateLineMarkers(renderRef!!.firstChild!!.firstChild!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        val expected = setOf(
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that setTemplate line marker lists every arm of a match`() {
        configureMovieController(
            """
                ${'$'}this->viewBuilder()->setTemplate(match (${'$'}this->request->getParam('kind')) {
                    'one' => 'artist',
                    default => 'film_director',
                });
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )

        val viewBuilderRef = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        val expected = setOf(
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that chained setTemplatePath line marker lists both branches of a ternary template`() {
        configureMovieController(
            "${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate(${'$'}x ? 'custom' : 'other');",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Template/Movie/Nested/other.ctp",
        )

        val viewBuilderRef = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .find { it.name == "viewBuilder" }
        assertNotNull(viewBuilderRef)

        val markers = calculateLineMarkers(viewBuilderRef!!.firstChild!!.firstChild!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        val expected = setOf(
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"),
            RelatedItemInfo(filename = "other.ctp", containingDir = "Nested"),
        )
        assertEquals(expected, infos)
    }

    fun `test that view field assignment line marker lists both branches of a ternary`() {
        configureMovieController(
            "${'$'}this->view = ${'$'}this->request->is('ajax') ? 'artist' : 'film_director';",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )

        val viewAssignment = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
            .find { (it.variable as? FieldReference)?.name == "view" }
        assertNotNull(viewAssignment)

        val fieldRef = viewAssignment!!.variable as FieldReference
        val thisVariable = fieldRef.classReference as Variable
        val markers = calculateLineMarkers(thisVariable.firstChild!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        val expected = setOf(
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    fun `test that method line marker lists both branches of a ternary render`() {
        configureMovieController(
            "${'$'}this->render(${'$'}this->request->is('ajax') ? 'artist' : 'film_director');",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)

        val markers = calculateLineMarkers(method!!.nameIdentifier!!, ControllerMethodLineMarker::class)
        assertEquals(1, markers.size)

        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        val expected = setOf(
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"),
        )
        assertEquals(expected, infos)
    }

    // Local variable template tests

    private fun markersOnThisOfMethodCall(methodName: String) : List<com.intellij.codeInsight.daemon.LineMarkerInfo<*>> {
        val ref = PsiTreeUtil.findChildrenOfType(myFixture.file, MethodReference::class.java)
            .find { it.name == methodName }
        assertNotNull("Expected a $methodName() call", ref)
        return calculateLineMarkers(ref!!.firstChild!!.firstChild!!, ControllerMethodLineMarker::class)
    }

    private fun assertMarkerTargets(markers: List<com.intellij.codeInsight.daemon.LineMarkerInfo<*>>, vararg expected: RelatedItemInfo) {
        assertEquals(1, markers.size)
        val infos = getRelatedItemInfos(gotoRelatedItems(markers.first()))
        assertEquals(expected.toSet(), infos)
    }

    fun `test that render line marker resolves a local variable`() {
        configureMovieController(
            """
                ${'$'}template = 'artist';
                ${'$'}this->render(${'$'}template);
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
        )
        assertMarkerTargets(markersOnThisOfMethodCall("render"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"))
    }

    fun `test that setTemplate line marker resolves a local variable`() {
        configureMovieController(
            """
                ${'$'}template = 'artist';
                ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
        )
        assertMarkerTargets(markersOnThisOfMethodCall("viewBuilder"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"))
    }

    fun `test that setTemplate line marker lists both if else assignments of a local variable`() {
        configureMovieController(
            """
                if (${'$'}this->request->is('ajax')) {
                    ${'$'}template = 'artist';
                } else {
                    ${'$'}template = 'film_director';
                }
                ${'$'}this->viewBuilder()->setTemplate(${'$'}template);
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )
        assertMarkerTargets(markersOnThisOfMethodCall("viewBuilder"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"))
    }

    fun `test that setTemplate line marker resolves a ternary of two local variables`() {
        configureMovieController(
            """
                ${'$'}one = 'artist';
                ${'$'}two = 'film_director';
                ${'$'}this->viewBuilder()->setTemplate(${'$'}this->request->is('ajax') ? ${'$'}one : ${'$'}two);
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )
        assertMarkerTargets(markersOnThisOfMethodCall("viewBuilder"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"))
    }

    fun `test that chained setTemplatePath line marker resolves a local variable path`() {
        configureMovieController(
            """
                ${'$'}path = 'Movie/Nested';
                ${'$'}this->viewBuilder()->setTemplatePath(${'$'}path)->setTemplate('custom');
            """.trimIndent(),
            "cake3/src/Template/Movie/Nested/custom.ctp",
        )
        assertMarkerTargets(markersOnThisOfMethodCall("viewBuilder"),
            RelatedItemInfo(filename = "custom.ctp", containingDir = "Nested"))
    }

    fun `test that view field assignment line marker resolves a local variable`() {
        configureMovieController(
            """
                ${'$'}template = 'artist';
                ${'$'}this->view = ${'$'}template;
            """.trimIndent(),
            "cake3/src/Template/Movie/artist.ctp",
        )

        val viewAssignment = PsiTreeUtil.findChildrenOfType(myFixture.file, AssignmentExpression::class.java)
            .find { (it.variable as? FieldReference)?.name == "view" }
        assertNotNull(viewAssignment)
        val thisVariable = (viewAssignment!!.variable as FieldReference).classReference as Variable
        val markers = calculateLineMarkers(thisVariable.firstChild!!, ControllerMethodLineMarker::class)
        assertMarkerTargets(markers, RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"))
    }

    fun `test that no line marker is added when the variable is a method parameter`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Controller/MovieController.php",
        )
        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller
        {
            public function movie(${'$'}template) {
                ${'$'}this->render(${'$'}template);
            }
        }
        """.trimIndent())
        myFixture.openFileInEditor(lastFile.virtualFile)

        assertEquals(0, markersOnThisOfMethodCall("render").size)
    }

    fun `test that method line marker lists views chosen through local variables`() {
        configureMovieController(
            """
                ${'$'}template = 'artist';
                if (${'$'}this->request->is('ajax')) {
                    ${'$'}template = 'film_director';
                }
                ${'$'}this->render(${'$'}template);
            """.trimIndent(),
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
        )

        val method = PsiTreeUtil.findChildOfType(myFixture.file, Method::class.java)
        assertNotNull(method)
        val markers = calculateLineMarkers(method!!.nameIdentifier!!, ControllerMethodLineMarker::class)
        assertMarkerTargets(markers,
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "artist.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "film_director.ctp", containingDir = "Movie"))
    }
}
