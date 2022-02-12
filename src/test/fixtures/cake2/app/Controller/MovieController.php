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

}
