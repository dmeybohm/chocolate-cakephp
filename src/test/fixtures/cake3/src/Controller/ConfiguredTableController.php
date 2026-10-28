<?php

namespace App\Controller;

class ConfiguredTableController extends AppController
{
    public function initialize(): void
    {
        parent::initialize();

        $this->loadModel('Movies');
        $this->loadModel('TestPlugin.Catalog');
    }
}
