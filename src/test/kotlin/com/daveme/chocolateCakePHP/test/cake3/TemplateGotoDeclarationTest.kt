package com.daveme.chocolateCakePHP.test.cake3

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler

class TemplateGotoDeclarationTest : Cake3BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake3/src/Controller/AppController.php",
            "cake3/src/Controller/MovieController.php",
            "cake3/src/View/AppView.php",
            "cake3/src/Template/Movie/artist.ctp",
            "cake3/src/Template/Movie/film_director.ctp",
            "cake3/src/Template/Movie/Nested/custom.ctp",
            "cake3/src/Template/Movie/AnotherPath/different.ctp",
            "cake3/src/Template/Admin/edit.ctp",
            "cake3/src/Template/Reports/Monthly/summary.ctp",
            "cake3/src/Template/User/Profile/view.ctp",
            "cake3/vendor/cakephp.php",
        )
    }

    fun `test TemplateGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function artist() {
                ${'$'}this->render('<caret>film_director');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "film_director.ctp")
    }


    fun `test TemplateGotoDeclarationHandler does not go to render calls on other objects`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function artist() {
                ${'$'}someOtherObject = new SomeOtherObject();
                ${'$'}someOtherObject->render('<caret>film_director');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        assertNotNull(elements)
       assertTrue(elements!!.isEmpty())
    }

    fun `test TemplateGotoDeclarationHandler can go to viewBuilder setTemplate calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function viewBuilderTest() {
                ${'$'}this->viewBuilder()->setTemplate('<caret>artist');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "artist.ctp")
    }

    fun `test TemplateGotoDeclarationHandler can go to viewBuilder setTemplate with setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function viewBuilderWithPathTest() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested');
                ${'$'}this->viewBuilder()->setTemplate('<caret>custom');
            }
        }
        """.trimIndent())

        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.ctp")
    }

    fun `test TemplateGotoDeclarationHandler handles multiple setTemplatePath calls`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function multipleSetTemplatePathTest() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested');
                ${'$'}this->viewBuilder()->setTemplate('custom');

                // Change path - this should affect the next setTemplate
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/AnotherPath');
                ${'$'}this->viewBuilder()->setTemplate('<caret>different');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "different.ctp")
    }

    fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls clicking on template`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function chainedTest() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('<caret>custom');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.ctp")
    }

    fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls clicking on path`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function chainedTest() {
                ${'$'}this->viewBuilder()->setTemplatePath('<caret>Movie/Nested')->setTemplate('custom');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.ctp")
    }

    // Chain walking tests - structural vs offset-based

    fun `test viewBuilder chain with extra whitespace`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie/Nested')  ->  setTemplate('<caret>custom');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD resolve the chained path even with extra whitespace, but currently doesn't
        assertNotNull("Expected goto declaration to work with chained viewBuilder calls", elements)
        assertFalse("Expected to find navigation target for chained viewBuilder call", elements!!.isEmpty())
    }

    fun `test viewBuilder chain with comment between`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()->setTemplatePath('Admin') /* some comment */ ->setTemplate('<caret>edit');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD resolve the chained path even with comment between, but currently doesn't
        assertNotNull("Expected goto declaration to work with chained viewBuilder calls", elements)
        assertFalse("Expected to find navigation target for chained viewBuilder call", elements!!.isEmpty())
    }

    fun `test viewBuilder chain with newline`() {
        myFixture.configureByFilePathAndText("cake3/src/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()
                    ->setTemplatePath('Reports/Monthly')
                    ->setTemplate('<caret>summary');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD resolve the chained path even with newlines, but currently doesn't
        assertNotNull("Expected goto declaration to work with chained viewBuilder calls", elements)
        assertFalse("Expected to find navigation target for chained viewBuilder call", elements!!.isEmpty())
    }
}