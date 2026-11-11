<?php
App::uses('ModelBehavior', 'Model');

/**
 * A behavior `AppModel` attaches, so every model has it.
 */
class BaselineBehavior extends ModelBehavior {

	public function baseline(Model $model) {
		return 'baseline';
	}

}
