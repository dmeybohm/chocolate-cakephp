<?php
declare(strict_types=1);

namespace Probe\Controller;

use App\Controller\AppController;

/**
 * A controller inside the Probe plugin naming layouts.
 */
class FramesController extends AppController
{
    public function shared(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('shared_layout');
        $this->render('body');
    }

    public function appfallback(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('app_only_layout');
        $this->render('body');
    }

    public function overridden(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('overridden_layout');
        $this->render('body');
    }
}
