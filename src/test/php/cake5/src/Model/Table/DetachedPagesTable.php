<?php
declare(strict_types=1);

namespace App\Model\Table;

/**
 * A table whose `initialize()` does not call its parent's.
 */
class DetachedPagesTable extends PagesTable
{
    public function initialize(array $config): void
    {
        $this->addBehavior('Sluggable');
    }
}
