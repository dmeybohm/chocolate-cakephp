<?php
declare(strict_types=1);

namespace Acme\Nested;

use Cake\Core\BasePlugin;

/**
 * A plugin installed into another plugin's own vendor directory, not the app's.
 */
class NestedPlugin extends BasePlugin
{
}
