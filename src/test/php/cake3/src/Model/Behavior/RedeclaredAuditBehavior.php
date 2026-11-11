<?php

namespace App\Model\Behavior;

/**
 * A behavior redeclaring `$_defaultConfig` without its parent's maps.
 */
class RedeclaredAuditBehavior extends AuditBehavior
{
    protected $_defaultConfig = [
        'on' => true,
    ];

    public function extra(): string
    {
        return 'extra';
    }
}
