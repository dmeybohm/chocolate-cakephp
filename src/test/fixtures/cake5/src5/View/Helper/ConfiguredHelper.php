<?php

namespace App\View\Helper;

class ConfiguredHelper extends \Cake\View\Helper
{
    protected array $helpers = ['MovieFormatter', 'Alias' => ['className' => 'ArtistFormatter']];
}
