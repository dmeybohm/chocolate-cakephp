<?php

/**
 * The connections the assumption tests use, all SQLite in memory.
 */
class DATABASE_CONFIG {

	public $default = array('datasource' => 'Database/Sqlite', 'database' => ':memory:');

	public $test = array('datasource' => 'Database/Sqlite', 'database' => ':memory:');

}
