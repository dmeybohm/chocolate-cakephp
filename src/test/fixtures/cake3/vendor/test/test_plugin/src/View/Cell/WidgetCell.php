<?php

namespace TestPlugin\View\Cell;

use Cake\View\Cell;

class WidgetCell extends Cell
{
    public function display()
    {
        $this->set('title', 'Widget');
    }
}
