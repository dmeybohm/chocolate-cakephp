<?php

namespace App\Model\Behavior;

use Cake\ORM\Behavior;

class CounterCacheBehavior extends Behavior
{
    public function counted(): string
    {
        return 'counted';
    }
}
