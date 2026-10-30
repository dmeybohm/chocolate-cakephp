<?php
declare(strict_types=1);

namespace Probe\Controller;

use App\Controller\AppController;

/**
 * A controller inside the Probe plugin.
 */
class ThingsController extends AppController
{
    public function index(): void
    {
    }

    public function other(): void
    {
        $this->render('other');
    }

    public function overridden(): void
    {
    }

    public function sharedelement(): void
    {
    }

    public function appelement(): void
    {
    }

    public function appfallback(): void
    {
    }
}
