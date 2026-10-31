<?php

namespace App\Controller;

/**
 * Configures helpers from the controller, to show what reaches which view.
 */
class HelpersController extends AppController
{
    public function viewloadhelper()
    {
        $this->viewBuilder()->setClassName('Configured')->setHelpers(['ByLoad' => ['className' => 'Marker']]);
    }

    public function viewloadwithoutoptions()
    {
        $this->viewBuilder()->setClassName('Configured')->setHelpers(['ByLoad']);
    }

    public function viewproperty()
    {
        $this->viewBuilder()->setClassName('Configured');
    }

    public function inlayout()
    {
        $this->viewBuilder()->enableAutoLayout(true)->setLayout('helper')->setHelpers(['Aliased' => ['className' => 'Marker']]);
    }
}
