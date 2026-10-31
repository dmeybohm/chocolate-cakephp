<?php

App::uses('AppController', 'Controller');

/**
 * An application controller declaring a layout property.
 */
class DeclaredController extends AppController {

	public $layout = 'probe';

	public function index() {
		$this->autoLayout = true;
	}
}
