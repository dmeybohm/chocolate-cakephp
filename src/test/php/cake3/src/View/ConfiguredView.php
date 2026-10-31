<?php

namespace App\View;

use Cake\View\View;

/**
 * A view class that configures helpers the way an application's AppView does.
 */
class ConfiguredView extends View
{
    protected $helpers = ['ByProperty' => ['className' => 'ViewMarker']];

    public function initialize()
    {
        parent::initialize();
        $this->loadHelper('ByLoad', ['className' => 'ViewMarker']);
    }
}
