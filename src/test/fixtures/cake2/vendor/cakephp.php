<?php

class App {
    /**
     * @param string $className
     * @param string $location
     * @return void
     */
    public static function uses($className, $location) {}
}

class Controller {

    public $uses = true;

    public $components = array('Session', 'Flash');

    public $helpers = array();

    public $layout = 'default';

    public function __get() {}

    public function set($keyOrArray, $value = null) {}
}

class Model {

    public $belongsTo = array();

    public $hasOne = array();

    public $hasMany = array();

    public $hasAndBelongsToMany = array();

    /**
     * @param string $type
     * @param array $query
     * @return array|int|null
     */
    public function find($type = 'first', $query = array()) {

    }

    /**
     * @param string $id
     * @return array
     */
    public function findById($id) {

    }
}

class Component {

}

class View {

    public $helpers = array();
}

class Helper {

    public $helpers = array();
}

class ClassRegistry {
    /**
     * @param string $className
     * @return Model
     */
    public static function init($className) { return new Model(); }
}
