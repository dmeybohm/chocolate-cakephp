<?php

App::uses('ControllerTestCase', 'TestSuite');

/**
 * Template resolution, which view navigation and view file creation depend on.
 */
class TemplateResolutionTest extends ControllerTestCase {

	private function renderedBy($url) {
		return $this->testAction($url, array('method' => 'get', 'return' => 'contents'));
	}

	public function testPluginControllerRendersImplicitTemplateFromPlugin() {
		$this->assertSame('plugin:Things/index', $this->renderedBy('/probe/things/index'));
	}

	public function testPluginControllerRendersUnprefixedTemplateFromPlugin() {
		$this->assertSame('plugin:Things/other', $this->renderedBy('/probe/things/other'));
	}

	public function testPluginControllerFallsBackToAppTemplate() {
		$this->assertSame('app:Things/appfallback', $this->renderedBy('/probe/things/appfallback'));
	}

	public function testAppOverrideWinsOverPluginTemplate() {
		$this->assertSame('override:Things/overridden', $this->renderedBy('/probe/things/overridden'));
	}

	public function testAppControllerDoesNotReachPluginTemplateUnprefixed() {
		$this->setExpectedException('MissingViewException', 'Widgets/only_in_plugin.ctp');
		$this->renderedBy('/widgets/unprefixed');
	}

	public function testAppControllerRendersPrefixedPluginTemplate() {
		$this->assertSame('plugin:Widgets/only_in_plugin', $this->renderedBy('/widgets/prefixed'));
	}

	public function testRoutingPrefixResolvesToPrefixTemplate() {
		$this->assertSame('app:Widgets/admin_index', $this->renderedBy('/admin/widgets/index'));
	}

	public function testPrefixedRenderFallsBackToAppTemplate() {
		$this->assertSame('app:Widgets/app_only', $this->renderedBy('/widgets/prefixedfallback'));
	}

	public function testAppOverrideWinsForPrefixedRender() {
		$this->assertSame('override:Widgets/overridden_widget', $this->renderedBy('/widgets/prefixedoverride'));
	}

	public function testPluginControllerRendersOtherPluginTemplate() {
		$this->assertSame('other:Things/in_other', $this->renderedBy('/probe/things/crossplugin'));
	}

	public function testRenderOfOtherPluginFallsBackToAppTemplate() {
		$this->assertSame('app:Things/in_app', $this->renderedBy('/probe/things/crossfallback'));
	}

	public function testRenderOfOtherPluginDoesNotFallBackToCallingPlugin() {
		$this->setExpectedException('MissingViewException', 'in_probe.ctp');
		$this->renderedBy('/probe/things/crossnotcaller');
	}

	public function testAppOverrideWinsForRenderOfOtherPlugin() {
		$this->assertSame('override:Things/overridden_other', $this->renderedBy('/probe/things/crossoverride'));
	}

	public function testLayoutNamedFromTemplateWraps() {
		$this->assertSame('layout:app:Widgets/templatelayout', $this->renderedBy('/widgets/templatelayout'));
	}
}
