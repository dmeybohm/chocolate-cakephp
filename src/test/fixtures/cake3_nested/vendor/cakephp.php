<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use ViewVarsTrait;
        use LocatorAwareTrait;
    }

    class Component {}
}

namespace Cake\View {
    class View {}
    class Helper {}
    class ViewBuilder {}

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
    }

    class RulesChecker {}

    class Table {
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
