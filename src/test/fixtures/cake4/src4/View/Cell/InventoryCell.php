<?php

namespace App\View\Cell;

use Cake\View\Cell;

class InventoryCell extends Cell
{
    public function display()
    {
        $items = ['apple', 'pear'];
        $this->set(compact('items'));
    }

    public function compact($limit = 5)
    {
        $this->set('limit', $limit);
    }
}
