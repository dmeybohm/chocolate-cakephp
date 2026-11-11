<?php

namespace Acme\Vendored\Model\Behavior;

use Cake\ORM\Behavior;

/**
 * A behavior in a plugin with a vendor prefix.
 */
class StampedBehavior extends Behavior
{
    public function stamp(): string
    {
        return 'vendored';
    }
}
