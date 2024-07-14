<?php

namespace App\Model\Entity;

use Cake\ORM\Entity;

class Movie extends Entity
{
    public function someMovieMethod(): string
    {
        return "someMovieMethod";
    }
}