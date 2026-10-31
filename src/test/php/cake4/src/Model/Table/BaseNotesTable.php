<?php
declare(strict_types=1);

namespace App\Model\Table;

use Cake\ORM\Table;

/**
 * A parent table that configures one association in `initialize()` and another in a query method.
 */
class BaseNotesTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->hasMany('Labels');
    }

    public function configureComments()
    {
        $this->hasMany('Comments');
    }
}
