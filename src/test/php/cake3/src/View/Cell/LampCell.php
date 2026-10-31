<?php

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * An application cell, outside any plugin.
 */
class LampCell extends Cell
{
    public function own()
    {
    }

    public function pluginonly()
    {
    }

    public function elementinapp()
    {
    }

    public function helper()
    {
    }

    public function helperelement()
    {
    }

    public function ownhelper()
    {
        $this->viewBuilder()->setHelpers(['OwnAliased' => ['className' => 'Marker']]);
    }
}
