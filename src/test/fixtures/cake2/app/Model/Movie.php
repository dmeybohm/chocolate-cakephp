<?php


class Movie extends AppModel
{
    public $hasMany = ['Artist'];
    public $belongsTo = ['Director'];

	public function saveScreening()
	{

	}
}