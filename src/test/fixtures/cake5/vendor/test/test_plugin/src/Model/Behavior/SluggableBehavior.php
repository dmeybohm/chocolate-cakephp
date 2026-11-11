<?php

namespace TestPlugin\Model\Behavior;

use Cake\Datasource\EntityInterface;
use Cake\ORM\Behavior;

class SluggableBehavior extends Behavior
{
    public function pluginSlug(): string
    {
        return 'plugin';
    }

    public function slug(EntityInterface $entity): string
    {
        return 'plugin';
    }
}
