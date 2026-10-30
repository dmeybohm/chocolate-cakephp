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

    public function testAppTemplateRendersPrefixedPluginElement()
    {
        $this->get('/widgets/prefixedelement');
        $this->assertResponseEquals('plugin:element/plugin_only');
    }
}
