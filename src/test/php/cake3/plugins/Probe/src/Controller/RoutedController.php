<?php

namespace Probe\Controller;

use App\Controller\AppController;
use App\Controller\RoutedActionsTrait;

/**
 * A controller inside the Probe plugin the route target tests dispatch to.
 */
class RoutedController extends AppController
{
    use RoutedActionsTrait;
}
