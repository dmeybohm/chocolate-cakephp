<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use ArrayObject;
use Cake\Datasource\EntityInterface;
use Cake\Event\EventInterface;
use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

/**
 * A behavior with no `implementedMethods` config, so its members come from reflection.
 */
class SluggableBehavior extends Behavior
{
    public function initialize(array $config): void
    {
    }

    public function slug(string $title): string
    {
        return $this->_normalize($title);
    }

    public function findSlugged(SelectQuery $query, string $slug): SelectQuery
    {
        return $query->applyOptions(['finder' => 'behavior:slugged:' . $slug]);
    }

    public function beforeSave(EventInterface $event, EntityInterface $entity, ArrayObject $options): void
    {
    }

    protected function _normalize(string $value): string
    {
        return strtolower(str_replace(' ', '-', $value));
    }
}
