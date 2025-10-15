package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.intellij.icons.AllIcons
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference

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

}
