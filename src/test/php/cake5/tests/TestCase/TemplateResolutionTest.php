<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingTemplateException;

/**
 * Template resolution, which view navigation and view file creation depend on.
 */
class TemplateResolutionTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testPluginControllerRendersImplicitTemplateFromPlugin(): void
    {
        $this->get('/probe/things/index');
        $this->assertResponseEquals('plugin:Things/index');
    }

    public function testPluginControllerRendersUnprefixedTemplateFromPlugin(): void
    {
        $this->get('/probe/things/other');
        $this->assertResponseEquals('plugin:Things/other');
    }

    public function testPluginControllerFallsBackToAppTemplate(): void
    {
        $this->get('/probe/things/appfallback');
        $this->assertResponseEquals('app:Things/appfallback');
    }

    public function testAppOverrideWinsOverPluginTemplate(): void
    {
        $this->get('/probe/things/overridden');
        $this->assertResponseEquals('override:Things/overridden');
    }

    public function testAppControllerDoesNotReachPluginTemplateUnprefixed(): void
    {
        $this->expectException(MissingTemplateException::class);
        $this->expectExceptionMessage('only_in_plugin');
        $this->get('/widgets/unprefixed');
    }

    public function testAppControllerRendersPrefixedPluginTemplate(): void
    {
        $this->get('/widgets/prefixed');
        $this->assertResponseEquals('plugin:Widgets/only_in_plugin');
    }

    public function testRoutingPrefixResolvesToPrefixTemplate(): void
    {
        $this->get('/admin/widgets/index');
        $this->assertResponseEquals('app:Admin/Widgets/index');
    }
}
