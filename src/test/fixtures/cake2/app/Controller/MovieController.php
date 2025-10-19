<?php

class MovieController extends AppController
{
	public $uses = ['Movie', 'Artist'];
    public $components = ['MovieMetadata'];

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

	public function direct_call_test()
	{
	    // Direct CALL - requires SourceKind.CALL resolution
	    // Note: CakePHP 2 uses ClassRegistry::init() to get model instances
	    $this->set('movieModel', ClassRegistry::init('Movie'));
	    // Add a second variable to avoid auto-completion in tests
	    $this->set('title', 'Direct Call Test');
	}

}
