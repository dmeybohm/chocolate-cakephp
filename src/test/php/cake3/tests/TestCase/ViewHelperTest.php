<?php

namespace App\Test\TestCase;

use App\View\ConfiguredView;
use App\View\Helper\ViewMarkerHelper;
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

    public function testViewBuiltByHandKeepsHelpersProperty()
    {
        $view = new ConfiguredView();
        $this->assertInstanceOf(ViewMarkerHelper::class, $view->ByProperty);
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

    public function testCellInitializeHelperReachesItsTemplate()
    {
        $this->get('/widgets/showcell?name=Configuring::own');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testCallerHelperWinsOverCellInitializeHelper()
    {
        $this->get('/widgets/helpercell?name=Configuring::caller');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testCellActionHelperWinsOverCallerHelper()
    {
        $this->get('/widgets/helpercell?name=Configuring::action');
        $this->assertResponseEquals('App\\View\\Helper\\ViewMarkerHelper');
    }

    public function testCellHelpersPropertyReachesItsTemplate()
    {
        $this->get('/widgets/showcell?name=Configuring::property');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testCellHelpersPropertyWinsOverCallerHelper()
    {
        $this->get('/widgets/helpercell?name=PropertyHelper');
        $this->assertResponseEquals('App\\View\\Helper\\ViewMarkerHelper');
    }
}
