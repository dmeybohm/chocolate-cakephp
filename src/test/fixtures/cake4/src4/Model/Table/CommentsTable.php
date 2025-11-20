<?php
declare(strict_types=1);

namespace App\Model\Table;

use Cake\ORM\Table;

class CommentsTable extends Table
{
    public function initialize(array $config): void
    {
        parent::initialize($config);

        $this->setTable('comments');
        $this->setDisplayField('content');
        $this->setPrimaryKey('id');
    }
}
