<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

class Critique extends Entity
{
    protected function _getAuthor(): string
    {
        return 'name';
    }
}
