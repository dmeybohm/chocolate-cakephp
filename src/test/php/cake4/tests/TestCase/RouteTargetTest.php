<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use App\Controller\Admin\CasedController as AdminCasedController;
use App\Controller\Admin\Reports\RoutedController as AdminReportsRoutedController;
use App\Controller\Admin\RoutedController as AdminRoutedController;
use App\Controller\RoutedController;
use BadMethodCallException;
use Cake\Controller\Exception\MissingActionException;
use Cake\Http\Exception\MissingControllerException;
use Cake\Routing\Exception\MissingRouteException;
use Cake\Routing\RouteBuilder;
use Cake\Routing\RouteCollection;
use Cake\TestSuite\IntegrationTestTrait;
use Cake\TestSuite\TestCase;
use Probe\Controller\RoutedController as ProbeRoutedController;

/**
 * How a route target array names a controller class and an action, in the route builder and
 * in URL building, which route target completion and navigation depend on.
 */
class RouteTargetTest extends TestCase
{
    use IntegrationTestTrait;

    public function setUp(): void
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
    }

    public function testControllerKeyIsTheClassShortName(): void
    {
        $this->get('/rt/plain');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testDashedRouteCamelizesTheControllerKey(): void
    {
        $this->get('/rt/lower-controller');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testLowercaseControllerKeyDoesNotDispatchOnAPlainRoute(): void
    {
        $this->expectException(MissingControllerException::class);
        $this->get('/rt/plain-route/lower-controller');
    }

    public function testActionKeyIsTheMethodName(): void
    {
        $this->get('/rt/camel');
        $this->assertResponseEquals(RoutedController::class . '::viewAll');
    }

    public function testDashedRouteCamelizesTheActionKey(): void
    {
        $this->get('/rt/underscored-action');
        $this->assertResponseEquals(RoutedController::class . '::viewAll');
    }

    public function testUnderscoredActionKeyDoesNotDispatchOnAPlainRoute(): void
    {
        $this->expectException(MissingActionException::class);
        $this->get('/rt/plain-route/underscored-action');
    }

    public function testActionKeyIsCaseSensitive(): void
    {
        $this->expectException(MissingActionException::class);
        $this->get('/rt/lower-action');
    }

    public function testPrefixKeySelectsTheSubNamespace(): void
    {
        $this->get('/rt/keyed-prefix');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testNestedPrefixKeyJoinsNamespacesWithASlash(): void
    {
        $this->get('/rt/keyed-nested-prefix');
        $this->assertResponseEquals(AdminReportsRoutedController::class . '::index');
    }

    public function testLowercasePrefixKeyDispatchesWithADeprecation(): void
    {
        $this->deprecated(function (): void {
            $this->get('/rt/lower-prefix');
            $this->assertResponseEquals(AdminCasedController::class . '::index');
        });
    }

    public function testPluginKeySelectsThePluginNamespace(): void
    {
        $this->get('/rt/keyed-plugin');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testPrefixScopeAppliesToItsRoutes(): void
    {
        $this->get('/rt/admin/scoped');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testPrefixScopeNameIsKeptAsWritten(): void
    {
        $this->get('/rt/admin/params');
        $this->assertSame(json_encode([
            'plugin' => null,
            'prefix' => 'Admin',
            'controller' => 'Routed',
            'action' => 'params',
        ]), (string)$this->_response->getBody());
    }

    public function testNestedPrefixScopesJoinWithASlash(): void
    {
        $this->get('/rt/admin/reports/params');
        $this->assertSame(json_encode([
            'plugin' => null,
            'prefix' => 'Admin/Reports',
            'controller' => 'Routed',
            'action' => 'params',
        ]), (string)$this->_response->getBody());
        $this->get('/rt/admin/reports/scoped');
        $this->assertResponseEquals(AdminReportsRoutedController::class . '::index');
    }

    public function testNullPrefixKeyEscapesThePrefixScope(): void
    {
        $this->get('/rt/admin/unprefixed');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testFalsePrefixKeyEscapesThePrefixScope(): void
    {
        $this->get('/rt/admin/unprefixed-false');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testScopeParamsSetThePrefix(): void
    {
        $this->get('/rt/scope-prefix/scoped');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testPluginScopeAppliesToItsRoutes(): void
    {
        $this->get('/rt/probe/scoped');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testScopeParamsSetThePlugin(): void
    {
        $this->get('/rt/scope-plugin/scoped');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testNullPluginKeyInPluginScopeTargetsTheApp(): void
    {
        $this->get('/rt/probe/app');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testOtherPluginKeyInPluginScopeIsRejected(): void
    {
        $builder = new RouteBuilder(new RouteCollection(), '/', ['plugin' => 'Probe']);
        $this->expectException(BadMethodCallException::class);
        $builder->connect('/x', ['plugin' => 'Other', 'controller' => 'Routed', 'action' => 'index']);
    }

    public function testFalsePluginKeyInPluginScopeIsRejected(): void
    {
        $builder = new RouteBuilder(new RouteCollection(), '/', ['plugin' => 'Probe']);
        $this->expectException(BadMethodCallException::class);
        $builder->connect('/x', ['plugin' => false, 'controller' => 'Routed', 'action' => 'index']);
    }

    public function testStringTargetNamesControllerAndAction(): void
    {
        $this->get('/rt/string');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testStringTargetTakesAPrefix(): void
    {
        $this->get('/rt/string-prefixed');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testStringTargetTakesAPlugin(): void
    {
        $this->get('/rt/string-plugin');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testResourcesNameIsTheController(): void
    {
        $this->get('/rt/routed');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testResourcesInPrefixScopeTakesThePrefix(): void
    {
        $this->get('/rt/admin/routed');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testUrlControllerAndActionKeysBuildTheUrl(): void
    {
        $this->get('/rt/url?controller=Routed&action=viewAll');
        $this->assertResponseEquals('/rt/camel');
    }

    public function testUrlActionOnlyKeepsTheRequestController(): void
    {
        $this->get('/rt/url?action=viewAll');
        $this->assertResponseEquals('/rt/camel');
    }

    public function testUrlKeepsTheRequestPrefix(): void
    {
        $this->get('/rt/admin/url?controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-prefix');
    }

    public function testUrlFalsePrefixLeavesTheRequestPrefix(): void
    {
        $this->get('/rt/admin/url?prefix=false&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/plain');
    }

    public function testUrlKeepsTheRequestPlugin(): void
    {
        $this->get('/rt/probe/url?controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }

    public function testUrlFalsePluginLeavesTheRequestPlugin(): void
    {
        $this->get('/rt/probe/url?plugin=false&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/plain');
    }

    public function testUrlPluginKeySelectsThePlugin(): void
    {
        $this->get('/rt/url?plugin=Probe&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }

    public function testUrlPrefixKeyMustMatchTheRouteSpelling(): void
    {
        $this->expectException(MissingRouteException::class);
        $this->get('/rt/url?prefix=admin&controller=Routed&action=index');
    }

    public function testUrlDashedRouteInflectsControllerAndPlugin(): void
    {
        $this->get('/rt/url?plugin=probe&controller=routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }
}
