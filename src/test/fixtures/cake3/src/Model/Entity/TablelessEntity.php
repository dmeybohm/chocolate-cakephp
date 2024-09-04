<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

class TablelessEntity extends Entity
{
    public function someTablelessEntityMethod(): string
    {
        return "someTablelessEntityMethod";
    }
}