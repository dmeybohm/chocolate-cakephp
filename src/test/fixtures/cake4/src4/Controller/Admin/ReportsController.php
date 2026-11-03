<?php

namespace App\Controller\Admin;

use App\Controller\AppController;

class ReportsController extends AppController
{
    public function index()
    {
        $this->set('title', 'Reports');
        $this->set('count', 1);
    }

    public function monthly()
    {
    }
}
