<?php

/** @var array $movies */

// Element call with parameters - this should work for navigation
$this->element('Director/filmography', ['director' => $movies[0]->director]);

// Element call without parameters - this already works
$this->element('artist_element');
