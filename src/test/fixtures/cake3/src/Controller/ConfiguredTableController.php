<?php

namespace App\Controller;

class ConfiguredTableController extends AppController
{
    public function initialize(): void
    {
        parent::initialize();

        $this->loadModel('Films', ['className' => 'Movies']);
        $this->loadModel('TestPlugin.Catalog');
    }
}
