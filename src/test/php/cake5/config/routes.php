<?php
declare(strict_types=1);

use Cake\Routing\Route\DashedRoute;
use Cake\Routing\RouteBuilder;

return static function (RouteBuilder $routes): void {
    $routes->setRouteClass(DashedRoute::class);
    $routes->prefix('Admin', static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
    $routes->plugin('Probe', ['path' => '/probe'], static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
    $routes->scope('/', static function (RouteBuilder $builder): void {
        $builder->fallbacks();
    });
};
