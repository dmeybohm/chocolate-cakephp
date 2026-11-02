<?php
declare(strict_types=1);

namespace App\Model\Table;

/**
 * A child table that configures the aliases of its parent's associations as other types.
 */
class NotesTable extends BaseNotesTable
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->belongsTo('Labels');
        $this->belongsTo('Comments');
    }
}
