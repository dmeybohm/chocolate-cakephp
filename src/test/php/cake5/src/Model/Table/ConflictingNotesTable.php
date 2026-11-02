<?php
declare(strict_types=1);

namespace App\Model\Table;

/**
 * A child table that configures an alias its parent's `initialize()` has already configured.
 */
class ConflictingNotesTable extends BaseNotesTable
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->belongsTo('Labels');
    }
}
