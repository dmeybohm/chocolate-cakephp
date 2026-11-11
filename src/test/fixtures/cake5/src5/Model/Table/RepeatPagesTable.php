<?php

namespace App\Model\Table;

class RepeatPagesTable extends PagesTable
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->addBehavior('Audit');
        $this->addBehavior('FindersMapped');
    }
}
