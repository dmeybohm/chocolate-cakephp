<?php
namespace App\View\Cell;

use Cake\View\Cell;

/**
 * A cell naming a layout.
 */
class FramedCell extends Cell
{
    public function fromclass()
    {
        $this->viewBuilder()->setLayout('probe');
    }

    public function fromtemplate()
    {
    }
}
