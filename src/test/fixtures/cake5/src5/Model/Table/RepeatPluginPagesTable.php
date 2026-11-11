<?php

namespace App\Model\Table;

class RepeatPluginPagesTable extends PluginPagesTable
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->addBehavior('Sluggable');
    }
}
