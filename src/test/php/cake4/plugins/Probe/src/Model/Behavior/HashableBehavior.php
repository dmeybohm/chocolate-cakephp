<?php
declare(strict_types=1);

namespace Probe\Model\Behavior;

use Cake\ORM\Behavior;

class HashableBehavior extends Behavior
{
    public function hash(string $value): string
    {
        return md5($value);
    }
}
