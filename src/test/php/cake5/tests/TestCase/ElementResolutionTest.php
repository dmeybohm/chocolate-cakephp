<?php
declare(strict_types=1);

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

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testPluginTemplatePrefersPluginElement(): void
    {
        $this->get('/probe/things/sharedelement');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testPluginTemplateFallsBackToAppElement(): void
    {
        $this->get('/probe/things/appelement');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testAppTemplateDoesNotReachPluginElementUnprefixed(): void
    {
        $this->expectException(MissingElementException::class);
        $this->expectExceptionMessage('plugin_only');
        $this->get('/widgets/pluginelement');
    }

    public function testAppTemplateRendersPrefixedPluginElement(): void
    {
        $this->get('/widgets/prefixedelement');
        $this->assertResponseEquals('plugin:element/plugin_only');
    }

    public function testPrefixedElementFallsBackToAppElement(): void
    {
        $this->get('/widgets/prefixedappelement');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testPluginTemplateRendersOtherPluginElement(): void
    {
        $this->get('/probe/things/crosselement');
        $this->assertResponseEquals('other:element/other_only');
    }

    public function testOtherPluginElementFallsBackToAppElement(): void
    {
        $this->get('/probe/things/crosselementfallback');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testOtherPluginElementDoesNotFallBackToCallingPlugin(): void
    {
        $this->expectException(MissingElementException::class);
        $this->expectExceptionMessage('plugin_only');
        $this->get('/probe/things/crosselementnotcaller');
    }

    public function testElementInFallbackTemplateResolvesInControllerPlugin(): void
    {
        $this->get('/probe/things/fallbackinner');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInPrefixedTemplateResolvesInControllerPlugin(): void
    {
        $this->get('/widgets/prefixedinner');
        $this->assertResponseEquals('app:element/shared');
    }

    public function testElementInOtherPluginTemplateResolvesInControllerPlugin(): void
    {
        $this->get('/probe/things/crossinner');
        $this->assertResponseEquals('plugin:element/shared');
    }
}
