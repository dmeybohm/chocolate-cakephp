<?php

namespace Acme\Vendored\Model\Behavior;

use Cake\ORM\Behavior;

class StampedBehavior extends Behavior
{
    public function stamp(): string
    {
        return 'stamped';
    }
}
