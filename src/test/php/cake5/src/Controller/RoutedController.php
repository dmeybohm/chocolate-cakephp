<?php
declare(strict_types=1);

namespace App\Controller;

/**
 * An application controller the route target tests dispatch to.
 */
class RoutedController extends AppController
{
    use RoutedActionsTrait;
}
