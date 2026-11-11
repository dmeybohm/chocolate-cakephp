<?php
App::uses('ModelBehavior', 'Model');

class HashableBehavior extends ModelBehavior {

	public function hash(Model $model, $value) {
		return md5($value);
	}

	public function slug(Model $model, $title) {
		return 'probe:' . $title;
	}

}
