<?php
namespace Probe\Controller;

use App\Controller\AppController;

/**
 * A controller inside the Probe plugin naming layouts.
 */
class FramesController extends AppController
{
    public function shared()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('shared_layout');
        $this->render('body');
    }

    public function appfallback()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('app_only_layout');
        $this->render('body');
    }

    public function overridden()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('overridden_layout');
        $this->render('body');
    }
}
