<?php

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingCellException;
use Cake\View\Exception\MissingCellViewException;
use Cake\View\Exception\MissingHelperException;

/**
 * Cell class and template resolution, which cell navigation and cell view variables depend on.
 */
class CellTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testPluginCellRendersTemplateFromPlugin()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::own');
        $this->assertResponseEquals('plugin:cell/Gadget/own');
    }

    public function testPluginCellFallsBackToAppTemplate()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::appfallback');
        $this->assertResponseEquals('app:cell/Gadget/appfallback');
    }

    public function testAppOverrideWinsOverPluginCellTemplate()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::overridden');
        $this->assertResponseEquals('override:cell/Gadget/overridden');
    }

    public function testAppCellDoesNotReachPluginTemplate()
    {
        $this->expectException(MissingCellViewException::class);
        $this->expectExceptionMessage('pluginonly');
        $this->get('/widgets/showcell?name=Lamp::pluginonly');
    }

    public function testAppTemplateDoesNotReachPluginCellUnprefixed()
    {
        $this->expectException(MissingCellException::class);
        $this->expectExceptionMessage('Gadget');
        $this->get('/widgets/showcell?name=Gadget::own');
    }

    public function testPluginTemplateDoesNotReachOwnPluginCellUnprefixed()
    {
        $this->expectException(MissingCellException::class);
        $this->expectExceptionMessage('Gadget');
        $this->get('/probe/things/showcell?name=Gadget::own');
    }

    public function testPluginTemplateRendersAppCellUnprefixed()
    {
        $this->get('/probe/things/showcell?name=Lamp::own');
        $this->assertResponseEquals('app:cell/Lamp/own');
    }

    public function testPluginTemplateRendersOtherPluginCell()
    {
        $this->get('/probe/things/showcell?name=Other.Sprocket::own');
        $this->assertResponseEquals('other:cell/Sprocket/own');
    }

    public function testOtherPluginCellDoesNotFallBackToCallingPlugin()
    {
        $this->expectException(MissingCellViewException::class);
        $this->expectExceptionMessage('inprobe');
        $this->get('/probe/things/showcell?name=Other.Sprocket::inprobe');
    }

    public function testElementInPluginCellTemplateResolvesInCellPlugin()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementinplugin');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInFallbackCellTemplateResolvesInCellPlugin()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementinapp');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInCellCalledFromOtherPluginTemplateResolvesInCellPlugin()
    {
        $this->get('/widgets/othershowcell?name=Probe.Gadget::elementinplugin');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInAppCellCalledFromPluginResolvesInApp()
    {
        $this->get('/probe/things/showcell?name=Lamp::elementinapp');
        $this->assertResponseEquals('app:element/shared');
    }

    public function testElementInNamedCellTemplateResolvesInCellPlugin()
    {
        $this->get('/widgets/cellnamedtemplate');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInPluginCellFallsBackToAppElement()
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementappfallback');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testControllerHelperReachesItsTemplate()
    {
        $this->get('/widgets/helpertemplate');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesCellTemplate()
    {
        $this->get('/widgets/helpercell?name=Lamp::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesElementInCellTemplate()
    {
        $this->get('/widgets/helpercell?name=Lamp::helperelement');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testCellTemplateHasNoHelperItsCallerLacks()
    {
        $this->expectException(MissingHelperException::class);
        $this->expectExceptionMessage('AliasedHelper');
        $this->get('/widgets/showcell?name=Lamp::helper');
    }
}
