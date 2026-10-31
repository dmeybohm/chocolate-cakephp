<?php

App::uses('CakeTestCase', 'TestSuite');

/**
 * The framework release the suite runs against.
 */
class FrameworkVersionTest extends CakeTestCase {

	public function testInstalledFrameworkIsThePinnedRelease() {
		$this->assertSame('2.10.24', Configure::version());
	}

}
