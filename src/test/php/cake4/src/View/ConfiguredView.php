<?php
declare(strict_types=1);

namespace App\View;

use Cake\View\View;

/**
 * A view class that configures helpers the way an application's AppView does.
 */
class ConfiguredView extends View
{
    protected $helpers = ['ByProperty' => ['className' => 'ViewMarker']];

    public function initialize(): void
    {
        parent::initialize();
        $this->addHelper('ByAdd', ['className' => 'ViewMarker']);
        $this->loadHelper('ByLoad', ['className' => 'ViewMarker']);
    }
}
