<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;

/**
 * An app behavior named like a core one.
 */
class CounterCacheBehavior extends Behavior
{
    public function counted(): string
    {
        return 'app';
    }
}
