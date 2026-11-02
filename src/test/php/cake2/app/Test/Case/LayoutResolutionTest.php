<?php

App::uses('ControllerTestCase', 'TestSuite');

/**
 * Layout resolution, which layout navigation depends on.
 */
class LayoutResolutionTest extends ControllerTestCase {

	private function renderedBy($url) {
		return $this->testAction($url, array('method' => 'get', 'return' => 'contents'));
	}

	public function testRenderSecondArgumentNamesLayout() {
		$this->assertSame('layout:body', $this->renderedBy('/framed/renderargument'));
	}

	public function testControllerLayoutAssignmentNamesLayout() {
		$this->assertSame('layout:body', $this->renderedBy('/framed/assignment'));
	}

	public function testControllerLayoutPropertyNamesLayout() {
		$this->assertSame('layout:declared', $this->renderedBy('/declared/index'));
	}

	public function testRenderFromTemplateWrapsItsOutputAgainInTheOuterLayout() {
		$this->assertSame('default:layout:body', $this->renderedBy('/framed/fromtemplate'));
	}

	public function testDataViewRendersWithoutLayout() {
		$this->assertSame('json-body', $this->renderedBy('/framed/dataview'));
	}

	public function testLayoutPathSelectsSubdirectory() {
		$this->assertSame('json-layout:body', $this->renderedBy('/framed/layoutpath'));
	}

	public function testPluginControllerPrefersPluginLayout() {
		$this->assertSame('plugin-layout:body', $this->renderedBy('/probe/frames/shared'));
	}

	public function testPluginControllerFallsBackToAppLayout() {
		$this->assertSame('app-layout:body', $this->renderedBy('/probe/frames/appfallback'));
	}

	public function testAppOverrideWinsOverPluginLayout() {
		$this->assertSame('override-layout:body', $this->renderedBy('/probe/frames/overridden'));
	}

	public function testAppControllerDoesNotReachPluginLayoutUnprefixed() {
		$this->setExpectedException('MissingLayoutException');
		$this->renderedBy('/framed/pluginunprefixed');
	}

	public function testAppControllerReachesPluginLayoutWithPrefix() {
		$this->assertSame('plugin-layout:body', $this->renderedBy('/framed/pluginprefixed'));
	}
}
