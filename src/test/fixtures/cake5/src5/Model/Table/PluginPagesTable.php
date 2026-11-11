<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class PluginPagesTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->addBehaviors(['TestPlugin.Sluggable', 'Audit' => ['implementedMethods' => []]]);
    }
}
