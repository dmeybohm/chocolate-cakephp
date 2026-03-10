# Chocolate CakePHP - Feature Ideas

Prioritized list of feature ideas for the Chocolate CakePHP plugin, organized by
impact and implementation feasibility.

---

## Priority 1 (High Impact)

### 1. Entity Virtual Fields / Accessor Support

**What it does:** Provide completion and navigation for virtual fields defined via
`_get*` accessor methods on Entity classes. When accessing `$entity->full_name`,
resolve it to the `_getFullName()` method and infer the return type.

**Why it matters:** Virtual fields are heavily used in CakePHP and are invisible to
PhpStorm's static analysis. Developers constantly get false "unknown property"
warnings and lose navigation/completion for these fields.

**CakePHP APIs involved:**
- `Cake\ORM\Entity` (CakePHP 3/4/5)
- `_get{PropertyName}()` accessor convention
- `_set{PropertyName}()` mutator convention
- `$_virtual` property for JSON serialization

**Implementation notes:** Parse entity classes for `_get*`/`_set*` methods, convert
method names to snake_case property names, and register them as dynamic properties
with the accessor's return type. A `TypeProvider` extension point would handle type
inference, while a `CompletionContributor` would add them to property completions.

---

### 2. Behavior Support (Completion + Navigation + Type Inference)

**What it does:** Recognize behaviors attached via `$this->addBehavior('Timestamp')` in
Table classes. Provide completion for behavior methods called on the Table, navigate to
the behavior class, and resolve types from behavior-provided finders.

**Why it matters:** Behaviors are one of CakePHP's primary code-reuse mechanisms. Without
plugin support, methods mixed in from behaviors show as unresolved, and developers lose
IDE assistance for a major part of the framework.

**CakePHP APIs involved:**
- `Cake\ORM\Table::addBehavior()`
- `Cake\ORM\Behavior` base class
- `Cake\ORM\BehaviorRegistry`
- Built-in behaviors: `TimestampBehavior`, `TreeBehavior`, `CounterCacheBehavior`, `TranslateBehavior`

**Implementation notes:** Index `addBehavior()` calls in Table classes, resolve the
behavior class (convention: `{Name}Behavior` in `Model/Behavior/`), and expose its
public methods as available on the Table instance. This is similar to how component
support works for controllers. Custom finders defined in behaviors (e.g.,
`findThreaded()` from `TreeBehavior`) should also appear in `find()` completions.

---

### 3. Route Controller/Action String Completion

**What it does:** Complete controller and action names in routing configuration, e.g.,
`$routes->connect('/path', ['controller' => '<caret>', 'action' => '<caret>'])`.

**Why it matters:** Route definitions are a central part of every CakePHP application.
Typos in controller/action strings cause runtime errors that are hard to debug. IDE
completion prevents these mistakes and helps developers discover available endpoints.

**CakePHP APIs involved:**
- `Cake\Routing\RouteBuilder::connect()`
- `Cake\Routing\RouteBuilder::scope()` / `prefix()` / `plugin()`
- `Router::url()` reverse routing with array syntax
- `$this->Html->link()` and other helper URL generation

**Implementation notes:** For `'controller'` keys, scan for classes extending
`Cake\Controller\Controller` and offer their short names (without the `Controller`
suffix). For `'action'` keys, offer public methods on the resolved controller. Scope
and prefix contexts from the routing DSL should filter results appropriately.

---

### 4. Validation Field Name Completion

**What it does:** Complete field names when building validation rules in Table classes,
e.g., `$validator->requirePresence('<caret>')`.

**Why it matters:** Validation rules reference database columns by string name. Mistyped
field names silently create rules that never trigger, leading to subtle bugs.

**CakePHP APIs involved:**
- `Cake\Validation\Validator` methods: `requirePresence()`, `notEmptyString()`,
  `allowEmptyString()`, `add()`, `scalar()`, `maxLength()`, etc.
- `Table::validationDefault(Validator $validator)`
- Entity `$_accessible` property

**Implementation notes:** Resolve the Table class that owns the `validationDefault()`
method, look up its schema (from `$this->getSchema()` or the corresponding migration/SQL),
and offer column names as completions for the first argument of Validator methods. Could
also cross-reference with Entity `$_accessible` definitions.

---

### 5. Cell Support

**What it does:** Provide completion, navigation, and type inference for CakePHP Cell
classes — mini-controllers used to render reusable view components. Support `$this->cell()`
calls in views and controllers, resolve Cell class references, and provide template
navigation for Cell templates.

