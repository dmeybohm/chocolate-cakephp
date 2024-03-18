<?php

namespace App\Model\Table;

use Cake\ORM\Query\SelectQuery;
use Cake\ORM\Table;

use Cake\Database\Query as DbQuery;

class MoviesTable extends Table
{
    public function findOwnedBy(SelectQuery $query, array $options): SelectQuery
    {
    }

}
