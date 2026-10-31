<?php

App::uses('ProbeAppController', 'Probe.Controller');

/**
 * A controller inside the Probe plugin.
 */
class ThingsController extends ProbeAppController {

	public function index() {
	}

	public function other() {
		$this->render('other');
	}

	public function overridden() {
	}

	public function sharedelement() {
	}

	public function appelement() {
	}

	public function appfallback() {
	}

	public function crossplugin() {
		$this->render('Other.in_other');
	}

	public function crossfallback() {
		$this->render('Other.in_app');
	}

	public function crossnotcaller() {
		$this->render('Other.in_probe');
	}

	public function crossoverride() {
		$this->render('Other.overridden_other');
	}

	public function crossinner() {
		$this->render('Other.inner');
	}

	public function crosselement() {
	}

	public function crosselementfallback() {
	}

	public function crosselementnotcaller() {
	}

	public function fallbackinner() {
	}
}