**Why it matters:** Cells are a core CakePHP feature (3+) for building reusable UI
components. They follow conventions similar to controllers (actions map to templates,
components can be loaded), but currently have no plugin support. Developers lose IDE
assistance for an important architectural pattern.

**CakePHP APIs involved:**
- `Cake\View\Cell` base class
- `$this->cell('CellName')` / `$this->cell('CellName::action')` in views and controllers
- Cell templates in `templates/cell/CellName/` (CakePHP 4/5) or `src/Template/Cell/` (CakePHP 3)
- `Cell::$validCellOptions` for constructor options

**Implementation notes:** This is a natural extension of existing controller/view support.
Index Cell classes (convention: `{Name}Cell` in `src/View/Cell/`), resolve `$this->cell()`
string arguments to Cell classes, and provide template navigation similar to controller
action → template navigation. Cell display methods should be treated like controller
actions for template resolution.

---

### 6. Configuration Code Disambiguation

**What it does:** Use context from configuration methods like `$this->initialize()` in
controllers to disambiguate dynamic property and method lookups, providing more accurate
type inference and completion.

**Why it matters:** CakePHP's `initialize()` method is where components, helpers, and
behaviors are configured. Overriding `initialize()` is extremely common — nearly every
controller, table, and view class does it. Understanding these configurations enables the
plugin to know which dynamic properties are available and what types they resolve to,
reducing false positives and improving completion accuracy.

**CakePHP APIs involved:**
- `Controller::initialize()` — `$this->loadComponent()` calls
- `Table::initialize()` — `$this->addBehavior()`, `$this->belongsTo()`, etc.
- `View::initialize()` — `$this->addHelper()` calls
- `AppController::initialize()` — inherited component loading

**Implementation notes:** This will likely require a stub indexer to index `initialize()`
methods and extract component/behavior/helper loading calls. Build a map of which dynamic
properties are available on each class based on its configuration. This needs to handle
inheritance (e.g., components loaded in `AppController` are available in all controllers).
This is complex because it requires dataflow analysis within the `initialize()` method,
but the indexing infrastructure would also benefit other features like behavior support
and warning suppression.

---

## Priority 2 (Medium Impact)

### 7. Configuration Key Completion

**What it does:** Complete configuration keys in `Configure::read('App.key')` and
`Configure::write()` calls, based on keys defined in config files.

**Why it matters:** Configuration is accessed by string keys throughout CakePHP
applications. Typos cause silent `null` returns. With many config files and nested keys,
remembering exact paths is error-prone.

**CakePHP APIs involved:**
- `Cake\Core\Configure::read()` / `write()` / `check()` / `delete()`
- `config/app.php`, `config/app_local.php`, and other config files
- `Configure::load()` for additional config sources

**Implementation notes:** Index array keys from files loaded via `Configure::load()` and
the default `config/app.php`. Build a trie/map of dotted key paths and offer them as
completions. Nested arrays should produce dot-separated suggestions (e.g., `App.encoding`,
`App.defaultLocale`).

---

### 8. Middleware Completion + Navigation

**What it does:** Complete middleware class names in `$middlewareQueue->add()` and provide
navigation from middleware strings to their class definitions.

**Why it matters:** Middleware is a key architectural layer in CakePHP 3.4+. String-based
middleware references are common, and navigating to their implementation speeds up
debugging request pipeline issues.

**CakePHP APIs involved:**
- `Cake\Http\MiddlewareQueue::add()` / `prepend()` / `insertAt()` / `insertBefore()` / `insertAfter()`
- `Application::middleware(MiddlewareQueue $middlewareQueue)`
- Built-in middleware: `AuthenticationMiddleware`, `CsrfProtectionMiddleware`, `BodyParserMiddleware`, etc.

**Implementation notes:** Scan for classes implementing `Psr\Http\Server\MiddlewareInterface`
or extending CakePHP middleware base classes. Provide completion in `add()` and related
methods. Navigation via Ctrl+Click on middleware class strings.

---

### 9. Event Listener Navigation

**What it does:** Navigate between event dispatching (`$this->dispatchEvent('Model.afterSave')`)
and event listener registrations (`$this->getEventManager()->on('Model.afterSave', ...)`
or `implementedEvents()` arrays).

**Why it matters:** CakePHP's event system is loosely coupled by design, making it hard
to trace which code handles a given event. IDE navigation bridges this gap and makes
event-driven code easier to understand.

