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
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('moviesTable', 'metadata'));
	}

	public function meta() {

    }

	public function directCallTest() {
		// Direct expression - requires SourceKind.EXPRESSION resolution
		$this->set('moviesTable', $this->fetchTable('Movies'));
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

	public function variableArrayTest() {
		// VARIABLE_ARRAY pattern: $this->set($vars) where $vars = [...]
		// Tests Phase 2 optimization - extracting keys from variable assignment
		$vars = [
			'movie' => 'Inception',
			'director' => 'Christopher Nolan',
			'year' => 2010
		];
		$this->set($vars);
	}

	public function variableCompactTest() {
		// VARIABLE_COMPACT pattern: $this->set($vars) where $vars = compact(...)
		// Tests Phase 3 optimization - extracting parameters from compact() call
		$genre = 'Sci-Fi';
		$rating = 8.8;
		$vars = compact('genre', 'rating');
		$this->set($vars);
	}

	public function variablePairTest() {
		// VARIABLE_PAIR pattern: $this->set($key, $val) where $key = '...'
		// Tests Phase 4 optimization - extracting variable name from assignment
		$key = 'studio';
		$val = 'Warner Bros';
		$this->set($key, $val);
	}

	public function viewBuilderTest() {
		$this->viewBuilder()->setTemplate('artist');
		$metadata = $this->MovieMetadata->generateMetadata();
		$this->set(compact('metadata'));
	}

	public function viewBuilderWithPathTest() {
		$this->viewBuilder()->setTemplatePath('Movie/Nested');
		$this->viewBuilder()->setTemplate('custom');
		$metadata = $this->MovieMetadata->generateMetadata();
		$this->set(compact('metadata'));
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
