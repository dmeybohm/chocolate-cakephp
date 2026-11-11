<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;

/**
 * A behavior that configures associations on the table it is attached to.
 */
class TaggableBehavior extends Behavior
{
    public function initialize(array $config)
    {
        $this->_table->hasMany('Tags');
        $this->getTable()->belongsTo('Albums');
    }
}
