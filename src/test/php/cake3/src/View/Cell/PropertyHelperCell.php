<?php

namespace App\View\Cell;

use Cake\View\Cell;

/**
 * A cell declaring a helper in its property, for an alias its caller also configures.
 */
class PropertyHelperCell extends Cell
{
    public $helpers = ['Aliased' => ['className' => 'ViewMarker']];

    public function display()
    {
    }
}
