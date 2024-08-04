<?php

namespace App\Controller;

use Cake\Controller\Controller;

class MyNestedController extends Controller
{
    public $components = ['MovieMetadata'];

	public function someNestedAction() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('moviesTable', 'metadata'));
	}

	public function anotherNestedAction() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('moviesTable', 'metadata'));
	}

	public function meta() {

    }
}
