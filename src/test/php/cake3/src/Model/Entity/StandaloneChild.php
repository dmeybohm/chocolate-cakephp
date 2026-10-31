<?php

namespace App\Model\Entity;

/**
 * A child of the entity that uses EntityTrait directly.
 */
class StandaloneChild extends Standalone
{
    private function _getChildSecret()
    {
        return 'child-secret';
    }
}
