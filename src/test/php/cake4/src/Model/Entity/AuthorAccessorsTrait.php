<?php
declare(strict_types=1);

namespace App\Model\Entity;

/**
 * A trait that contributes an accessor to the entity using it.
 */
trait AuthorAccessorsTrait
{
    protected function _getTraitName()
    {
        return 'trait-name';
    }
}
