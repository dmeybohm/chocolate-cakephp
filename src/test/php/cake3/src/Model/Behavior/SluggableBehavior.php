<?php

namespace App\Model\Behavior;

use ArrayObject;
use Cake\Datasource\EntityInterface;
use Cake\Event\Event;
use Cake\ORM\Behavior;
use Cake\ORM\Query;

/**
 * A behavior with no `implementedMethods` config, so its members come from reflection.
 */
class SluggableBehavior extends Behavior
{
    public function initialize(array $config)
    {
    }

    public function slug(string $title): string
    {
        return $this->_normalize($title);
    }

    public function findSlugged(Query $query, array $options): Query
    {
        return $query->applyOptions(['finder' => 'behavior:slugged:' . $options['slug']]);
    }

    public function beforeSave(Event $event, EntityInterface $entity, ArrayObject $options): void
    {
    }

    protected function _normalize(string $value): string
    {
        return strtolower(str_replace(' ', '-', $value));
    }
}
