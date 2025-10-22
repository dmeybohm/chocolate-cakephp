<?php

class MovieController extends AppController
{
    public $uses = ['Movie', 'Artist'];
    public $components = ['MovieMetadata'];

	/** @var string */
	public $statusMessage = "Ready";

    public function artist($artistId)
    {
        $movies = $this->Movie->find('all', ['conditions' => ['artist_id' => $artistId]]);
        $artist = $this->Artist->find('first', ['conditions' => ['id' => $artistId]]);
        $metadata = $this->MovieMetadata->generateMovieMetadata($movies);

        ClassRegistry::init('Movie')->saveScreening();
        $this->set(compact('movies', 'artist', 'metadata'));
    }

    public function film_director($artistId)
    {
        $movies = $this->Movie->find('all', ['conditions' => ['artist_id' => $artistId]]);
        $artist = $this->Artist->find('first', ['conditions' => ['id' => $artistId]]);
        $metadata = $this->MovieMetadata->generateMovieMetadata($movies);

        $movieModel = ClassRegistry::init('Movie');
        ClassRegistry::init('Movie')->saveScreening();
        $this->set(compact('movies', 'artist', 'metadata', 'movieModel'));
    }

    public function viewFieldTest($artistId)
    {
        $this->view = 'artist';
        $movies = $this->Movie->find('all', ['conditions' => ['artist_id' => $artistId]]);
        $artist = $this->Artist->find('first', ['conditions' => ['id' => $artistId]]);
        $metadata = $this->MovieMetadata->generateMovieMetadata($movies);
        $movieModel = ClassRegistry::init('Movie');
        $this->set(compact('movies', 'artist', 'metadata', 'movieModel'));
    }

	public function direct_call_test()
	{
	    // Direct expression - requires SourceKind.EXPRESSION resolution
	    // Note: CakePHP 2 uses ClassRegistry::init() to get model instances
	    $this->set('movieModel', ClassRegistry::init('Movie'));
	    // Add a second variable to avoid auto-completion in tests
	    $this->set('title', 'Direct Call Test');
	}

	public function expression_variety_test()
	{
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

	public function array_variety_test()
	{
		// Single variable in array syntax (property access expression)
		$this->set(['singleVar' => $this->statusMessage]);

		// Mixed literals and variables in array syntax
		/** @var int */
		$count = 42;
		$this->set(['title' => 'Test Title', 'count' => 42, 'total' => $count]);
	}

}
