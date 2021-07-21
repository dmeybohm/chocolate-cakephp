<?php

namespace App\Controller\Component;

class MovieMetadataComponent extends \Cake\Controller\Component
{
    public function generateMetadata(array $movies) : array {
        return [
            'metadata',
        ];
    }
}
