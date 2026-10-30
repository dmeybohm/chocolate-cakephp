<?php

use Cake\Routing\Route\DashedRoute;
use Cake\Routing\Router;

Router::defaultRouteClass(DashedRoute::class);
Router::prefix('admin', function ($routes) {
    $routes->fallbacks();
});
Router::plugin('Probe', ['path' => '/probe'], function ($routes) {
    $routes->fallbacks();
});
Router::scope('/', function ($routes) {
    $routes->fallbacks();
});
