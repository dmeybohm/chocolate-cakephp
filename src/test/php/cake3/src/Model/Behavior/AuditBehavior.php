<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query;

/**
 * A behavior whose `$_defaultConfig` names its mixed-in methods and finders, as the built-in
 * behaviors do.
 */
class AuditBehavior extends Behavior
{
    protected $_defaultConfig = [
        'implementedMethods' => ['audit' => 'writeAudit'],
        'implementedFinders' => ['audited' => 'findAudited'],
    ];

    public function writeAudit(string $message): string
    {
        return 'audit:' . $message;
    }

    public function findAudited(Query $query): Query
    {
        return $query->applyOptions(['finder' => 'behavior:audited']);
    }

    public function notMixedIn(): string
    {
        return 'not mixed in';
    }
}
