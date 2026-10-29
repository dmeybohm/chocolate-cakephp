<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    use Cake\ORM\Locator\LocatorAwareTrait;

    class Controller {
        use LocatorAwareTrait;
        use ViewVarsTrait;

        public function initialize(): void {}

        public function loadComponent(string $name, array $config = []): Component {
            return new Component();
        }
    }
    class Component {}
}

namespace Cake\Controller\Component {
    class FlashComponent extends \Cake\Controller\Component {
        public function success(string $message): void {}
    }
}

namespace Cake\View {
    class View {
        use CellTrait;

        public function initialize(): void {}

        public function loadHelper(string $name, array $config = []): Helper {
            return new Helper();
        }

        public function addHelper(string $helper, array $options = []) {
            return $this;
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
        protected function cell(string $cell, array $data = [], array $options = []): Cell {
            return new Cell();
        }
    }
    abstract class Cell {
        use ViewVarsTrait;

        public function initialize(): void {}

        public function render(?string $template = null): string {
            return '';
        }
    }
    trait ViewVarsTrait {
        /**
         * @return ViewBuilder
         */
        public function viewBuilder(): ViewBuilder {
            return new ViewBuilder();
        }

        /**
         * @param array|string $name
         * @param mixed $value
         * @return $this
         */
        public function set(array|string $name, mixed $value = null) {
            return $this;
        }
    }
}


namespace Cake\ORM\Query {
    class SelectQuery {
        /**
         * @param string $finder
         * @param mixed ...$args
         * @return static
         */
        public function find(string $finder, mixed ... $args): static {
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
        public function where(array|string|null $conditions = null, array $types = [], bool $overwrite = false): static {
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
}

namespace Cake\ORM {
    use Cake\ORM\Query\SelectQuery;

    class RulesChecker {}
    class Table {
        public function initialize(array $config): void {}

        public function belongsTo(string $associated, array $options = []) {}
        public function hasOne(string $associated, array $options = []) {}
        public function hasMany(string $associated, array $options = []) {}
        public function belongsToMany(string $associated, array $options = []) {}
        public function addAssociations(array $params) {
            return $this;
        }
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
        public function getTableLocator(): LocatorInterface {
            return new TableLocator();
        }

        /**
         * @param string|null $alias
         * @param array $options
         * @return Table
         */
        public function fetchTable(?string $alias = null, array $options = []): Table {
            return new Table();
        }
    }
}