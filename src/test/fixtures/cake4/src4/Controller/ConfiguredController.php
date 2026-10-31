<?php

namespace App\Controller;

use App\Controller\Component\MovieMetadataComponent;

class ConfiguredController extends AppController
{
    public function initialize(): void
    {
        parent::initialize();

        $this->loadComponent('MovieMetadata');
        $this->loadComponent('Meta', ['className' => MovieMetadataComponent::class]);
        $this->loadComponent('Legacy', ['className' => 'MovieMetadata']);
        $this->loadComponent('TestPlugin.InsidePlugin');
        $this->loadComponent('Flash');
    }

    public function beforeFilter()
    {
        $this->loadComponent('TestPlugin.Conditional');
    }

    public function reload()
    {
        $this->loadComponent('Meta', ['className' => 'Conditional']);
    }
}
