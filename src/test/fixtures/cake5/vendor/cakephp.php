<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use LocatorAwareTrait;
        use ViewVarsTrait;
    }
    class Component {}
}

namespace Cake\View {
    class View {}
    class Helper {}
    class ViewBuilder {}
    trait ViewVarsTrait {
        public function viewBuilder(): ViewBuilder {
            return new ViewBuilder();
        }

        /**
         * @return $this
         */
        public function set($name, $value = null): void { }
    }
}


namespace Cake\ORM\Query {
    class SelectQuery {
        public function find(string $finder, mixed ... $args): static {
            return new SelectQuery();
        }

        public function toArray(): array {
            return [];
        }

        public function where(): static {
        }
    }
}

namespace Cake\ORM {
    use Cake\ORM\Query\SelectQuery;

    class RulesChecker {}
    class Table {
        public function findThreaded(): SelectQuery {
            return new SelectQuery();
        }
        public function findList(): SelectQuery {
            return new SelectQuery();
        }
        public function findAll(): SelectQuery {
            return new SelectQuery();
        }
        /**
         * @param string $type
         */
        public function find(string $type = 'all', ... $args): SelectQuery {
            return new SelectQuery();
        }
    }

    class TableRegistry {
        /**
         * @return \Cake\ORM\Locator\LocatorInterface
         */
        public function getTableLocator() {}
    }
}
namespace Cake\ORM\Query {
    class SelectQuery extends \Cake\Database\SelectQuery {}
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
    class SelectQuery extends \Cake\Database\Query {}
}
namespace Cake\Validation {
    class Validator {}
}

namespace Cake\ORM\Locator {
    use Cake\ORM\Table;

    interface LocatorInterface {

        /**
         * Get a table instance from the registry.
         * @param string $alias
         * @return \Cake\ORM\Table
         */
        public function get($alias, array $options = []);
    }

    trait LocatorAwareTrait {


        /**
         * @return \Cake\ORM\Locator\LocatorInterface
         */
        public function getTableLocator() {}

        public function fetchTable(?string $alias = null, array $optoins = []): Table {}
    }
}