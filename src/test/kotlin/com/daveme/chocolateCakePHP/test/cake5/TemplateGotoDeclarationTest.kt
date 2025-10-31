package com.daveme.chocolateCakePHP.test.cake5

import com.daveme.chocolateCakePHP.test.configureByFilePathAndText
import com.daveme.chocolateCakePHP.view.TemplateGotoDeclarationHandler

class TemplateGotoDeclarationTest : Cake5BaseTestCase() {
    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake5/src5/Controller/AppController.php",
            "cake5/src5/Controller/MovieController.php",
            "cake5/src5/View/AppView.php",
            "cake5/templates/Movie/artist.php",
            "cake5/templates/Movie/film_director.php",
            "cake5/templates/Movie/Nested/custom.php",
            "cake5/templates/Movie/AnotherPath/different.php",
            "cake5/templates/Admin/edit.php",
            "cake5/templates/Reports/Monthly/summary.php",
            "cake5/templates/User/Profile/view.php",
            "cake5/vendor/cakephp.php",
        )
    }

    fun `test TemplateGotoDeclarationHandler can go to render calls`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "film_director.php")
    }


    fun `test TemplateGotoDeclarationHandler does not go to render calls on other objects`() {

        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "artist.php")
    }

    fun `test TemplateGotoDeclarationHandler can go to viewBuilder setTemplate with setTemplatePath`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.php")
    }

    fun `test TemplateGotoDeclarationHandler handles multiple setTemplatePath calls`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "different.php")
    }

    fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls clicking on template`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.php")
    }

    fun `test TemplateGotoDeclarationHandler with chained viewBuilder calls clicking on path`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        assertGotoDeclarationHandlerGoesToFilename(handler, "custom.php")
    }

    // Chain walking tests - structural vs offset-based

    fun `test viewBuilder chain with extra whitespace`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
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

    fun `test viewBuilder chain with multiple formatting variations`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()
                    ->  setTemplatePath(  'User/Profile'  )
                        ->
                    setTemplate(  '<caret>view'  );
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD resolve the chained path even with complex formatting, but currently doesn't
        assertNotNull("Expected goto declaration to work with chained viewBuilder calls", elements)
        assertFalse("Expected to find navigation target for chained viewBuilder call", elements!!.isEmpty())
    }

    // Path normalization tests

    fun `test viewBuilder path with double slashes is normalized`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()->setTemplatePath('Movie//Nested')->setTemplate('<caret>custom');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD normalize double slashes in path, but currently doesn't
        assertNotNull("Expected goto declaration to normalize path with double slashes", elements)
        assertFalse("Expected to find navigation target with normalized path", elements!!.isEmpty())
    }

    fun `test viewBuilder path with whitespace is normalized`() {
        myFixture.configureByFilePathAndText("cake5/src5/Controller/MovieController.php", """
        <?php

        namespace App\Controller;

        use Cake\Controller\Controller;

        class MovieController extends Controller {
            public function test() {
                ${'$'}this->viewBuilder()->setTemplatePath(' Movie/Nested ')->setTemplate('<caret>custom');
            }
        }
        """.trimIndent())
        val handler = TemplateGotoDeclarationHandler()
        val elements = gotoDeclarationHandlerTargets(handler)
        // SHOULD normalize whitespace in path, but currently doesn't
        assertNotNull("Expected goto declaration to normalize path with whitespace", elements)
        assertFalse("Expected to find navigation target with normalized path", elements!!.isEmpty())
    }
}