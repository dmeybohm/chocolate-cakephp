<?php

namespace App\Controller;

use Cake\Controller\Controller;

class MovieController extends Controller
{
	/** @var string */
	public $statusMessage = "Ready";

	public function artist() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$this->set(compact('metadata'));
	}

	public function filmDirector() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->getTableLocator()->get('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function meta() {

    }

	public function paramTest(int $movieId) {
		$this->set(compact('movieId'));
	}

	public function literalTest() {
		$this->set('title', 'Test Movie');
		$this->set('count', 42);
	}

	public function directCallTest() {
		// Direct expression - requires SourceKind.EXPRESSION resolution
		// Note: Chained method calls are resolved by finding the outermost expression
		$this->set('moviesTable', $this->getTableLocator()->get('Movies'));
		// Add a second variable to avoid auto-completion in tests
		$this->set('title', 'Direct Call Test');
	}

	public function expressionVarietyTest() {
		// Property access
		$this->set('message', $this->statusMessage);

		// Nested function calls
		$this->set('cleaned', str_replace('World', 'PHP', "Hello World"));

		// Variable reference (with typed local variable)
		/** @var int */
		$count = 42;
		$this->set('total', $count);

		// Array access
		$data = ['key' => 'value', 'num' => 123];
		$this->set('item', $data['key']);
	}

	public function arrayVarietyTest() {
		// Single variable in array syntax (property access expression)
		$this->set(['singleVar' => $this->statusMessage]);

		// Mixed literals and variables in array syntax
		/** @var int */
		$count = 42;
		$this->set(['title' => 'Test Title', 'count' => 42, 'total' => $count]);
	}

	public function viewBuilderTest() {
		$this->viewBuilder()->setTemplate('artist');
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->getTableLocator()->get('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function viewBuilderWithPathTest() {
		$this->viewBuilder()->setTemplatePath('Movie/Nested');
		$this->viewBuilder()->setTemplate('custom');
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->getTableLocator()->get('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function multipleSetTemplatePathTest() {
		// First path and template
		$this->viewBuilder()->setTemplatePath('Movie/Nested');
		$this->viewBuilder()->setTemplate('custom');

		// Change path and use different template
		$this->viewBuilder()->setTemplatePath('Movie/AnotherPath');
		$this->viewBuilder()->setTemplate('different');

		$metadata = $this->MovieMetadata->generateMetadata();
		$this->set(compact('metadata'));
	}

}
