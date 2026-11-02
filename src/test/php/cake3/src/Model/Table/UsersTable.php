<?php

namespace App\Model\Table;

use Cake\ORM\Table;

/**
 * A table with to-many and to-one associations.
 */
class UsersTable extends Table
{
    public function initialize(array $config)
    {
        parent::initialize($config);
        $this->hasMany('Reviews');
        $this->hasOne('Profiles');
    }
}
