<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

class BaseMovie extends Entity
{
    protected function _getInheritedLabel(): string
    {
        return 'inherited';
    }
}
