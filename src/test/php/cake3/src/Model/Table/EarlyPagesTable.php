<?php

namespace App\Model\Table;

/**
 * A table loading a behavior plainly before `parent::initialize()` loads it with options.
 */
class EarlyPagesTable extends PagesTable
{
    public function initialize(array $config)
    {
        $this->addBehavior('Audit');
        parent::initialize($config);
    }
}
