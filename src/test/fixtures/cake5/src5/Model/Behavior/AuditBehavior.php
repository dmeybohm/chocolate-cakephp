<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query\SelectQuery;

class AuditBehavior extends Behavior
{
    protected array $_defaultConfig = [
        'implementedMethods' => ['audit' => 'writeAudit'],
        'implementedFinders' => ['audited' => 'findAudited'],
    ];

    public function writeAudit(string $message): int
    {
        return 1;
    }

    public function findAudited(SelectQuery $query): SelectQuery
    {
        return $query;
    }

    public function notMixedIn(): void
    {
    }
}
