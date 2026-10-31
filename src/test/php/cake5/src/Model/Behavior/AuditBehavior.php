<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

/**
 * A behavior whose `$_defaultConfig` names its mixed-in methods and finders, as the built-in
 * behaviors do.
 */
class AuditBehavior extends Behavior
{
    protected array $_defaultConfig = [
        'implementedMethods' => ['audit' => 'writeAudit'],
        'implementedFinders' => ['audited' => 'findAudited'],
    ];

    public function writeAudit(string $message): string
    {
        return 'audit:' . $message;
    }

    public function findAudited(SelectQuery $query): SelectQuery
    {
        return $query->applyOptions(['finder' => 'behavior:audited']);
    }

    public function notMixedIn(): string
    {
        return 'not mixed in';
    }
}
