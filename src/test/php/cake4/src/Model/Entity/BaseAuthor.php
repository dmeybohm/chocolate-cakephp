<?php
declare(strict_types=1);

namespace App\Model\Entity;

use Cake\ORM\Entity;

/**
 * A parent entity whose accessors a child entity inherits.
 */
class BaseAuthor extends Entity
{
    protected function _getParentName()
    {
        return 'parent-name';
    }

    private function _getParentSecret()
    {
        return 'parent-secret';
    }

    protected function _getOverridden()
    {
        return 'parent';
    }
}
