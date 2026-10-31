<?php
declare(strict_types=1);

namespace App\Controller;

/**
 * An application controller, outside any plugin.
 */
class WidgetsController extends AppController
{
    public function unprefixed(): void
    {
        $this->render('only_in_plugin');
    }

    public function prefixed(): void
    {
        $this->render('Probe.only_in_plugin');
    }

    public function pluginelement(): void
    {
    }

    public function prefixedelement(): void
    {
    }

    public function showcell(): void
    {
        $this->set('name', $this->getRequest()->getQuery('name'));
    }

    public function prefixedfallback(): void
    {
        $this->render('Probe.app_only');
    }

    public function prefixedoverride(): void
    {
        $this->render('Probe.overridden_widget');
    }

    public function prefixedappelement(): void
    {
    }

    public function prefixedinner(): void
    {
        $this->render('Probe.inner');
    }
}
