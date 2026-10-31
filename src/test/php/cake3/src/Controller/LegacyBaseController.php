<?php

namespace App\Controller;

/**
 * Declares helpers in the property CakePHP 3 reads, for a subclass to merge with.
 */
class LegacyBaseController extends AppController
{
    public $helpers = ['Inherited' => ['className' => 'Marker']];
}
