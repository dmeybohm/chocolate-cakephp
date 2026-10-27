<?php

namespace App\View\Helper;

class ConfiguredHelper extends \Cake\View\Helper
{
    public $helpers = ['MovieFormatter', 'Alias' => ['className' => 'ArtistFormatter']];
}
