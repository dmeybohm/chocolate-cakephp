<?php

App::uses('ProbeAppController', 'Probe.Controller');

/**
 * A controller inside the Probe plugin naming layouts.
 */
class FramesController extends ProbeAppController {

	public function shared() {
		$this->autoLayout = true;
		$this->layout = 'shared_layout';
		$this->render('body');
	}

	public function appfallback() {
		$this->autoLayout = true;
		$this->layout = 'app_only_layout';
		$this->render('body');
	}

	public function overridden() {
		$this->autoLayout = true;
		$this->layout = 'overridden_layout';
		$this->render('body');
	}
}
