<?php

namespace App\Controller;

use Cake\Controller\Controller;

class MovieController extends Controller
{
	public function artist() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function filmDirector() {
		$this->Auth->allow();
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function meta() {

    }

	public function viewBuilderTest() {
		$this->viewBuilder()->setTemplate('artist');
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

	public function viewBuilderWithPathTest() {
		$this->viewBuilder()->setTemplatePath('Movie/Nested');
		$this->viewBuilder()->setTemplate('custom');
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
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

	public function chainedViewBuilderTest() {
		// Chained setTemplatePath and setTemplate
		$this->viewBuilder()->setTemplatePath('Movie/Nested')->setTemplate('custom');
		$metadata = $this->MovieMetadata->generateMetadata();
		$moviesTable = $this->fetchTable('Movies');
		$this->set(compact('metadata', 'moviesTable'));
	}

}
