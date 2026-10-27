<?php

namespace App\Controller;

use Cake\Controller\Controller;

class BeforeFilterOnlyController extends Controller
{
    public function beforeFilter()
    {
        $this->loadComponent('Late', ['className' => 'MovieMetadata']);
    }
}
