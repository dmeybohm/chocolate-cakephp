<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class PostsTable extends Table
{
    const REMARKS = 'remarks';

    public function initialize(array $config)
    {
        parent::initialize($config);

        $this->hasMany('Reviews', ['propertyName' => 'written']);
        $this->hasMany('Comments', ['propertyName' => self::REMARKS]);
    }
}
