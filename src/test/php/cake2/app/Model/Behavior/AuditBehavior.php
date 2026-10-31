<?php
App::uses('ModelBehavior', 'Model');

class AuditBehavior extends ModelBehavior {

	public function setup(Model $model, $config = array()) {
		$this->settings[$model->alias] = $config;
	}

	public function audit(Model $model, $message) {
		return 'audit:' . $message;
	}

}
