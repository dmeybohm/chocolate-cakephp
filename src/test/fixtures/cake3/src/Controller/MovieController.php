<?php

namespace App\Controller;

use Cake\Controller\Controller;

class MovieController extends Controller
{
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
		// Direct CALL - requires SourceKind.CALL resolution
		// Note: Chained calls resolve to the first method's return type (getTableLocator)
		// due to AST offset pointing to the start of the expression
		$this->set('moviesTable', $this->getTableLocator()->get('Movies'));
		// Add a second variable to avoid auto-completion in tests
		$this->set('title', 'Direct Call Test');
	}

}
