<?php

namespace App\Controller;

use Cake\Http\Response;

/**
 * The app's own base class of the routed controllers, whose public methods are actions too.
 */
class RoutedBaseController extends AppController
{
    public function inherited()
    {
        return $this->response->withStringBody(static::class . '::' . __FUNCTION__);
    }
}
