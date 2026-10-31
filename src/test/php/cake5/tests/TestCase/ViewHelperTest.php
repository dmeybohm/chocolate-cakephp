<?php
declare(strict_types=1);

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

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testViewAddHelperWinsOverControllerHelper(): void
    {
        $this->get('/helpers/viewaddhelper');
        $this->assertResponseEquals('App\\View\\Helper\\ViewMarkerHelper');
    }

    public function testViewLoadHelperConflictsWithControllerClassName(): void
    {
        $this->expectException(\RuntimeException::class);
        $this->expectExceptionMessage('has already been loaded');
        $this->get('/helpers/viewloadhelper');
    }

    public function testViewLoadHelperWinsOverControllerHelperWithoutOptions(): void
    {
        $this->get('/helpers/viewloadwithoutoptions');
        $this->assertResponseEquals('App\\View\\Helper\\ViewMarkerHelper');
    }

    public function testControllerBuilderReplacesViewHelpersProperty(): void
    {
        $this->expectException(MissingHelperException::class);
        $this->expectExceptionMessage('ByPropertyHelper');
        $this->get('/helpers/viewproperty');
    }

    public function testControllerHelperReachesLayoutAndItsElement(): void
    {
        $this->get('/helpers/inlayout');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper|App\\View\\Helper\\MarkerHelper');
    }

    public function testCellOwnHelperReachesItsTemplate(): void
    {
        $this->get('/widgets/showcell?name=Lamp::ownhelper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesPluginCellTemplate(): void
    {
        $this->get('/widgets/helpercell?name=Probe.Gadget::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelperReachesElementInPluginCellTemplate(): void
    {
        $this->get('/widgets/helpercell?name=Probe.Gadget::helperelement');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testPluginControllerHelperReachesAppCellTemplate(): void
    {
        $this->get('/probe/things/helpercell?name=Lamp::helper');
        $this->assertResponseEquals('App\\View\\Helper\\MarkerHelper');
    }

    public function testControllerHelpersPropertyIsIgnored(): void
    {
        $this->expectException(MissingHelperException::class);
        $this->expectExceptionMessage('AliasedHelper');
        $this->get('/legacy-helpers/index');
    }
}
