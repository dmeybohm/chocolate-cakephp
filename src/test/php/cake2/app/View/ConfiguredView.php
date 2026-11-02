<?php

App::uses('View', 'View');

/**
 * A view class that configures helpers the way an application's AppView does.
 */
class ConfiguredView extends View {

	public $helpers = array('ByProperty' => array('className' => 'ViewMarker'));

}
