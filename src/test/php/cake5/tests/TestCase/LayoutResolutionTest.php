<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingLayoutException;

/**
 * Layout resolution, which layout navigation depends on.
 */
class LayoutResolutionTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testRenderSecondArgumentNamesLayout(): void
    {
        $this->get('/framed/renderargument');
        $this->assertResponseEquals('layout:body');
    }

    public function testBuilderSetLayoutNamesLayout(): void
    {
        $this->get('/framed/builderset');
        $this->assertResponseEquals('layout:body');
    }

    public function testLayoutAssignedInTemplateWraps(): void
    {
        $this->get('/framed/assigned');
        $this->assertResponseEquals('layout:assigned');
    }

    public function testRenderFromTemplateWrapsItsOutputAgainInTheOuterLayout(): void
    {
        $this->get('/framed/fromtemplate');
        $this->assertResponseEquals('default:layout:body');
    }

    public function testChainedBuilderSettersAllApply(): void
    {
        $this->get('/framed/chained');
        $this->assertResponseEquals('layout:chained');
    }

    public function testDataViewRendersWithoutLayout(): void
    {
        $this->get('/framed/dataview');
        $this->assertResponseEquals('json-body');
    }

    public function testLayoutPathSelectsSubdirectory(): void
    {
        $this->get('/framed/layoutpath');
        $this->assertResponseEquals('json-layout:body');
    }

    public function testPluginControllerPrefersPluginLayout(): void
    {
        $this->get('/probe/frames/shared');
        $this->assertResponseEquals('plugin-layout:body');
    }

    public function testPluginControllerFallsBackToAppLayout(): void
    {
        $this->get('/probe/frames/appfallback');
        $this->assertResponseEquals('app-layout:body');
    }

    public function testAppOverrideWinsOverPluginLayout(): void
    {
        $this->get('/probe/frames/overridden');
        $this->assertResponseEquals('override-layout:body');
    }

    public function testAppControllerDoesNotReachPluginLayoutUnprefixed(): void
    {
        $this->expectException(MissingLayoutException::class);
        $this->expectExceptionMessage('plugin_only_layout');
        $this->get('/framed/pluginunprefixed');
    }

    public function testAppControllerReachesPluginLayoutWithPrefix(): void
    {
        $this->get('/framed/pluginprefixed');
        $this->assertResponseEquals('plugin-layout:body');
    }

    public function testLayoutNamedInCellClassHasNoEffect(): void
    {
        $this->get('/widgets/showcell?name=Framed::fromclass');
        $this->assertResponseEquals('cell');
    }

    public function testLayoutNamedInCellTemplateHasNoEffect(): void
    {
        $this->get('/widgets/showcell?name=Framed::fromtemplate');
        $this->assertResponseEquals('cell');
    }

    public function testBuilderHasNoLayoutMethod(): void
    {
        $this->expectException(\Error::class);
        $this->expectExceptionMessage('layout');
        $this->get('/framed/builderdeprecated');
    }

    public function testControllerLayoutAssignmentHasNoEffect(): void
    {
        $this->get('/framed/assignment');
        $this->assertResponseEquals('default:body');
    }

    public function testControllerLayoutPropertyHasNoEffect(): void
    {
        $this->get('/declared/index');
        $this->assertResponseEquals('default:declared');
    }

    public function testViewHasNoLayoutMethod(): void
    {
        $this->expectException(\Error::class);
        $this->expectExceptionMessage('layout');
        $this->get('/framed/shorthand');
    }
}