**CakePHP APIs involved:**
- `Cake\Event\EventManager::on()` / `dispatch()`
- `Cake\Event\EventListenerInterface::implementedEvents()`
- `$this->dispatchEvent()` from various framework classes
- Convention-based event names: `Model.beforeSave`, `Controller.beforeRender`, etc.

**Implementation notes:** Index event name strings from `implementedEvents()` return arrays
and `on()` calls. Provide a line marker or gutter icon on `dispatchEvent()` calls that
navigates to listeners. A "Find Usages" provider for event names would also be valuable.

---

### 10. Request Data Field Completion

**What it does:** Complete field names in `$this->request->getData('field_name')` based
on form definitions in the corresponding view templates.

**Why it matters:** Request data fields are string-based references to form inputs. Typos
cause silent failures. Connecting controller code to view form fields improves
discoverability and reduces bugs.

**CakePHP APIs involved:**
- `Cake\Http\ServerRequest::getData()` / `getQuery()` / `getCookie()`
- `$this->Form->control('field_name')` in templates
- `$this->request->getData()` in controllers

**Implementation notes:** This is complex because it requires cross-referencing controller
actions with their templates and parsing `FormHelper` calls. A simpler first pass could
offer entity/table column names as completions, since form fields often map 1:1 to
database columns.

---

### 11. Dynamic Property/Method Warning Suppression

**What it does:** Suppress false "undefined property" and "undefined method" warnings on
CakePHP classes that use magic properties and methods — including Controllers, Cells,
View Helpers, and Views.

**Why it matters:** CakePHP heavily uses `__get()` and `__call()` magic to provide
convenient access to components, helpers, models, and other framework objects. PhpStorm
flags these as errors, creating noise that obscures real problems. The plugin already
suppresses view variable warnings, but doesn't cover other CakePHP magic property contexts.

**CakePHP APIs involved:**
- `Cake\Controller\Controller` — magic access to components, models, request
- `Cake\View\Cell` — magic access similar to controllers
- `Cake\View\Helper` — magic access to other helpers, view instance
- `Cake\View\View` — magic access to helpers

**Implementation notes:** Register a `PhpUndefinedFieldInspectionSuppressor` (or equivalent)
for classes extending the relevant CakePHP base classes. Cross-reference property accesses
against known components/helpers/models loaded in the class. Only suppress warnings for
properties that plausibly correspond to CakePHP magic — don't blanket-suppress all
undefined properties.

---

### 12. Undefined Method/Field Error Inspection

**What it does:** Provide a custom inspection that flags genuinely undefined methods and
fields on Controllers, Views, Components, and Helpers — complementing the warning
suppression above by catching real errors that the suppressor would otherwise hide.

**Why it matters:** Once magic property warnings are suppressed, there's a risk of hiding
real typos and errors. A custom inspection that understands CakePHP's conventions can flag
`$this->NonExistentComponent` or `$this->Helper->noSuchMethod()` while allowing legitimate
magic access.

**CakePHP APIs involved:**
- Component loading: `$this->loadComponent('ComponentName')`
- Helper loading: `$this->addHelper('HelperName')` / `$helpers` array
- Model associations and registry lookups
- `Cake\Controller\ComponentRegistry`, `Cake\View\HelperRegistry`

**Implementation notes:** Build on top of the warning suppression feature. When a property
access is suppressed, verify it against indexed component/helper/model registrations. If
no matching registration is found, flag it as a custom "Unknown CakePHP component/helper"
warning. This requires the indexing infrastructure from component and behavior support.

---

### 13. Quickfix to Add Missing Methods

**What it does:** When a controller action, view helper method, or component method is
called but doesn't exist, offer a quickfix to generate a stub method in the appropriate
class.

**Why it matters:** CakePHP developers frequently need to add new actions to controllers
or methods to components/helpers. A quickfix that creates the method with the correct
signature and in the right file saves time and reduces boilerplate.

**CakePHP APIs involved:**
- Controller action methods (public, no prefix)
- `Cake\View\Helper` subclass methods
- `Cake\Controller\Component` subclass methods
- `Cake\View\Cell` display methods

**Implementation notes:** Register an `IntentionAction` or `LocalQuickFix` that triggers
on unresolved method references in CakePHP contexts. Generate a method stub with
appropriate visibility, return type, and docblock. For controller actions, also offer to
create the corresponding template file.

---

### 14. Plugin Auto-Discovery

**What it does:** Automatically discover CakePHP plugins by reading the
`vendor/cakephp-plugins.php` file instead of requiring manual plugin configuration in
the IDE settings.

