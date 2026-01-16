<?php

namespace Cake\Controller {
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use LocatorAwareTrait;
    }

    class Component {}
}

namespace Cake\View {
    class View {}
    class Helper {}
}

namespace Cake\Validation {
    class Validator {}
}

namespace Cake\ORM {
    class RulesChecker {}
    class Table {
        /**
         * @return Query
         */
        public function findThreaded(): Query {
            return new Query();
        }
        /**
         * @return Query
         */
        public function findList(): Query {
            return new Query();
        }
        /**
         * @return Query
         */
        public function findAll(): Query {
            return new Query();
        }
        /**
         * @param string $type
         * @param array $options
         * @return Query
         */
        public function find(string $type = 'all', array $options = []): Query {
            return new Query();
        }
    }
    class Query {
        /**
         * @param string $finder
         * @param array $options
         * @return static
         */
        public function find(string $finder, array $options = []) {
            return $this;
        }

        /**
         * @return array
         */
        public function toArray(): array {
            return [];
        }

        /**
         * @param array|string|null $conditions
         * @param array $types
         * @param bool $overwrite
         * @return static
         */
        public function where($conditions = null, array $types = [], bool $overwrite = false) {
            return $this;
        }
    }

    class TableRegistry {
        /**
         * @return \Cake\ORM\Locator\LocatorInterface
         */
        public function getTableLocator() { }
    }
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
         * @param string|null $alias
         * @param array $options
         * @return \Cake\ORM\Table
         */
        public function fetchTable(?string $alias = null, array $options = []): \Cake\ORM\Table {
            return new Table();
        }

        /**
         * @return LocatorInterface
         */
        public function getTableLocator(): LocatorInterface {
            return new TableLocator;
        }
    }

}
