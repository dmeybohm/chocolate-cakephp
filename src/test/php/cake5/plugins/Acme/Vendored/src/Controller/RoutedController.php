<?php
declare(strict_types=1);

namespace Acme\Vendored\Controller;

use App\Controller\AppController;
use App\Controller\RoutedActionsTrait;

/**
 * A controller inside the vendor-namespaced Acme/Vendored plugin.
 */
class RoutedController extends AppController
{
    use RoutedActionsTrait;
}
