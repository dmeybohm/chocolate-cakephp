<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class ReviewsTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);

        $this->belongsTo('Movies');
        $this->belongsTo('Authors', ['className' => 'Users']);
        $this->hasMany('TestPlugin.Ratings');
        $this->addAssociations([
            'belongsTo' => ['Editors' => ['className' => 'Users']],
            'hasMany' => ['Comments'],
        ]);
    }
}
