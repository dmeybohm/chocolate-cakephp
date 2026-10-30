<?php

namespace App;

use Cake\Http\BaseApplication;
use Cake\Http\MiddlewareQueue;
use Cake\Routing\Middleware\RoutingMiddleware;

/**
 * The application the assumption tests dispatch requests through.
 */
class Application extends BaseApplication
{
    public function middleware($middlewareQueue)
    {
        return $middlewareQueue->add(new RoutingMiddleware($this));
    }
}
