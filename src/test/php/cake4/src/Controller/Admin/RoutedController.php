<?php
declare(strict_types=1);

namespace App\Controller\Admin;

use App\Controller\AppController;
use App\Controller\RoutedActionsTrait;

/**
 * An application controller under the Admin routing prefix the route target tests dispatch to.
 */
class RoutedController extends AppController
{
    use RoutedActionsTrait;
}
