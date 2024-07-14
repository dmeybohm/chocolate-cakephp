<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

class Article extends Entity
{
    public function someArticleMethod(): string
    {
        return "someArticleMethod";
    }
}