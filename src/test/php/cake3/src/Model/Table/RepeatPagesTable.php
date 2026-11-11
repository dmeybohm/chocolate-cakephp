<?php

namespace App\Model\Table;

/**
 * A table loading a behavior its parent loads with options again, plainly, after `parent::initialize()`.
 */
class RepeatPagesTable extends PagesTable
{
    public function initialize(array $config)
    {
        parent::initialize($config);
        $this->addBehavior('Audit');
    }
}
