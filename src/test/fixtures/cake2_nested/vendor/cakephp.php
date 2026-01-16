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

    public function __get() {}

    public function set($keyOrArray, $value = null) {}
}

class Model {

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

}

class ClassRegistry {
    /**
     * @param string $className
     * @return Model
     */
    public static function init($className) { return new Model(); }
}
