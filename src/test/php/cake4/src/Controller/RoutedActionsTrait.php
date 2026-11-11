<?php
declare(strict_types=1);

namespace App\Controller;

use Cake\Http\Response;
use Cake\Routing\Router;

/**
 * Actions that report the class and method a route target dispatched to, the request params a
 * route scope produced, and the URL an array target builds in the current request.
 */
trait RoutedActionsTrait
{
    public function index(): Response
    {
        return $this->response->withStringBody(static::class . '::' . __FUNCTION__);
    }

    public function viewAll(): Response
    {
        return $this->response->withStringBody(static::class . '::' . __FUNCTION__);
    }

    public function links(): void
    {
    }

    public function cellurl(): void
    {
    }

    public function redirecting()
    {
        return $this->redirect(['action' => 'viewAll']);
    }

    public function params(): Response
    {
        $params = [];
        foreach (['plugin', 'prefix', 'controller', 'action'] as $key) {
            $params[$key] = $this->getRequest()->getParam($key);
        }

        return $this->response->withStringBody(json_encode($params));
    }

    /**
     * Builds a URL from the query string keys `plugin`, `prefix`, `controller` and `action`.
     * A value of `false` is passed as the boolean.
     */
    public function url(): Response
    {
        $target = [];
        foreach (['plugin', 'prefix', 'controller', 'action'] as $key) {
            $value = $this->getRequest()->getQuery($key);
            if ($value === null) {
                continue;
            }
            $target[$key] = $value === 'false' ? false : $value;
        }

        return $this->response->withStringBody(Router::url($target));
    }
}
