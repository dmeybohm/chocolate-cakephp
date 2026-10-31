<?php

namespace Probe\Model\Behavior;

use Cake\ORM\Behavior;

class SluggableBehavior extends Behavior
{
    public function slug(string $title): string
    {
        return 'probe:' . $title;
    }

    public function pluginSlug(): string
    {
        return 'probe';
    }
}
