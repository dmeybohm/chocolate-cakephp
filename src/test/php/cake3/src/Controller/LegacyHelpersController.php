<?php

namespace App\Controller;

/**
 * Declares helpers in the property CakePHP 3 reads.
 */
class LegacyHelpersController extends LegacyBaseController
{
    public $helpers = ['Aliased' => ['className' => 'Marker']];

    public function index()
    {
    }
}
