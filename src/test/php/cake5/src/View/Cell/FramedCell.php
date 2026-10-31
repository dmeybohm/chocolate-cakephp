<?php
declare(strict_types=1);

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * A cell naming a layout.
 */
class FramedCell extends Cell
{
    public function fromclass(): void
    {
        $this->viewBuilder()->setLayout('probe');
    }

    public function fromtemplate(): void
    {
    }
}
