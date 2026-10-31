<?php
App::uses('ModelBehavior', 'Model');

/**
 * A behavior whose public methods are mapped onto the model, minus callbacks and helpers.
 */
class SluggableBehavior extends ModelBehavior {

	public function setup(Model $model, $config = array()) {
	}

	public function slug(Model $model, $title) {
		return $this->_normalize($title);
	}

	public function beforeSave(Model $model, $options = array()) {
		return true;
	}

	protected function _normalize($value) {
		return strtolower(str_replace(' ', '-', $value));
	}

}
