<?php
declare(strict_types=1);

namespace App\Controller;

/**
 * An application controller declaring a layout property.
 */
class DeclaredController extends AppController
{
    public $layout = 'probe';

    public function index(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }
}
