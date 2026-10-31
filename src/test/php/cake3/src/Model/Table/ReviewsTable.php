<?php

namespace App\Model\Table;

use Cake\ORM\Table;

/**
 * A table whose associations cover every way a property name and target entity are derived.
 */
class ReviewsTable extends Table
{
    public function initialize(array $config)
    {
        parent::initialize($config);
        $this->belongsTo('Authors', ['className' => 'Users']);
        $this->belongsTo('ReviewAuthors', ['className' => 'Users']);
        $this->belongsTo('Editors', ['className' => 'Users', 'propertyName' => 'reviewer']);
        $this->belongsTo('Movies');
        $this->belongsTo('People');
        $this->hasMany('ReviewComments');
        $this->hasMany('Probe.Widgets');
        $this->belongsToMany('Tags');
    }
}
