<?php

namespace App\Model\Behavior;

use Cake\Datasource\EntityInterface;
use Cake\Event\EventInterface;
use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

class SluggableBehavior extends Behavior
{
    public function initialize(array $config): void
    {
    }

    public function slug(EntityInterface $entity): string
    {
        return $this->_normalize((string)$entity->get('title'));
    }

    public function findSlugged(SelectQuery $query, string $slug): SelectQuery
    {
        return $query->where(['slug' => $slug]);
    }

    public function beforeSave(EventInterface $event, EntityInterface $entity): void
    {
    }

    protected function _normalize(string $value): string
    {
        return strtolower($value);
    }
}
