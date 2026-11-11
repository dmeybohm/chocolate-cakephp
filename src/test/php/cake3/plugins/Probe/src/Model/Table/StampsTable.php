<?php

namespace Probe\Model\Table;

use Cake\ORM\Table;

/**
 * A plugin table attaching behaviors by bare name.
 */
class StampsTable extends Table
{
    public function initialize(array $config)
    {
        $this->addBehavior('Sluggable');
        $this->addBehavior('CounterCache');
    }
}
