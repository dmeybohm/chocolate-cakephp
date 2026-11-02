<?php
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

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testRenderSecondArgumentNamesLayout()
    {
        $this->get('/framed/renderargument');
        $this->assertResponseEquals('layout:body');
    }

    public function testBuilderSetLayoutNamesLayout()
    {
        $this->get('/framed/builderset');
        $this->assertResponseEquals('layout:body');
    }

    public function testLayoutAssignedInTemplateWraps()
    {
        $this->get('/framed/assigned');
        $this->assertResponseEquals('layout:assigned');
    }

    public function testRenderFromTemplateWrapsItsOutputAgainInTheOuterLayout()
    {
        $this->get('/framed/fromtemplate');
        $this->assertResponseEquals('default:layout:body');
    }

    public function testChainedBuilderSettersAllApply()
    {
        $this->get('/framed/chained');
        $this->assertResponseEquals('layout:chained');
    }

    public function testDataViewRendersWithoutLayout()
    {
        $this->get('/framed/dataview');
        $this->assertResponseEquals('json-body');
    }

    public function testLayoutPathSelectsSubdirectory()
    {
        $this->get('/framed/layoutpath');
        $this->assertResponseEquals('json-layout:body');
    }

    public function testPluginControllerPrefersPluginLayout()
    {
        $this->get('/probe/frames/shared');
        $this->assertResponseEquals('plugin-layout:body');
    }

    public function testPluginControllerFallsBackToAppLayout()
    {
        $this->get('/probe/frames/appfallback');
        $this->assertResponseEquals('app-layout:body');
    }

    public function testAppOverrideWinsOverPluginLayout()
    {
        $this->get('/probe/frames/overridden');
        $this->assertResponseEquals('override-layout:body');
    }

    public function testAppControllerDoesNotReachPluginLayoutUnprefixed()
    {
        $this->expectException(MissingLayoutException::class);
        $this->expectExceptionMessage('plugin_only_layout');
        $this->get('/framed/pluginunprefixed');
    }

    public function testAppControllerReachesPluginLayoutWithPrefix()
    {
        $this->get('/framed/pluginprefixed');
        $this->assertResponseEquals('plugin-layout:body');
    }

    public function testLayoutNamedInCellClassHasNoEffect()
    {
        $this->get('/widgets/showcell?name=Framed::fromclass');
        $this->assertResponseEquals('cell');
    }

    public function testLayoutNamedInCellTemplateWraps()
    {
        $this->get('/widgets/showcell?name=Framed::fromtemplate');
        $this->assertResponseEquals('layout:cell');
    }

    public function testBuilderLayoutNamesLayout()
    {
        $this->deprecated(function () {
            $this->get('/framed/builderdeprecated');
        });
        $this->assertResponseEquals('layout:body');
    }

    public function testControllerLayoutAssignmentNamesLayout()
    {
        $this->deprecated(function () {
            $this->get('/framed/assignment');
        });
        $this->assertResponseEquals('layout:body');
    }

    public function testControllerLayoutPropertyNamesLayout()
    {
        $this->deprecated(function () {
            $this->get('/declared/index');
        });
        $this->assertResponseEquals('layout:declared');
    }

    public function testViewLayoutMethodNamesLayout()
    {
        $this->deprecated(function () {
            $this->get('/framed/shorthand');
        });
        $this->assertResponseEquals('layout:shorthand');
    }
}
