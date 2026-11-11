<?php

namespace App\Model\Table;

use Cake\Datasource\EntityInterface;
use Cake\ORM\Query\SelectQuery;
use Cake\ORM\Table;

class PagesTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->addBehavior('Timestamp');
        $this->addBehavior('Sluggable');
        $this->addBehavior('CounterCache');
        $this->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
    }

    public function enableTree(): void
    {
        $this->addBehavior('Tree');
    }

    public function findSlugged(SelectQuery $query, string $slug): SelectQuery
    {
        return $query;
    }

    public function touch(EntityInterface $entity, string $eventName = 'Model.beforeSave'): bool
    {
        return true;
    }
}
