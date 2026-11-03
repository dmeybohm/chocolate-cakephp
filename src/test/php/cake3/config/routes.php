<?php

use Cake\Routing\Route\DashedRoute;
use Cake\Routing\Route\Route;
use Cake\Routing\Router;

Router::defaultRouteClass(DashedRoute::class);

// Route targets under test, see RouteTargetTest. Connected first so URL building finds them.
Router::scope('/rt', function ($routes) {
    $routes->connect('/plain', ['controller' => 'Routed', 'action' => 'index']);
    $routes->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
    $routes->connect('/lower-controller', ['controller' => 'routed', 'action' => 'index']);
    $routes->connect('/underscored-action', ['controller' => 'Routed', 'action' => 'view_all']);
    $routes->connect('/lower-action', ['controller' => 'Routed', 'action' => 'viewall']);
    $routes->connect('/plain-route/lower-controller', ['controller' => 'routed', 'action' => 'index'], ['routeClass' => Route::class]);
    $routes->connect('/plain-route/underscored-action', ['controller' => 'Routed', 'action' => 'view_all'], ['routeClass' => Route::class]);
    $routes->connect('/keyed-prefix', ['prefix' => 'admin', 'controller' => 'Routed', 'action' => 'index']);
    $routes->connect('/keyed-nested-prefix', ['prefix' => 'admin/reports', 'controller' => 'Routed', 'action' => 'index']);
    $routes->connect('/lower-prefix', ['prefix' => 'admin', 'controller' => 'Cased', 'action' => 'index']);
    $routes->connect('/camel-prefix', ['prefix' => 'Admin', 'controller' => 'Cased', 'action' => 'viewAll']);
    $routes->connect('/keyed-plugin', ['plugin' => 'Probe', 'controller' => 'Routed', 'action' => 'index']);
    $routes->connect('/keyed-vendor-plugin', ['plugin' => 'Acme/Vendored', 'controller' => 'Routed', 'action' => 'index']);
    $routes->connect('/string', 'Routed::index');
    $routes->connect('/string-prefixed', 'Admin/Routed::index');
    $routes->connect('/string-plugin', 'Probe.Routed::index');
    $routes->connect('/string-vendor-plugin', 'Acme/Vendored.Routed::index');
    $routes->connect('/inherited', ['controller' => 'Routed', 'action' => 'inherited']);
    $routes->connect('/base-method', ['controller' => 'Routed', 'action' => 'redirect']);
    $routes->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
    $routes->connect('/redirecting', ['controller' => 'Routed', 'action' => 'redirecting']);
    $routes->connect('/other', ['controller' => 'Other', 'action' => 'index']);
    $routes->connect('/other-view', ['controller' => 'Other', 'action' => 'viewAll']);
    $routes->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
    $routes->prefix('Admin', function ($routes) {
        $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
        $routes->connect('/unprefixed', ['prefix' => null, 'controller' => 'Routed', 'action' => 'index']);
        $routes->connect('/unprefixed-false', ['prefix' => false, 'controller' => 'Routed', 'action' => 'index']);
        $routes->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
        $routes->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
        $routes->connect('/params', ['controller' => 'Routed', 'action' => 'params']);
        $routes->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
        $routes->prefix('Reports', function ($routes) {
            $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
            $routes->connect('/params', ['controller' => 'Routed', 'action' => 'params']);
        });
        $routes->resources('Routed');
    });
    $routes->scope('/scope-prefix', ['prefix' => 'admin'], function ($routes) {
        $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
    });
    $routes->plugin('Probe', function ($routes) {
        $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
        $routes->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
        $routes->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
        $routes->connect('/cellurl', ['controller' => 'Routed', 'action' => 'cellurl']);
        $routes->connect('/app', ['plugin' => null, 'controller' => 'Routed', 'action' => 'index']);
        $routes->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
    });
    $routes->scope('/scope-plugin', ['plugin' => 'Probe'], function ($routes) {
        $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
    });
    $routes->plugin('Acme/Vendored', function ($routes) {
        $routes->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
    });
    $routes->resources('Routed');
});

Router::prefix('admin', function ($routes) {
    $routes->fallbacks();
});
Router::plugin('Probe', ['path' => '/probe'], function ($routes) {
    $routes->fallbacks();
});
Router::plugin('Pathed', ['path' => '/pathed'], function ($routes) {
    $routes->fallbacks();
});
Router::scope('/', function ($routes) {
    $routes->fallbacks();
});
