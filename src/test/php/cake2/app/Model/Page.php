<?php
App::uses('AppModel', 'Model');

/**
 * A model attaching behaviors through `$actsAs`: plain, with settings, and plugin-prefixed.
 */
class Page extends AppModel {

	public $useTable = false;

	public $actsAs = array('Containable', 'Sluggable', 'Audit' => array('on' => true), 'Probe.Hashable');

	public function hash($value) {
		return 'model:' . $value;
	}

}
