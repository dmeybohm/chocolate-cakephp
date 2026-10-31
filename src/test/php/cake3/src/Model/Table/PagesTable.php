<?php

namespace App\Model\Table;

use Cake\Datasource\EntityInterface;
use Cake\ORM\Query;
use Cake\ORM\Table;

class PagesTable extends Table
{
    public function initialize(array $config)
    {
        $this->addBehavior('Timestamp');
        $this->addBehavior('Sluggable');
        $this->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
    }

    public function enableTree(): void
    {
        $this->addBehavior('Tree');
    }

    public function findSlugged(Query $query, array $options): Query
    {
        return $query->applyOptions(['finder' => 'table:slugged:' . $options['slug']]);
    }

    public function touch(EntityInterface $entity, string $eventName = 'Model.beforeSave'): bool
    {
        return false;
    }
}
