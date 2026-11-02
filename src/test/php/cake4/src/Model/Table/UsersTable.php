<?php
declare(strict_types=1);

namespace App\Model\Table;

use Cake\ORM\Table;

/**
 * A table with to-many and to-one associations.
 */
class UsersTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->hasMany('Reviews');
        $this->hasOne('Profiles');
    }
}
