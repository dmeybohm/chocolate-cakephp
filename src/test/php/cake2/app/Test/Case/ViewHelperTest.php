<?php

App::uses('ConfiguredView', 'View');
App::uses('ControllerTestCase', 'TestSuite');

/**
 * Which configuration supplies a view's helpers, which view helper resolution depends on.
 */
class ViewHelperTest extends ControllerTestCase {

	public function testControllerHelpersReplaceViewHelpersProperty() {
		$rendered = $this->testAction('/helpers/viewproperty', array('method' => 'get', 'return' => 'contents'));
		$this->assertSame('ConfiguredView:replaced', $rendered);
	}

	public function testViewBuiltByHandKeepsHelpersProperty() {
		$view = new ConfiguredView(null);
		$this->assertInstanceOf('ViewMarkerHelper', $view->ByProperty);
	}

}
