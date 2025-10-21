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

}
