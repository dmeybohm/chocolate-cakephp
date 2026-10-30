<?php

App::uses('AppController', 'Controller');

/**
 * An application controller, outside any plugin.
 */
class WidgetsController extends AppController {

	public function unprefixed() {
		$this->render('only_in_plugin');
	}

	public function prefixed() {
		$this->render('Probe.only_in_plugin');
	}

	public function pluginelement() {
	}

	public function prefixedelement() {
	}

	public function admin_index() {
	}

	public function prefixedfallback() {
		$this->render('Probe.app_only');
	}

	public function prefixedoverride() {
		$this->render('Probe.overridden_widget');
	}

	public function prefixedappelement() {
	}

	public function prefixedinner() {
		$this->render('Probe.inner');
	}
}
