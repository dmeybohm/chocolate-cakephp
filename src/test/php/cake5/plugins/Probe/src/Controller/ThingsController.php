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

    public function showcell(): void
    {
        $this->set('name', $this->getRequest()->getQuery('name'));
    }

    public function crossplugin(): void
    {
        $this->render('Other.in_other');
    }

    public function crossfallback(): void
    {
        $this->render('Other.in_app');
    }

    public function crossnotcaller(): void
    {
        $this->render('Other.in_probe');
    }

    public function crossoverride(): void
    {
        $this->render('Other.overridden_other');
    }

    public function crossinner(): void
    {
        $this->render('Other.inner');
    }

    public function crosselement(): void
    {
    }

    public function crosselementfallback(): void
    {
    }

    public function crosselementnotcaller(): void
    {
    }

    public function fallbackinner(): void
    {
    }
}
