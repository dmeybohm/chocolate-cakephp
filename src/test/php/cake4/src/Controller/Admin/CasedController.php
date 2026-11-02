<?php
declare(strict_types=1);

namespace App\Controller\Admin;

use App\Controller\AppController;
use App\Controller\RoutedActionsTrait;

/**
 * A controller under the Admin prefix that only the prefix spelling tests dispatch to, so the
 * class is never autoloaded before them and the lookup is by the spelling under test.
 */
class CasedController extends AppController
{
    use RoutedActionsTrait;
}
