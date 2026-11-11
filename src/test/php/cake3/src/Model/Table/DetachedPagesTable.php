<?php

namespace App\Model\Table;

/**
 * A table whose `initialize()` does not call its parent's.
 */
class DetachedPagesTable extends PagesTable
{
    public function initialize(array $config)
    {
        $this->addBehavior('Sluggable');
    }
}
