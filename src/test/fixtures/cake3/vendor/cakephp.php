<?php

namespace Cake\Controller {
    use Cake\View\ViewVarsTrait;
    class Controller {
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
    class SelectQuery {}
}
namespace Cake\ORM {
    class RulesChecker {}
    class Table {}
}
namespace Cake\Validation {
    class Validator {}
}
