<?php
namespace App\Controller;

/**
 * An application controller naming layouts in each form the framework offers.
 */
class FramedController extends AppController
{
    public function renderargument()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->render('body', 'probe');
    }

    public function builderset()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function builderdeprecated()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->layout('probe');
        $this->render('body');
    }

    public function assignment()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->layout = 'probe';
        $this->render('body');
    }

    public function assigned()
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function shorthand()
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function fromtemplate()
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function chained()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('probe')->setTemplatePath('Framed/Nested')->setTemplate('chained');
    }

    public function dataview()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setClassName('Json');
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function layoutpath()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayoutPath('json');
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function pluginunprefixed()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('plugin_only_layout');
        $this->render('body');
    }

    public function pluginprefixed()
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('Probe.plugin_only_layout');
        $this->render('body');
    }
}
