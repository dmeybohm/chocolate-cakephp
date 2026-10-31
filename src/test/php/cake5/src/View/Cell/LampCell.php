<?php
declare(strict_types=1);

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * An application cell, outside any plugin.
 */
class LampCell extends Cell
{
    public function own(): void
    {
    }

    public function pluginonly(): void
    {
    }

    public function elementinapp(): void
    {
    }

    public function helper(): void
    {
    }

    public function helperelement(): void
    {
    }

    public function ownhelper(): void
    {
        $this->viewBuilder()->addHelper('OwnAliased', ['className' => 'Marker']);
    }
}
