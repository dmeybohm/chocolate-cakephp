<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query;

/**
 * A behavior whose `$_defaultConfig` names its methods but not its finders.
 */
class MethodsMappedBehavior extends Behavior
{
    protected $_defaultConfig = [
        'implementedMethods' => ['mark' => 'stampIt'],
    ];

    public function stampIt(): string
    {
        return 'stamped';
    }

    public function unstamp(): string
    {
        return 'unstamped';
    }

    public function findLatest(Query $query): Query
    {
        return $query;
    }

    public function findOldest(Query $query): Query
    {
        return $query;
    }
}
