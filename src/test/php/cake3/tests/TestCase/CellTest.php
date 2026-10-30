<?php

namespace App\Test\TestCase;

use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Cake\View\Exception\MissingCellException;
use Cake\View\Exception\MissingCellViewException;

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
}
