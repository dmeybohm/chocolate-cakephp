<?php

namespace TestPlugin\View\Helper;

use Cake\View\Helper;

class TestPluginHelper extends Helper
{
    public function helpWithSomething() : string
    {
        return "test";
    }
}