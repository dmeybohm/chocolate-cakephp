<?php
declare(strict_types=1);

namespace Probe\View\Cell;

use Cake\View\Cell;

/**
 * A cell inside the Probe plugin.
 */
class GadgetCell extends Cell
{
    public function own(): void
    {
    }

    public function appfallback(): void
    {
    }

    public function overridden(): void
    {
    }

    public function loadTable($alias)
    {
        return $this->fetchTable($alias);
    }

    public function elementinplugin(): void
    {
    }

    public function elementinapp(): void
    {
    }

    public function elementappfallback(): void
    {
    }
}
