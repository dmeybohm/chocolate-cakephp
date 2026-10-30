<?php

namespace App\Controller;

use Cake\Controller\Controller;

/**
 * Renders templates without a layout, so a response body is exactly one template.
 */
class AppController extends Controller
{
    public function initialize()
    {
        parent::initialize();
        $this->viewBuilder()->enableAutoLayout(false);
    }
}
