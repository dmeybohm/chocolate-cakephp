<?php

namespace App\View\Helper;

class ConfiguredHelper extends \Cake\View\Helper
{
    protected $helpers = ['MovieFormatter', 'Alias' => ['className' => 'ArtistFormatter']];
}
