<?php
declare(strict_types=1);

namespace App\Controller;

/**
 * An application controller naming layouts in each form the framework offers.
 */
class FramedController extends AppController
{
    public function renderargument(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->render('body', 'probe');
    }

    public function builderset(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function builderdeprecated(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->layout('probe');
        $this->render('body');
    }

    public function assignment(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->layout = 'probe';
        $this->render('body');
    }

    public function assigned(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function shorthand(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function fromtemplate(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
    }

    public function chained(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('probe')->setTemplatePath('Framed/Nested')->setTemplate('chained');
    }

    public function dataview(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setClassName('Json');
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function layoutpath(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayoutPath('json');
        $this->viewBuilder()->setLayout('probe');
        $this->render('body');
    }

    public function pluginunprefixed(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('plugin_only_layout');
        $this->render('body');
    }

    public function pluginprefixed(): void
    {
        $this->viewBuilder()->enableAutoLayout(true);
        $this->viewBuilder()->setLayout('Probe.plugin_only_layout');
        $this->render('body');
    }
}
