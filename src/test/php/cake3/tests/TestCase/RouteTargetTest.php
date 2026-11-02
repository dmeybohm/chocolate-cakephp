<?php

namespace App\Test\TestCase;

use Acme\Vendored\Controller\RoutedController as VendoredRoutedController;
use App\Controller\Admin\CasedController as AdminCasedController;
use App\Controller\Admin\Reports\RoutedController as AdminReportsRoutedController;
use App\Controller\Admin\RoutedController as AdminRoutedController;
use App\Controller\RoutedController;
use BadMethodCallException;
use Cake\Controller\Exception\MissingActionException;
use Cake\Routing\Exception\MissingControllerException;
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

    public function setUp()
    {
        parent::setUp();
        $this->disableErrorHandlerMiddleware();
        $this->useHttpServer(true);
    }

    public function testControllerKeyIsTheClassShortName()
    {
        $this->get('/rt/plain');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testDashedRouteCamelizesTheControllerKey()
    {
        $this->get('/rt/lower-controller');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testLowercaseControllerKeyDoesNotDispatchOnAPlainRoute()
    {
        $this->expectException(MissingControllerException::class);
        $this->get('/rt/plain-route/lower-controller');
    }

    public function testActionKeyIsTheMethodName()
    {
        $this->get('/rt/camel');
        $this->assertResponseEquals(RoutedController::class . '::viewAll');
    }

    public function testDashedRouteCamelizesTheActionKey()
    {
        $this->get('/rt/underscored-action');
        $this->assertResponseEquals(RoutedController::class . '::viewAll');
    }

    public function testUnderscoredActionKeyDoesNotDispatchOnAPlainRoute()
    {
        $this->expectException(MissingActionException::class);
        $this->get('/rt/plain-route/underscored-action');
    }

    public function testActionKeyIsCaseInsensitive()
    {
        $this->get('/rt/lower-action');
        $this->assertResponseEquals(RoutedController::class . '::viewAll');
    }

    public function testPrefixKeySelectsTheSubNamespace()
    {
        $this->get('/rt/keyed-prefix');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testNestedPrefixKeyJoinsNamespacesWithASlash()
    {
        $this->get('/rt/keyed-nested-prefix');
        $this->assertResponseEquals(AdminReportsRoutedController::class . '::index');
    }

    public function testPrefixKeyIsCamelizedOnDispatch()
    {
        $this->get('/rt/lower-prefix');
        $this->assertResponseEquals(AdminCasedController::class . '::index');
        $this->get('/rt/camel-prefix');
        $this->assertResponseEquals(AdminCasedController::class . '::viewAll');
    }

    public function testPluginKeySelectsThePluginNamespace()
    {
        $this->get('/rt/keyed-plugin');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testPrefixScopeAppliesToItsRoutes()
    {
        $this->get('/rt/admin/scoped');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testPrefixScopeNameIsUnderscored()
    {
        $this->get('/rt/admin/params');
        $this->assertSame(json_encode([
            'plugin' => false,
            'prefix' => 'admin',
            'controller' => 'Routed',
            'action' => 'params',
        ]), (string)$this->_response->getBody());
    }

    public function testNestedPrefixScopesJoinWithASlash()
    {
        $this->get('/rt/admin/reports/params');
        $this->assertSame(json_encode([
            'plugin' => false,
            'prefix' => 'admin/reports',
            'controller' => 'Routed',
            'action' => 'params',
        ]), (string)$this->_response->getBody());
        $this->get('/rt/admin/reports/scoped');
        $this->assertResponseEquals(AdminReportsRoutedController::class . '::index');
    }

    public function testNullPrefixKeyEscapesThePrefixScope()
    {
        $this->get('/rt/admin/unprefixed');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testFalsePrefixKeyEscapesThePrefixScope()
    {
        $this->get('/rt/admin/unprefixed-false');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testScopeParamsSetThePrefix()
    {
        $this->get('/rt/scope-prefix/scoped');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testPluginScopeAppliesToItsRoutes()
    {
        $this->get('/rt/probe/scoped');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testScopeParamsSetThePlugin()
    {
        $this->get('/rt/scope-plugin/scoped');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testNullPluginKeyInPluginScopeTargetsTheApp()
    {
        $this->get('/rt/probe/app');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testOtherPluginKeyInPluginScopeIsRejected()
    {
        $builder = new RouteBuilder(new RouteCollection(), '/', ['plugin' => 'Probe']);
        $this->expectException(BadMethodCallException::class);
        $builder->connect('/x', ['plugin' => 'Other', 'controller' => 'Routed', 'action' => 'index']);
    }

    public function testFalsePluginKeyInPluginScopeIsRejected()
    {
        $builder = new RouteBuilder(new RouteCollection(), '/', ['plugin' => 'Probe']);
        $this->expectException(BadMethodCallException::class);
        $builder->connect('/x', ['plugin' => false, 'controller' => 'Routed', 'action' => 'index']);
    }

    public function testStringTargetNamesControllerAndAction()
    {
        $this->get('/rt/string');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testStringTargetTakesAPrefix()
    {
        $this->get('/rt/string-prefixed');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testStringTargetTakesAPlugin()
    {
        $this->get('/rt/string-plugin');
        $this->assertResponseEquals(ProbeRoutedController::class . '::index');
    }

    public function testResourcesNameIsTheController()
    {
        $this->get('/rt/routed');
        $this->assertResponseEquals(RoutedController::class . '::index');
    }

    public function testResourcesInPrefixScopeTakesThePrefix()
    {
        $this->get('/rt/admin/routed');
        $this->assertResponseEquals(AdminRoutedController::class . '::index');
    }

    public function testUrlControllerAndActionKeysBuildTheUrl()
    {
        $this->get('/rt/url?controller=Routed&action=viewAll');
        $this->assertResponseEquals('/rt/camel');
    }

    public function testUrlActionOnlyKeepsTheRequestController()
    {
        $this->get('/rt/url?action=viewAll');
        $this->assertResponseEquals('/rt/camel');
    }

    public function testUrlKeepsTheRequestPrefix()
    {
        $this->get('/rt/admin/url?controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-prefix');
    }

    public function testUrlFalsePrefixLeavesTheRequestPrefix()
    {
        $this->get('/rt/admin/url?prefix=false&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/plain');
    }

    public function testUrlKeepsTheRequestPlugin()
    {
        $this->get('/rt/probe/url?controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }

    public function testUrlFalsePluginLeavesTheRequestPlugin()
    {
        $this->get('/rt/probe/url?plugin=false&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/plain');
    }

    public function testUrlPluginKeySelectsThePlugin()
    {
        $this->get('/rt/url?plugin=Probe&controller=Routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }

    public function testUrlPrefixKeyMustMatchTheRouteSpelling()
    {
        $this->expectException(MissingRouteException::class);
        $this->get('/rt/url?prefix=Admin&controller=Routed&action=index');
    }

    public function testUrlDashedRouteInflectsControllerAndPlugin()
    {
        $this->get('/rt/url?plugin=probe&controller=routed&action=index');
        $this->assertResponseEquals('/rt/keyed-plugin');
    }

    public function testInheritedActionDispatches()
    {
        $this->get('/rt/inherited');
        $this->assertResponseEquals(RoutedController::class . '::inherited');
    }

    public function testBaseControllerMethodIsNotAnAction()
    {
        $this->expectException(MissingActionException::class);
        $this->get('/rt/base-method');
    }

    public function testVendorPluginKeySelectsTheNestedNamespace()
    {
        $this->get('/rt/keyed-vendor-plugin');
        $this->assertResponseEquals(VendoredRoutedController::class . '::index');
    }

    public function testVendorPluginScopeAppliesToItsRoutes()
    {
        $this->get('/rt/acme/vendored/scoped');
        $this->assertResponseEquals(VendoredRoutedController::class . '::index');
    }

    public function testStringTargetTakesAVendorPlugin()
    {
        $this->get('/rt/string-vendor-plugin');
        $this->assertResponseEquals(VendoredRoutedController::class . '::index');
    }

    public function testUrlNoActionKeyCopiesTheRequestActionForTheSameController()
    {
        $this->get('/rt/url?controller=Routed');
        $this->assertResponseEquals('/rt/url');
    }

    public function testUrlNoActionKeyCopiesTheRequestActionAcrossPlugins()
    {
        $this->get('/rt/url?plugin=Probe&controller=Routed');
        $this->assertResponseEquals('/rt/probe/url');
    }

    public function testUrlNoActionKeyDefaultsToIndexForAnotherController()
    {
        $this->get('/rt/url?controller=Other');
        $this->assertResponseEquals('/rt/other');
    }

    public function testHelpersBuildUrlsLikeRouterUrl()
    {
        $this->get('/rt/links');
        $this->assertResponseContains('link:<a href="/rt/camel">x</a>');
        $this->assertResponseRegExp('#postLink:<form[^>]* action="/rt/camel"#');
        $this->assertResponseContains('build:/rt/camel');
        $this->assertResponseRegExp('#create:<form[^>]* action="/rt/camel"#');
    }

    public function testControllerRedirectBuildsUrlsLikeRouterUrl()
    {
        $this->get('/rt/redirecting');
        $this->assertHeader('Location', 'http://localhost/rt/camel');
    }
}
