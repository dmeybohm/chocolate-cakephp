<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class CritiquesTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);

        $this->belongsTo('Authors', ['className' => 'Users']);
        $this->belongsTo('Movies');
    }
}
