<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

/**
 * The entity of ReviewsTable, with an accessor named for one of its association properties.
 */
class Review extends Entity
{
    protected function _getReviewer($value)
    {
        return 'accessor:' . (is_object($value) ? get_class($value) : var_export($value, true));
    }
}
