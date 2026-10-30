<?php

namespace App\Controller;

/**
 * An application controller, outside any plugin.
 */
class WidgetsController extends AppController
{
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
}
