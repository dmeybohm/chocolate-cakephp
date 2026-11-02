<?php

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingElementException;

/**
 * Element resolution, which element navigation and related usages depend on.
 */
class ElementResolutionTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testPluginTemplatePrefersPluginElement()
    {
        $this->get('/probe/things/sharedelement');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testPluginTemplateFallsBackToAppElement()
    {
        $this->get('/probe/things/appelement');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testAppTemplateDoesNotReachPluginElementUnprefixed()
    {
        $this->expectException(MissingElementException::class);
        $this->expectExceptionMessage('plugin_only');
        $this->get('/widgets/pluginelement');
    }

    public function testWronglyCasedElementDirectoryIsNotFound()
    {
        $this->expectException(MissingElementException::class);
        $this->expectExceptionMessage('wrong_case');
        $this->get('/widgets/wrongcaseelement');
    }

    public function testAppTemplateRendersPrefixedPluginElement()
    {
        $this->get('/widgets/prefixedelement');
        $this->assertResponseEquals('plugin:element/plugin_only');
    }

    public function testPrefixedElementFallsBackToAppElement()
    {
        $this->get('/widgets/prefixedappelement');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testPluginTemplateRendersOtherPluginElement()
    {
        $this->get('/probe/things/crosselement');
        $this->assertResponseEquals('other:element/other_only');
    }

    public function testOtherPluginElementFallsBackToAppElement()
    {
        $this->get('/probe/things/crosselementfallback');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testOtherPluginElementDoesNotFallBackToCallingPlugin()
    {
        $this->expectException(MissingElementException::class);
        $this->expectExceptionMessage('plugin_only');
        $this->get('/probe/things/crosselementnotcaller');
    }

    public function testElementInFallbackTemplateResolvesInControllerPlugin()
    {
        $this->get('/probe/things/fallbackinner');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInPrefixedTemplateResolvesInControllerPlugin()
    {
        $this->get('/widgets/prefixedinner');
        $this->assertResponseEquals('app:element/shared');
    }

    public function testElementInOtherPluginTemplateResolvesInControllerPlugin()
    {
        $this->get('/probe/things/crossinner');
        $this->assertResponseEquals('plugin:element/shared');
    }
}
