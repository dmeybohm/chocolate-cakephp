<?php

namespace App\Model\Behavior;

use Cake\Datasource\EntityInterface;

class ChildSluggableBehavior extends SluggableBehavior
{
    public function reslug(EntityInterface $entity): string
    {
        return $this->slug($entity);
    }
}
