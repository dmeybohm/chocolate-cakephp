<?php
declare(strict_types=1);

use Cake\Routing\Route\DashedRoute;
use Cake\Routing\Route\Route;
use Cake\Routing\RouteBuilder;

return static function (RouteBuilder $routes): void {
    $routes->setRouteClass(DashedRoute::class);

    // Route targets under test, see RouteTargetTest. Connected first so URL building finds them.
    $routes->scope('/rt', static function (RouteBuilder $builder): void {
        $builder->connect('/plain', ['controller' => 'Routed', 'action' => 'index']);
        $builder->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
        $builder->connect('/lower-controller', ['controller' => 'routed', 'action' => 'index']);
        $builder->connect('/underscored-action', ['controller' => 'Routed', 'action' => 'view_all']);
        $builder->connect('/lower-action', ['controller' => 'Routed', 'action' => 'viewall']);
        $builder->connect('/plain-route/lower-controller', ['controller' => 'routed', 'action' => 'index'], ['routeClass' => Route::class]);
        $builder->connect('/plain-route/underscored-action', ['controller' => 'Routed', 'action' => 'view_all'], ['routeClass' => Route::class]);
        $builder->connect('/keyed-prefix', ['prefix' => 'Admin', 'controller' => 'Routed', 'action' => 'index']);
        $builder->connect('/keyed-nested-prefix', ['prefix' => 'Admin/Reports', 'controller' => 'Routed', 'action' => 'index']);
        $builder->connect('/lower-prefix', ['prefix' => 'admin', 'controller' => 'Cased', 'action' => 'index']);
        $builder->connect('/keyed-plugin', ['plugin' => 'Probe', 'controller' => 'Routed', 'action' => 'index']);
        $builder->connect('/keyed-vendor-plugin', ['plugin' => 'Acme/Vendored', 'controller' => 'Routed', 'action' => 'index']);
        $builder->connect('/string', 'Routed::index');
        $builder->connect('/string-prefixed', 'Admin/Routed::index');
        $builder->connect('/string-plugin', 'Probe.Routed::index');
        $builder->connect('/string-vendor-plugin', 'Acme/Vendored.Routed::index');
        $builder->connect('/inherited', ['controller' => 'Routed', 'action' => 'inherited']);
        $builder->connect('/base-method', ['controller' => 'Routed', 'action' => 'redirect']);
        $builder->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
        $builder->connect('/redirecting', ['controller' => 'Routed', 'action' => 'redirecting']);
        $builder->connect('/other', ['controller' => 'Other', 'action' => 'index']);
        $builder->connect('/other-view', ['controller' => 'Other', 'action' => 'viewAll']);
        $builder->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
        $builder->prefix('Admin', static function (RouteBuilder $builder): void {
            $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
            $builder->connect('/unprefixed', ['prefix' => null, 'controller' => 'Routed', 'action' => 'index']);
            $builder->connect('/unprefixed-false', ['prefix' => false, 'controller' => 'Routed', 'action' => 'index']);
            $builder->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
            $builder->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
            $builder->connect('/params', ['controller' => 'Routed', 'action' => 'params']);
            $builder->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
            $builder->prefix('Reports', static function (RouteBuilder $builder): void {
                $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
                $builder->connect('/params', ['controller' => 'Routed', 'action' => 'params']);
            });
            $builder->resources('Routed');
        });
        $builder->scope('/scope-prefix', ['prefix' => 'Admin'], static function (RouteBuilder $builder): void {
            $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
        });
        $builder->plugin('Probe', static function (RouteBuilder $builder): void {
            $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
            $builder->connect('/camel', ['controller' => 'Routed', 'action' => 'viewAll']);
            $builder->connect('/links', ['controller' => 'Routed', 'action' => 'links']);
            $builder->connect('/cellurl', ['controller' => 'Routed', 'action' => 'cellurl']);
            $builder->connect('/app', ['plugin' => null, 'controller' => 'Routed', 'action' => 'index']);
            $builder->connect('/url', ['controller' => 'Routed', 'action' => 'url']);
        });
        $builder->scope('/scope-plugin', ['plugin' => 'Probe'], static function (RouteBuilder $builder): void {
            $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
        });
        $builder->plugin('Acme/Vendored', static function (RouteBuilder $builder): void {
            $builder->connect('/scoped', ['controller' => 'Routed', 'action' => 'index']);
        });
        $builder->resources('Routed');
    });

    $routes->prefix('Admin', static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
    $routes->plugin('Probe', ['path' => '/probe'], static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
    $routes->plugin('Pathed', ['path' => '/pathed'], static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
    $routes->scope('/', static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
};
