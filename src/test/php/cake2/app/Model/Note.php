<?php
App::uses('AppModel', 'Model');

/**
 * A model attaching nothing of its own, for runtime loads.
 */
class Note extends AppModel {

	public $useTable = false;

}
