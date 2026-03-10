<?php
namespace TestPlugin\Model\Table;

use Cake\ORM\Table;

class PluginItemsTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->setTable('plugin_items');
    }
}
