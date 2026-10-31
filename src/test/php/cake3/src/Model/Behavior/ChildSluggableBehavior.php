<?php

namespace App\Model\Behavior;

/**
 * A behavior inheriting another's reflected members.
 */
class ChildSluggableBehavior extends SluggableBehavior
{
    public function reslug(string $title): string
    {
        return $this->slug($title) . '-2';
    }
}
