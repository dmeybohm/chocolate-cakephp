<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use ViewVarsTrait;
        use LocatorAwareTrait;

        public function initialize() {}

        public function loadComponent($name, array $config = []) {
            return new Component();
        }

        public function loadModel($modelClass = null, $modelType = null) {
            return new \Cake\ORM\Table();
        }
    }

    class Component {}
}

namespace Cake\Controller\Component {
    class FlashComponent extends \Cake\Controller\Component {
        public function success($message) {}
    }
}

namespace Cake\View {
    class View {
        use CellTrait;

        public function initialize() {}

        public function loadHelper($name, array $config = []) {
            return new Helper();
        }
    }
    class Helper {
        /**
         * @var \Cake\View\View
         */
        protected $_View;
    }
    class ViewBuilder {}
    trait CellTrait {
        protected function cell($cell, array $data = [], array $options = []) {
            return new Cell();
        }
    }
    abstract class Cell {
        use ViewVarsTrait;

        public function initialize() {}

        public function render($template = null) {
            return '';
        }
    }

    trait ViewVarsTrait {
        /**
         * @return ViewBuilder
         */
        public function viewBuilder() {
            return new ViewBuilder();
        }

        /**
         * @return $this
         */
        public function set($name, $value = null): void { }
    }
}


namespace Cake\ORM {
}

namespace Cake\ORM {

    class Query {
        /**
         * @param string $finder
         * @param array $options
         * @return $this
         */
        public function find($finder, $options = []) {
            return $this;
        }

        /**
         * @return array
         */
        public function toArray() {
            return [];
        }

        /**
         * @param array|string|null $conditions
         * @param array $types
         * @param bool $overwrite
         * @return $this
         */
        public function where($conditions = null, $types = [], $overwrite = false) {
            return $this;
        }

        /**
         * @param string|array $associations
         * @return static
         */
        public function contain($associations) {
            return $this;
        }
    }

    class RulesChecker {}

    class Table {
        public function initialize(array $config) {}

        public function belongsTo($associated, array $options = []) {}
        public function hasOne($associated, array $options = []) {}
        public function hasMany($associated, array $options = []) {}
        public function belongsToMany($associated, array $options = []) {}
        public function addAssociations(array $params) {
            return $this;
        }
        /**
         * @return Query
         */
        public function findThreaded() {
            return new Query();
        }
        /**
         * @return Query
         */
        public function findList() {
            return new Query();
        }
        /**
         * @return Query
         */
        public function findAll() {
            return new Query();
        }
        /**
         * @param string $type
         * @param array $options
         * @return Query
         */
        public function find($type = 'all', $options = []) {
            return new Query();
        }
    }

    class TableRegistry {
        /**
         * @return \Cake\ORM\Locator\LocatorInterface
         */
        public function getTableLocator() { }

        /**
         * @param string $alias
         * @param array $options
         * @return \Cake\ORM\Table
         * @deprecated 3.6.0 Use \Cake\ORM\Locator\TableLocator::get() instead
         */
        public function get($alias, array $options = [])
        {}
    }
}

namespace Cake\Database {
    abstract class Query {
        /**
         * @param array|string|null $conditions
         * @param array $types
         * @param bool $overwrite
         * @return $this
         */
         public function where($conditions = null, $types = [], $overwrite = false) {
             return $this;
         }
    }
}

namespace Cake\Validation {
    class Validator {}
}

namespace Cake\ORM\Locator {

    interface LocatorInterface {
        /**
         * @param string $alias
         * @return \Cake\ORM\Table
         */
        public function get($alias, array $options = []);
    }

    class TableLocator implements LocatorInterface {
        /**
         * @param string $alias
         * @return \Cake\ORM\Table
         */
        public function get($alias, array $options = []) {
            return new Table();
        }
    }

    trait LocatorAwareTrait {
        /**
         * @return \Cake\ORM\Locator\LocatorInterface
         */
        public function getTableLocator() {
            return new TableLocator;
        }
    }

}
