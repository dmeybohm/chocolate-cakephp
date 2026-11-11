<?php

namespace App\Model\Behavior;

/**
 * A behavior inheriting another's `$_defaultConfig` maps.
 */
class ChildAuditBehavior extends AuditBehavior
{
    public function extra(): string
    {
        return 'extra';
    }
}
