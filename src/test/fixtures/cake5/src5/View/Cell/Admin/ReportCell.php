<?php

namespace App\View\Cell\Admin;

use Cake\View\Cell;

class ReportCell extends Cell
{
    public function display(): void
    {
        $this->set('total', 10);
    }
}
