<?php

namespace App\View\Cell;

use Cake\View\Cell;

class InventoryCell extends Cell
{
    public function display(): void
    {
        $items = ['apple', 'pear'];
        $this->set(compact('items'));
    }

    public function compact(int $limit = 5): void
    {
        $this->set('limit', $limit);
    }
}
