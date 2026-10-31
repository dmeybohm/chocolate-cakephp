<?php

namespace App\Controller;

/**
 * An application controller, outside any plugin.
 */
class WidgetsController extends AppController
{
    public function templatelayout()
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function unprefixed()
    {
        $this->render('only_in_plugin');
    }

    public function prefixed()
    {
        $this->render('Probe.only_in_plugin');
    }

    public function pluginelement()
    {
    }

    public function prefixedelement()
    {
    }

    public function showcell()
    {
        $this->set('name', $this->getRequest()->getQuery('name'));
    }

    public function prefixedfallback()
    {
        $this->render('Probe.app_only');
    }

    public function prefixedoverride()
    {
        $this->render('Probe.overridden_widget');
    }

    public function prefixedappelement()
    {
    }

    public function prefixedinner()
    {
        $this->render('Probe.inner');
    }

    public function othershowcell()
    {
        $this->set('name', $this->getRequest()->getQuery('name'));
        $this->render('Other.showcell');
    }

    public function cellnamedtemplate()
    {
    }

    public function helpertemplate()
    {
        $this->viewBuilder()->setHelpers(['Aliased' => ['className' => 'Marker']]);
    }

    public function helpercell()
    {
        $this->viewBuilder()->setHelpers(['Aliased' => ['className' => 'Marker']]);
        $this->set('name', $this->getRequest()->getQuery('name'));
    }
}
