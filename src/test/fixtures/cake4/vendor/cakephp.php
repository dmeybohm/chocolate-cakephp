<?php

namespace Cake\Controller {
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use LocatorAwareTrait;
    }

    class Component {}
}

namespace Cake\Datasource {
    trait EntityTrait {
        /**
         * @return bool[]
         */
        public function getAccessible() {
            return ['foo' => true];
        }
    }
}

namespace Cake\View {
    class View {}
    class Helper {}
}

namespace Cake\Validation {
    class Validator {}
}

namespace Cake\ORM {
    use Cake\Datasource\EntityTrait;

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
    class Query {
        /**
         * @param string $type
         */
        public function find(string $type = 'all', ... $args): static {
            return new Query();
        }

        public function toArray(): array {
            return [];
        }

        public function where(): static {
        }
    }

    class Entity {
        use EntityTrait;
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
        public function fetchTable(): \Cake\ORM\Table {
        }

        public function getTableLocator(): LocatorInterface {
            return new TableLocator;
        }
    }

}
