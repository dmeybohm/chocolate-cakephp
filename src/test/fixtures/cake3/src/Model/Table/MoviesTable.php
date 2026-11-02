<?php

namespace App\Model\Table;

use Cake\ORM\Query\SelectQuery;
use Cake\ORM\Table;

class MoviesTable extends Table
{
    public function findOwnedBy(SelectQuery $query, array $options): SelectQuery
    {
    }

    protected function _getLabel(): string
    {
        return 'not an entity accessor';
    }
}
