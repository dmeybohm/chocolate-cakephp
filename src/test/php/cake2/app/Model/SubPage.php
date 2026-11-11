<?php
App::uses('Page', 'Model');

/**
 * A model declaring `$actsAs` below a parent model that declares its own.
 */
class SubPage extends Page {

	public $actsAs = array('Labelable');

}
