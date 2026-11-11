<?php
App::uses('ModelBehavior', 'Model');

/**
 * A behavior for a model two levels below the one whose `$actsAs` it would merge with.
 */
class TaggableBehavior extends ModelBehavior {

	public function tag(Model $model) {
		return 'tag';
	}

}