**Why it matters:** Currently, users must manually enter plugin names and paths in the
plugin's settings panel. This is tedious, error-prone, and easily gets out of sync with
the actual installed plugins. Auto-discovery makes the plugin work out of the box for
most projects.

**CakePHP APIs involved:**
- `vendor/cakephp-plugins.php` — auto-generated file listing all Composer-installed CakePHP plugins
- `Cake\Core\Plugin::getCollection()` — runtime plugin registry
- `composer.json` `extra.cakephp-plugins` section

**Implementation notes:** Parse `vendor/cakephp-plugins.php` (a PHP file returning an array
mapping plugin names to filesystem paths) on project open and when the file changes. Use
the discovered plugin paths to resolve plugin-prefixed template references, controller
namespaces, and model classes. Maintain backward compatibility with manual configuration
as an override.

---

## Priority 3 (Lower Impact / Higher Effort)

### 15. Bake/CLI Integration

**What it does:** Provide a UI within PhpStorm for running CakePHP's `bake` code
generation commands, with form fields for options and preview of generated files.

**Why it matters:** Bake is CakePHP's scaffolding tool and is frequently used. A UI
integration would make it more discoverable and reduce command-line context switching.

**CakePHP APIs involved:**
- `bin/cake bake` commands: `model`, `controller`, `template`, `migration`, etc.
- `Cake\Command\Command` base class
- Bake template files (`.twig` or `.php`)

**Implementation notes:** This would require a Run Configuration or Tool Window
integration. Parse available bake commands and their options to build dynamic forms.
Significant UI work required. Consider whether this adds enough value over the terminal.

---

### 16. Migration Support

**What it does:** Provide completion and navigation in Phinx/CakePHP migration files,
including table and column name completion in migration API calls.

**Why it matters:** Migrations define database schema changes. Column name typos in
migrations cause deployment failures. Cross-referencing migrations with model schema
helps maintain consistency.

**CakePHP APIs involved:**
- `Phinx\Migration\AbstractMigration` (CakePHP 3/4) / `Cake\Migration\AbstractMigration` (CakePHP 5)
- `$table->addColumn()`, `renameColumn()`, `removeColumn()`, `addIndex()`, etc.
- `$this->table('table_name')` table references

**Implementation notes:** Index existing migrations to build a schema model. Offer table
names in `$this->table()` calls and column names in column-related methods. Could also
detect schema inconsistencies (e.g., removing a column that doesn't exist). Depends on
parsing migration history in order, which adds complexity.

---

### 17. Plugin Route Prefix Awareness

**What it does:** Understand plugin routing prefixes and provide correct completions and
navigation for plugin controllers and actions within their routing scope.

**Why it matters:** CakePHP plugins define their own routing namespaces. Without prefix
awareness, route completions may suggest incorrect controllers or miss plugin-scoped
controllers entirely.

**CakePHP APIs involved:**
- `Cake\Routing\RouteBuilder::plugin()`
- `$routes->plugin('PluginName', ...)` scope
- `['plugin' => 'PluginName', 'controller' => '...']` array syntax
- `Plugin::routes()` and plugin `routes.php` files

**Implementation notes:** Extends the Route Controller/Action completion (feature #3)
with plugin context. When inside a `$routes->plugin()` scope, limit controller
completions to that plugin's namespace. When `'plugin'` key is present in a route
array, resolve controllers from the plugin's namespace. Requires understanding the
plugin loading mechanism and namespace mapping.

---

### 18. Enhanced `.phpstorm.meta.php`

**What it does:** Expand the existing `.phpstorm.meta.php` file (currently only covers
CakePHP 2) to cover CakePHP 3-5 APIs using `expectedArguments()` and `registerArgumentsSet()`
for improved completion in framework method calls.

**Why it matters:** PhpStorm's meta file mechanism provides completion for string arguments
without requiring a full plugin implementation. This is a lower-effort way to improve the
developer experience for common CakePHP API calls that accept string arguments with known
valid values.

**CakePHP APIs involved:**
- `TableLocator::get()` / `$this->fetchTable()` — model/table name arguments
- `$this->Html->link()`, `$this->Form->control()` — helper method options
- `BreadcrumbsHelper` title and URL arguments
- `$this->loadComponent()`, `$this->addHelper()` — framework registry lookups

**Implementation notes:** Generate or maintain a `.phpstorm.meta.php` file with
`expectedArguments()` declarations for common CakePHP methods. This could be a static file
shipped with the plugin or dynamically generated based on the project's CakePHP version.
The meta file approach is complementary to full `CompletionContributor` implementations —
it provides basic completion with minimal effort.

