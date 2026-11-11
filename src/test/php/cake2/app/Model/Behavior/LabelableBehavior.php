<?php
App::uses('ModelBehavior', 'Model');

/**
 * Members a behavior gains from a trait.
 */
trait LabelTrait {

	public function label(Model $model) {
		return 'label';
	}

	protected function hidden(Model $model) {
		return 'hidden';
	}

}

/**
 * A behavior with a method from a trait and a static method.
 */
class LabelableBehavior extends ModelBehavior {

	use LabelTrait;

	public static function build() {
		return 'built';
	}

}
