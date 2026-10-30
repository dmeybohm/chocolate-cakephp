<?php

App::uses('ControllerTestCase', 'TestSuite');

/**
 * Element resolution, which element navigation and related usages depend on.
 */
class ElementResolutionTest extends ControllerTestCase {

	private function renderedBy($url) {
		return $this->testAction($url, array('method' => 'get', 'return' => 'contents'));
	}

	public function testPluginTemplatePrefersPluginElement() {
		$this->assertSame('plugin:element/shared', $this->renderedBy('/probe/things/sharedelement'));
	}

	public function testPluginTemplateFallsBackToAppElement() {
		$this->assertSame('app:element/app_only', $this->renderedBy('/probe/things/appelement'));
	}

	public function testAppTemplateDoesNotReachPluginElementUnprefixed() {
		$rendered = $this->renderedBy('/widgets/pluginelement');
		$this->assertContains('Element Not Found: Elements/plugin_only.ctp', $rendered);
		$this->assertNotContains('plugin:element/plugin_only', $rendered);
	}

	public function testAppTemplateRendersPrefixedPluginElement() {
		$this->assertSame('plugin:element/plugin_only', $this->renderedBy('/widgets/prefixedelement'));
	}

	public function testPrefixedElementFallsBackToAppElement() {
		$this->assertSame('app:element/app_only', $this->renderedBy('/widgets/prefixedappelement'));
	}

}
