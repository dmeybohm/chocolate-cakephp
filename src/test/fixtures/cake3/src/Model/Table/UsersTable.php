<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class UsersTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);

        $this->hasMany('Reviews');
        $this->hasOne('Profiles');
    }

    public function usersTableMethod(): void
    {
    }
}
