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
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('moviesTable', 'metadata'));
	}

	public function meta() {

    }

	public function directCallTest() {
		// Direct CALL - requires SourceKind.CALL resolution
		$this->set('moviesTable', $this->fetchTable('Movies'));
		// Add a second variable to avoid auto-completion in tests
		$this->set('title', 'Direct Call Test');
	}

}
