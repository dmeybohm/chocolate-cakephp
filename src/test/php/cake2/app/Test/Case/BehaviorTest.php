<?php

App::uses('CakeTestCase', 'TestSuite');
App::uses('ClassRegistry', 'Utility');
App::uses('Page', 'Model');

/**
 * How a model attaches behaviors and which of their methods it gains, which behavior
 * completion, types and navigation depend on.
 */
class BehaviorTest extends CakeTestCase {

	public $Page;

	public function setUp() {
		parent::setUp();
		ClassRegistry::flush();
		$this->Page = ClassRegistry::init('Page');
	}

	public function testActsAsLoadsPlainConfiguredAndPluginBehaviors() {
		$this->assertSame(array('Baseline', 'Containable', 'Sluggable', 'Audit', 'Hashable'), $this->Page->Behaviors->loaded());
		$this->assertInstanceOf('ContainableBehavior', $this->Page->Behaviors->Containable);
		$this->assertInstanceOf('SluggableBehavior', $this->Page->Behaviors->Sluggable);
		$this->assertInstanceOf('HashableBehavior', $this->Page->Behaviors->Hashable);
		$this->assertSame(array('on' => true), $this->Page->Behaviors->Audit->settings['Page']);
	}

	public function testMappedMethodsExcludeUnderscoredCallbacksAndModelBehaviorMethods() {
		$methods = $this->Page->Behaviors->methods();
		$this->assertArrayHasKey('slug', $methods);
		$this->assertArrayHasKey('audit', $methods);
		$this->assertArrayHasKey('contain', $methods);
		foreach (array('_normalize', 'setup', 'cleanup', 'beforeSave', 'afterSave', 'beforeValidate', 'onError') as $name) {
			$this->assertArrayNotHasKey($name, $methods, $name);
		}
	}

	public function testMappedMethodIsCalledWithoutTheModelParameter() {
		$this->assertSame('hello-world', $this->Page->slug('Hello World'));
		$this->assertSame('audit:x', $this->Page->audit('x'));
	}

	public function testFirstBehaviorKeepsADuplicateMethodName() {
		$methods = $this->Page->Behaviors->methods();
		$this->assertSame(array('Sluggable', 'slug'), $methods['slug']);
		$this->assertSame('hello-world', $this->Page->slug('Hello World'));
	}

	public function testModelOwnMethodWinsOverBehavior() {
		$this->assertArrayHasKey('hash', $this->Page->Behaviors->methods());
		$this->assertSame('model:x', $this->Page->hash('x'));
	}

	public function testBehaviorsLoadAttachesAtRuntime() {
		$this->assertFalse($this->Page->Behaviors->hasMethod('recover'));
		$this->Page->Behaviors->load('Tree');
		$this->assertTrue($this->Page->Behaviors->hasMethod('recover'));
		$this->assertInstanceOf('TreeBehavior', $this->Page->Behaviors->Tree);
	}

	public function testClassNameOptionKeepsAliasAndLoadsClass() {
		$this->Page->Behaviors->load('Trail', array('className' => 'Audit'));
		$this->assertContains('Trail', $this->Page->Behaviors->loaded());
		$this->assertInstanceOf('AuditBehavior', $this->Page->Behaviors->Trail);
	}

	public function testClassNameOptionKeepsADottedFirstArgumentAsTheAlias() {
		$this->Page->Behaviors->load('Probe.Custom', array('className' => 'Audit'));
		$this->assertContains('Probe.Custom', $this->Page->Behaviors->loaded());
		$this->assertInstanceOf('AuditBehavior', $this->Page->Behaviors->{'Probe.Custom'});
	}

	public function testClassNameEqualToTheFirstArgument() {
		$this->Page->Behaviors->load('Audit', array('className' => 'Audit'));
		$this->Page->Behaviors->load('Probe.Hashable', array('className' => 'Probe.Hashable'));
		$this->assertContains('Probe.Hashable', $this->Page->Behaviors->loaded());
		$this->assertInstanceOf('HashableBehavior', $this->Page->Behaviors->{'Probe.Hashable'});
	}

	public function testPluginNameOfAnUnloadedClassDoesNotFallBackToAppBehavior() {
		$this->assertFalse(class_exists('StampableBehavior', false));
		$note = ClassRegistry::init('Note');
		try {
			$note->Behaviors->load('Probe.Stampable');
			$this->fail('Expected MissingBehaviorException');
		} catch (MissingBehaviorException $e) {
			$this->assertSame(array('Baseline'), $note->Behaviors->loaded());
		}
	}

	public function testBareNameOfAnUnloadedClassDoesNotReachPluginBehavior() {
		$this->assertFalse(class_exists('TrackableBehavior', false));
		$note = ClassRegistry::init('Note');
		try {
			$note->Behaviors->load('Trackable');
			$this->fail('Expected MissingBehaviorException');
		} catch (MissingBehaviorException $e) {
			$this->assertSame(array('Baseline'), $note->Behaviors->loaded());
		}
	}

	public function testAnyNameOfALoadedClassUsesItWhateverItsOrigin() {
		$this->assertTrue(class_exists('AuditBehavior', false));
		$this->assertTrue(class_exists('HashableBehavior', false));
		$note = ClassRegistry::init('Note');
		$note->Behaviors->load('Probe.Audit');
		$note->Behaviors->load('Hashable');
		$this->assertSame(array('Baseline', 'Audit', 'Hashable'), $note->Behaviors->loaded());
		$this->assertInstanceOf('AuditBehavior', $note->Behaviors->Audit);
		$this->assertInstanceOf('HashableBehavior', $note->Behaviors->Hashable);
		$this->assertSame($this->Page->Behaviors->Audit, $note->Behaviors->Audit);
	}

	public function testActsAsMergesWithTheDirectParentModel() {
		$subPage = ClassRegistry::init('SubPage');
		$this->assertSame(
			array('Baseline', 'Containable', 'Sluggable', 'Audit', 'Hashable', 'Labelable'),
			$subPage->Behaviors->loaded()
		);
	}

	public function testActsAsDoesNotMergeWithAGrandparentModel() {
		$subSubPage = ClassRegistry::init('SubSubPage');
		$this->assertSame(array('Baseline', 'Labelable', 'Taggable'), $subSubPage->Behaviors->loaded());
	}

	public function testMappedMethodsIncludeTraitAndStaticMethods() {
		$subPage = ClassRegistry::init('SubPage');
		$methods = $subPage->Behaviors->methods();
		$this->assertSame(array('Labelable', 'label'), $methods['label']);
		$this->assertSame(array('Labelable', 'build'), $methods['build']);
		$this->assertArrayNotHasKey('hidden', $methods);
		$this->assertSame('label', $subPage->label());
	}

	public function testMappedMethodsIncludeAParentBehaviorsMethods() {
		$note = ClassRegistry::init('Note');
		$note->Behaviors->load('ChildSluggable');
		$methods = $note->Behaviors->methods();
		$this->assertSame(array('ChildSluggable', 'slug'), $methods['slug']);
		$this->assertSame(array('ChildSluggable', 'reslug'), $methods['reslug']);
		$this->assertArrayNotHasKey('beforeSave', $methods);
		$this->assertSame('hello-world-2', $note->reslug('Hello World'));
	}

	public function testActsAsMergesWithAppModel() {
		$note = ClassRegistry::init('Note');
		$this->assertSame(array('Baseline'), $note->Behaviors->loaded());
		$this->assertSame('baseline', $note->baseline());
		$this->assertSame('baseline', $this->Page->baseline());
	}

}
