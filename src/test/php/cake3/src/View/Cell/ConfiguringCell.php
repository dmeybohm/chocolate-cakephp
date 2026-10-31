<?php

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * A cell that configures helpers of its own.
 */
class ConfiguringCell extends Cell
{
    public $helpers = ['ByCellProperty' => ['className' => 'Marker']];

    public function initialize()
    {
        parent::initialize();
        $this->viewBuilder()->setHelpers(['ByCellInitialize' => ['className' => 'Marker']]);
        $this->viewBuilder()->setHelpers(['Aliased' => ['className' => 'ViewMarker']]);
    }

    public function own()
    {
    }

    public function caller()
    {
    }

    public function action()
    {
        $this->viewBuilder()->setHelpers(['Aliased' => ['className' => 'ViewMarker']]);
    }

    public function property()
    {
    }
}
