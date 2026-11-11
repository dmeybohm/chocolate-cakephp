<?php
App::uses('ModelBehavior', 'Model');

/**
 * An app behavior nothing attaches, so its class is not loaded until a test asks for it.
 */
class StampableBehavior extends ModelBehavior {

	public function stamp(Model $model) {
		return 'app';
	}

}
