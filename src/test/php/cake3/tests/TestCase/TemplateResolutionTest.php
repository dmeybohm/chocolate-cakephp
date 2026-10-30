<?php

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

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testPluginControllerRendersImplicitTemplateFromPlugin()
    {
        $this->get('/probe/things/index');
        $this->assertResponseEquals('plugin:Things/index');
    }

    public function testPluginControllerRendersUnprefixedTemplateFromPlugin()
    {
        $this->get('/probe/things/other');
        $this->assertResponseEquals('plugin:Things/other');
    }

    public function testPluginControllerFallsBackToAppTemplate()
    {
        $this->get('/probe/things/appfallback');
        $this->assertResponseEquals('app:Things/appfallback');
    }

    public function testAppOverrideWinsOverPluginTemplate()
    {
        $this->get('/probe/things/overridden');
        $this->assertResponseEquals('override:Things/overridden');
    }

    public function testAppControllerDoesNotReachPluginTemplateUnprefixed()
    {
        $this->expectException(MissingTemplateException::class);
        $this->expectExceptionMessage('only_in_plugin');
        $this->get('/widgets/unprefixed');
    }

    public function testAppControllerRendersPrefixedPluginTemplate()
    {
        $this->get('/widgets/prefixed');
        $this->assertResponseEquals('plugin:Widgets/only_in_plugin');
    }

    public function testRoutingPrefixResolvesToPrefixTemplate()
    {
        $this->get('/admin/widgets/index');
        $this->assertResponseEquals('app:Admin/Widgets/index');
    }

    public function testPrefixedRenderFallsBackToAppTemplate()
    {
        $this->get('/widgets/prefixedfallback');
        $this->assertResponseEquals('app:Widgets/app_only');
    }

    public function testAppOverrideWinsForPrefixedRender()
    {
        $this->get('/widgets/prefixedoverride');
        $this->assertResponseEquals('override:Widgets/overridden_widget');
    }

    public function testPluginControllerRendersOtherPluginTemplate()
    {
        $this->get('/probe/things/crossplugin');
        $this->assertResponseEquals('other:Things/in_other');
    }

    public function testRenderOfOtherPluginFallsBackToAppTemplate()
    {
        $this->get('/probe/things/crossfallback');
        $this->assertResponseEquals('app:Things/in_app');
    }

    public function testRenderOfOtherPluginDoesNotFallBackToCallingPlugin()
    {
        $this->expectException(MissingTemplateException::class);
        $this->expectExceptionMessage('in_probe');
        $this->get('/probe/things/crossnotcaller');
    }

    public function testAppOverrideWinsForRenderOfOtherPlugin()
    {
        $this->get('/probe/things/crossoverride');
        $this->assertResponseEquals('override:Things/overridden_other');
    }
}
