<?php

namespace TestPlugin\Model\Table;

use Cake\ORM\Query;
use Cake\ORM\Table;

class DirectorsTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);

        $this->setTable('directors');
        $this->setDisplayField('name');
        $this->setPrimaryKey('id');
    }

    public function findPlugin(Query $query, array $options): Query
    {
        return $query;
    }

    public function myPluginDirectorMethod()
    {
        return "This is a plugin director method";
    }
}