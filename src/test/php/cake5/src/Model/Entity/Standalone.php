<?php
declare(strict_types=1);

namespace App\Model\Entity;

use Cake\Datasource\EntityInterface;
use Cake\Datasource\EntityTrait;

/**
 * An entity that uses EntityTrait directly instead of extending Entity.
 */
class Standalone implements EntityInterface
{
    use EntityTrait;

    protected function _getOwnName()
    {
        return 'own-name';
    }

    private function _getOwnSecret()
    {
        return 'own-secret';
    }
}
