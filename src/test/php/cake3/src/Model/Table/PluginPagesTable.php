<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class PluginPagesTable extends Table
{
    public function initialize(array $config)
    {
        $this->addBehaviors(['Probe.Sluggable', 'Audit' => ['implementedMethods' => []]]);
    }
}
