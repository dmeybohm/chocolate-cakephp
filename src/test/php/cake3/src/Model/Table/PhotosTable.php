<?php

namespace App\Model\Table;

use Cake\ORM\Table;

class PhotosTable extends Table
{
    public function initialize(array $config)
    {
        $this->addBehavior('Taggable');
    }
}
