package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.intellij.icons.AllIcons
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference

class ControllerLineMarkerTest : Cake3BaseTestCase() {

    override fun setUpTestFiles() {
        // change app directory:
        val originalSettings = Settings.getInstance(myFixture.project)
        val newState = originalSettings.state.copy()
        newState.appDirectory = "src"
        originalSettings.loadState(newState)
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

}
