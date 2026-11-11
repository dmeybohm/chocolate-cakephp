<?php
declare(strict_types=1);

namespace Unloaded\Model\Behavior;

use Cake\ORM\Behavior;

/**
 * A behavior in a plugin the application does not load.
 */
class MarkedBehavior extends Behavior
{
    public function mark(): string
    {
        return 'unloaded';
    }
}
