<?php

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingHelperException;

/**
 * Which configuration supplies a view's helpers, which view helper resolution depends on.
 */
class ViewHelperTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testViewLoadHelperConflictsWithControllerClassName()
    {
        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('has already been loaded');
        $this->get('/helpers/viewloadhelper');
    }

    public function testViewLoadHelperWinsOverControllerHelperWithoutOptions()
    {
        $this->get('/helpers/viewloadwithoutoptions');
        $this->assertResponseEquals('App\\View\\Helper\\ViewMarkerHelper');
    }

    public function testControllerBuilderReplacesViewHelpersProperty()
    {
        $this->expectException(MissingHelperException::class);
        $this->expectExceptionMessage('ByPropertyHelper');
        $this->get('/helpers/viewproperty');
    }

    public function testControllerHelperReachesLayoutAndItsElement()
    {
        $this->get('/helpers/inlayout');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper|App\\View\\Helper\\MarkerHelper');
    }

    public function testCellOwnHelperReachesItsTemplate()
    {
        $this->get('/widgets/showcell?name=Lamp::ownhelper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesPluginCellTemplate()
    {
        $this->get('/widgets/helpercell?name=Probe.Gadget::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesElementInPluginCellTemplate()
    {
        $this->get('/widgets/helpercell?name=Probe.Gadget::helperelement');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testPluginControllerHelperReachesAppCellTemplate()
    {
        $this->get('/probe/things/helpercell?name=Lamp::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelpersPropertyReachesTemplateMerged()
    {
        $this->get('/legacy-helpers/index');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper|App\\View\\Helper\\MarkerHelper');
    }
}
