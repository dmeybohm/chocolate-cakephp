<?php
declare(strict_types=1);

namespace App\Model\Table;

use Cake\ORM\Table;

class PluginPagesTable extends Table
{
    public function initialize(array $config): void
    {
        $this->addBehaviors(['Probe.Sluggable', 'Audit' => ['implementedMethods' => []]]);
    }
}
