package com.daveme.chocolateCakePHP.test

import com.daveme.chocolateCakePHP.controller.ControllerMethodLineMarker
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Test
import com.jetbrains.php.lang.psi.elements.Method

class ViewLineMarkerTest : BaseTestCase() {

    @Test
    fun `test that line markers contain one entry for each type of view`() {
        val files = myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/vendor/cakephp.php",
            "cake3/src/Template/Movie/json/movie.ctp",
            "cake3/src/Template/Movie/movie.ctp",
            "cake3/src/Controller/MovieController.php",
        )

        val lastFile = files.last()
        myFixture.saveText(lastFile.virtualFile, """"
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
        assertEquals(markers.size, 1)

        val items = gotoRelatedItems(markers.first())
        assertEquals(2, items.size)

        val infos = getRelatedItemInfos(items)
        val expected = setOf(
            RelatedItemInfo(filename = "movie.ctp", containingDir = "Movie"),
            RelatedItemInfo(filename = "movie.ctp", containingDir = "json"),
        )
        assertEquals(expected, infos)
    }

    @Test
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
        myFixture.saveText(lastFile.virtualFile, """"
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
        assertEquals(markers.size, 1)

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

    @Test
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
        myFixture.saveText(lastFile.virtualFile, """"
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
        assertEquals(markers.size, 1)

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

}