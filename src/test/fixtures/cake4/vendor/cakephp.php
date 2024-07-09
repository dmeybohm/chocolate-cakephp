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

namespace Cake\ORM\Query {
    class SelectQuery {}
}
namespace Cake\ORM {
    class RulesChecker {}
    class Table {}
}
namespace Cake\Validation {
    class Validator {}
}

namespace Cake\ORM {
    class Table {
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
