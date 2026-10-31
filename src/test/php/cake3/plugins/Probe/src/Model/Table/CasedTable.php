<?php

namespace Probe\Model\Table;

use Cake\ORM\Table;

/**
 * A table no other test loads, because PHP class lookups ignore case once a class is loaded.
 */
class CasedTable extends Table
{
}
