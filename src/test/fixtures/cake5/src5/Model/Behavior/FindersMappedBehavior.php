<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

class FindersMappedBehavior extends Behavior
{
    protected array $_defaultConfig = [
        'implementedFinders' => ['recent' => 'findLatest'],
    ];

    public function stampIt(): string
    {
        return 'stamped';
    }

    public function findLatest(SelectQuery $query): SelectQuery
    {
        return $query;
    }

    public function findOldest(SelectQuery $query): SelectQuery
    {
        return $query;
    }
}
