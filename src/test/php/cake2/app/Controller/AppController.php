<?php

App::uses('Controller', 'Controller');

/**
 * Renders views without a layout, so a response body is exactly one view.
 */
class AppController extends Controller {

	public $autoLayout = false;

	public $uses = array();

}
