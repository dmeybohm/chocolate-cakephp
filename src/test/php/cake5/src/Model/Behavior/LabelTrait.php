<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use Cake\ORM\Query\SelectQuery;

/**
 * Members a behavior gains from a trait.
 */
trait LabelTrait
{
    public function label(): string
    {
        return 'label';
    }

    public function findLabelled(SelectQuery $query): SelectQuery
    {
        return $query->applyOptions(['finder' => 'trait:labelled']);
    }

    protected function hidden(): string
    {
        return 'hidden';
    }
}
