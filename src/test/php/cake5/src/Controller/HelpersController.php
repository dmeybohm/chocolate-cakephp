<?php
declare(strict_types=1);

namespace App\Controller;

/**
 * Configures helpers from the controller, to show what reaches which view.
 */
class HelpersController extends AppController
{
    public function viewaddhelper(): void
    {
        $this->viewBuilder()->setClassName('Configured')->addHelper('ByAdd', ['className' => 'Marker']);
    }

    public function viewloadhelper(): void
    {
        $this->viewBuilder()->setClassName('Configured')->addHelper('ByLoad', ['className' => 'Marker']);
    }

    public function viewloadwithoutoptions(): void
    {
        $this->viewBuilder()->setClassName('Configured')->addHelper('ByLoad');
    }

    public function viewproperty(): void
    {
        $this->viewBuilder()->setClassName('Configured');
    }

    public function inlayout(): void
    {
        $this->viewBuilder()->enableAutoLayout(true)->setLayout('helper')->addHelper('Aliased', ['className' => 'Marker']);
    }
}
