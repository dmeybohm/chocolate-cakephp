<?php
declare(strict_types=1);

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * A cell that configures helpers of its own.
 */
class ConfiguringCell extends Cell
{
    public $helpers = ['ByCellProperty' => ['className' => 'Marker']];

    public function initialize(): void
    {
        parent::initialize();
        $this->viewBuilder()->addHelper('ByCellInitialize', ['className' => 'Marker']);
        $this->viewBuilder()->addHelper('Aliased', ['className' => 'ViewMarker']);
    }

    public function own(): void
    {
    }

    public function caller(): void
    {
    }

    public function action(): void
    {
        $this->viewBuilder()->addHelper('Aliased', ['className' => 'ViewMarker']);
    }

    public function property(): void
    {
    }
}
