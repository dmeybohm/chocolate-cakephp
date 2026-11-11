<?php
App::uses('SubPage', 'Model');

/**
 * A model declaring `$actsAs` two levels below `Page`.
 */
class SubSubPage extends SubPage {

	public $actsAs = array('Taggable');

}
