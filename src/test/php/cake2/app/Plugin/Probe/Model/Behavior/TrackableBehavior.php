<?php
App::uses('ModelBehavior', 'Model');

/**
 * A plugin behavior nothing attaches, so its class is not loaded until a test asks for it.
 */
class TrackableBehavior extends ModelBehavior {

	public function track(Model $model) {
		return 'probe';
	}

}
