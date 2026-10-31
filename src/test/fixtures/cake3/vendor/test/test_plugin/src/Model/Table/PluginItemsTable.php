<?php
namespace TestPlugin\Model\Table;

use Cake\ORM\Query;
use Cake\ORM\Table;

class PluginItemsTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);
        $this->setTable('plugin_items');
    }

    public function pluginItemsMethod(): void
    {
    }

    public function findFeaturedItems(Query $query): Query
    {
        return $query;
    }
}
