<?php

namespace App\View;

use Cake\View\View;

class ConfiguredView extends View
{
    public function initialize(): void
    {
        $this->loadHelper('Html');
        $this->loadHelper('Fmt', ['className' => 'MovieFormatter']);
        $this->loadHelper('TestPlugin.Shared');
    }
}
