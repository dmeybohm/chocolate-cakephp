<?php

App::uses('AppController', 'Controller');

/**
 * An application controller naming layouts in each form the framework offers.
 */
class FramedController extends AppController {

	public function renderargument() {
		$this->autoLayout = true;
		$this->render('body', 'probe');
	}

	public function assignment() {
		$this->autoLayout = true;
		$this->layout = 'probe';
		$this->render('body');
	}

	public function fromtemplate() {
		$this->autoLayout = true;
	}

	public function dataview() {
		$this->autoLayout = true;
		$this->viewClass = 'Json';
		$this->layout = 'probe';
		$this->render('body');
	}

	public function layoutpath() {
		$this->autoLayout = true;
		$this->layoutPath = 'json';
		$this->layout = 'probe';
		$this->render('body');
	}

	public function pluginunprefixed() {
		$this->autoLayout = true;
		$this->layout = 'plugin_only_layout';
		$this->render('body');
	}

	public function pluginprefixed() {
		$this->autoLayout = true;
		$this->layout = 'Probe.plugin_only_layout';
		$this->render('body');
	}
}
