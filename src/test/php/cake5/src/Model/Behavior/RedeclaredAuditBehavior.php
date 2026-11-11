<?php
declare(strict_types=1);

namespace App\Model\Behavior;

/**
 * A behavior redeclaring `$_defaultConfig` without its parent's maps.
 */
class RedeclaredAuditBehavior extends AuditBehavior
{
    protected array $_defaultConfig = [
        'on' => true,
    ];

    public function extra(): string
    {
        return 'extra';
    }
}
