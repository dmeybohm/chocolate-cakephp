<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    use Cake\ORM\Locator\LocatorAwareTrait;
    use Cake\ORM\Table;

    class Controller {
        use ViewVarsTrait;
        use LocatorAwareTrait;

        /**
         * @param string|null $modelClass
         * @return \Cake\ORM\Table
         */
        public function loadModel($modelClass = null) {
            return new Table();
        }
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
        public function find(string $finder, mixed ... $args): static {
            return new Query();
        }

        public function toArray(): array {
            return [];
        }

        public function where(): static {
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
        public function findThreaded(): Query {
            return new Query();
        }
        public function findList(): Query {
            return new Query();
        }
        public function findAll(): Query {
            return new Query();
        }
        /**
         * @param string $type
         */
        public function find(string $type = 'all', ... $args): Query {
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
         * @return Query
         */
         public function where(
             array|string|null $conditions = null,
             array $types = [],
             bool $overwrite = false
         ) {
             return $this;
         }
    }
}

namespace Cake\Database\Query {
    class Query {}
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