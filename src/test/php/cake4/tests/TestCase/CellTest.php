<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingCellException;
use Cake\View\Exception\MissingCellTemplateException;
use Cake\View\Exception\MissingHelperException;

/**
 * Cell class and template resolution, which cell navigation and cell view variables depend on.
 */
class CellTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testPluginCellRendersTemplateFromPlugin(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::own');
        $this->assertResponseEquals('plugin:cell/Gadget/own');
    }

    public function testPluginCellFallsBackToAppTemplate(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::appfallback');
        $this->assertResponseEquals('app:cell/Gadget/appfallback');
    }

    public function testAppOverrideWinsOverPluginCellTemplate(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::overridden');
        $this->assertResponseEquals('override:cell/Gadget/overridden');
    }

    public function testAppCellDoesNotReachPluginTemplate(): void
    {
        $this->expectException(MissingCellTemplateException::class);
        $this->expectExceptionMessage('pluginonly');
        $this->get('/widgets/showcell?name=Lamp::pluginonly');
    }

    public function testAppTemplateDoesNotReachPluginCellUnprefixed(): void
    {
        $this->expectException(MissingCellException::class);
        $this->expectExceptionMessage('Gadget');
        $this->get('/widgets/showcell?name=Gadget::own');
    }

    public function testPluginTemplateDoesNotReachOwnPluginCellUnprefixed(): void
    {
        $this->expectException(MissingCellException::class);
        $this->expectExceptionMessage('Gadget');
        $this->get('/probe/things/showcell?name=Gadget::own');
    }

    public function testPluginTemplateRendersAppCellUnprefixed(): void
    {
        $this->get('/probe/things/showcell?name=Lamp::own');
        $this->assertResponseEquals('app:cell/Lamp/own');
    }

    public function testPluginTemplateRendersOtherPluginCell(): void
    {
        $this->get('/probe/things/showcell?name=Other.Sprocket::own');
        $this->assertResponseEquals('other:cell/Sprocket/own');
    }

    public function testOtherPluginCellDoesNotFallBackToCallingPlugin(): void
    {
        $this->expectException(MissingCellTemplateException::class);
        $this->expectExceptionMessage('inprobe');
        $this->get('/probe/things/showcell?name=Other.Sprocket::inprobe');
    }

    public function testElementInPluginCellTemplateResolvesInCellPlugin(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementinplugin');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInFallbackCellTemplateResolvesInCellPlugin(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementinapp');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInCellCalledFromOtherPluginTemplateResolvesInCellPlugin(): void
    {
        $this->get('/widgets/othershowcell?name=Probe.Gadget::elementinplugin');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInAppCellCalledFromPluginResolvesInApp(): void
    {
        $this->get('/probe/things/showcell?name=Lamp::elementinapp');
        $this->assertResponseEquals('app:element/shared');
    }

    public function testElementInNamedCellTemplateResolvesInCellPlugin(): void
    {
        $this->get('/widgets/cellnamedtemplate');
        $this->assertResponseEquals('plugin:element/shared');
    }

    public function testElementInPluginCellFallsBackToAppElement(): void
    {
        $this->get('/widgets/showcell?name=Probe.Gadget::elementappfallback');
        $this->assertResponseEquals('app:element/app_only');
    }

    public function testControllerHelperReachesItsTemplate(): void
    {
        $this->get('/widgets/helpertemplate');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesCellTemplate(): void
    {
        $this->get('/widgets/helpercell?name=Lamp::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesElementInCellTemplate(): void
    {
        $this->get('/widgets/helpercell?name=Lamp::helperelement');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testCellTemplateHasNoHelperItsCallerLacks(): void
    {
        $this->expectException(MissingHelperException::class);
        $this->expectExceptionMessage('AliasedHelper');
        $this->get('/widgets/showcell?name=Lamp::helper');
    }
}
