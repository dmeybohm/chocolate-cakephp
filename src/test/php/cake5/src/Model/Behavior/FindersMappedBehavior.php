<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

/**
 * A behavior whose `$_defaultConfig` names its finders but not its methods.
 */
class FindersMappedBehavior extends Behavior
{
    protected array $_defaultConfig = [
        'implementedFinders' => ['recent' => 'findLatest'],
    ];

    public function stampIt(): string
    {
        return 'stamped';
    }

    public function unstamp(): string
    {
        return 'unstamped';
    }

    public function findLatest(SelectQuery $query): SelectQuery
    {
        return $query;
    }

    public function findOldest(SelectQuery $query): SelectQuery
    {
        return $query;
    }
}
