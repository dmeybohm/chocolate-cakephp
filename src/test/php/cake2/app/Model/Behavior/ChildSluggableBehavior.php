<?php
App::uses('SluggableBehavior', 'Model/Behavior');

/**
 * A behavior inheriting another's methods.
 */
class ChildSluggableBehavior extends SluggableBehavior {

	public function reslug(Model $model, $title) {
		return $this->slug($model, $title) . '-2';
	}

}
