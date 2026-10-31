<?php

namespace Probe\Model\Behavior;

use Cake\ORM\Behavior;

class HashableBehavior extends Behavior
{
    public function hash(string $value): string
    {
        return md5($value);
    }
}
