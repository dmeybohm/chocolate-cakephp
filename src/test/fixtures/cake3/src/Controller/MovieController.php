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

}
