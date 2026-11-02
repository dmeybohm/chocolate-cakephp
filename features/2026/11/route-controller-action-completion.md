# Route controller and action string completion

## Context

Feature 3 in `features/ideas.md`. A route target is an array with `controller`, `action` and
optionally `plugin` and `prefix` keys, and the same array shape names a URL everywhere else in a
CakePHP app: `Router::url()`, `$this->Html->link()`, `$this->redirect()`, and so on. Today every
one of those strings is opaque to the plugin: no completion, no navigation, and a typo is only
found at runtime.

The plugin already enumerates classes by parent (`getCellSubclasses`, `getComponentSubclasses`
in `Classes.kt`), completes a string argument from a class list (`CellCompletionContributor`),
navigates from a string argument to a class or method (`CellGotoDeclarationHandler`), and knows
the app and plugin namespaces (`cellNamespaces`, `controllerTypeFromControllerName`). This feature
adds the routing-array shape on top of those pieces.

Feature 21 in `features/ideas.md` (plugin-aware route completion) is folded in here, since the
`plugin()` scope and the `plugin` key are part of the same resolution and leaving them out would
offer the wrong controllers inside a plugin's routes file.

## How CakePHP resolves a route target

Pinned by `RouteTargetTest` in `src/test/php` on 3.10, 4.6 and 5.4 (see the catalogue in
`src/test/php/README.md`). Session #1 below lists where the first reading of the sources was wrong.

- A target array has string keys `plugin`, `prefix`, `controller`, `action`, plus `_name`,
  `_method`, `_ext`, `?`, `#` and positional passed arguments. Only the four named keys matter
  here.
- `controller` is the class's short name without the `Controller` suffix, CamelCase:
  `'controller' => 'Articles'` is `App\Controller\ArticlesController`. The controller factory
  does not inflect it and refuses a name starting in lower case, but `DashedRoute` (the
  application skeleton's route class) camelizes the `controller` and `action` params of every
  route it parses, connect() defaults included, so `'controller' => 'articles'` and
  `'action' => 'view_all'` dispatch on a dashed route and fail on a plain `Route`. The plugin
  offers the class's own spelling and may resolve the inflected spellings for navigation.
- `plugin` is the plugin's CamelCase name, `'plugin' => 'DebugKit'`, or `'Acme/Vendored'` for
  a vendor-namespaced plugin, and selects the plugin's `Controller` namespace in place of the
  app's. Inside a `plugin()` scope a `connect()` target
  may not name another plugin, and `'plugin' => false` is rejected the same way
  (`BadMethodCallException`); `'plugin' => null` connects an app route.
- `prefix` selects a sub-namespace under `Controller`. CakePHP 4 and 5 want it CamelCase,
  `'prefix' => 'Admin'`, and nested prefixes are slash-joined, `'Admin/Reports'`, mapping to
  `App\Controller\Admin\Reports\...`. CakePHP 3 underscores it: `RouteBuilder::prefix()`
  calls `Inflector::underscore()`, so the key is `'admin'` and the factory camelizes each
  segment back, so `'Admin'` dispatches on 3 as well. CakePHP 4 still camelizes a lower case
  prefix, with a deprecation; CakePHP 5 uses it verbatim, so `'admin'` is a missing controller.
  `'prefix' => null` inside a prefix scope connects the unprefixed controller on every version;
  `'prefix' => false` does the same on 3 and 4 and is rejected on 5.
- `action` is the method name as written, camelCase. An action is any public method, inherited
  from the app's own base classes or traits included, that `Cake\Controller\Controller` does
  not declare; 4 and 5 match it case-sensitively, 3 does not. The underscored spelling only works through `DashedRoute`.
- `RouteBuilder::scope()`, `prefix()` and `plugin()` each run a closure with a builder whose
  `_params` carry the `plugin` and `prefix` of every enclosing scope. `connect()` merges the
  target array over those params, so inside `$routes->prefix('Admin', function ($builder) {
  $builder->connect('/x', ['controller' => 'Reports', 'action' => 'index']); })` the target is
  `App\Controller\Admin\ReportsController`. `scope('/path', ['prefix' => 'Admin'], ...)` and
  `scope('/path', ['plugin' => 'X'], ...)` set the same params through the array argument.
  `prefix()` nests: `prefix('Admin', ...)` around `prefix('Reports', ...)` gives
  `'Admin/Reports'`.
- `connect()`'s defaults may also be a string, `'[Plugin.][Prefix/]Controller::action'`, on all
  three versions. CakePHP 3 lowercases the prefix part, matching its `prefix()` convention.
- `get()`, `post()`, `put()`, `patch()`, `delete()`, `head()` and `options()` take the same
  target as their second argument, as does `redirect()`. `resources('Articles')` takes a bare
  controller name as its first argument and connects the index/add/view/edit/delete actions to
  it; its `prefix` and `plugin` come from the enclosing scope and the `prefix` option.
- The static `Router::connect()`, `Router::scope()`, `Router::prefix()` and `Router::plugin()`
  exist in 3 and 4 (deprecated in 3.x, removed in 5) and take the same arguments.
- Away from the routes file, the array goes to `Router::url()`, `$this->Url->build()`,
  `$this->Html->link()`, `$this->Form->postLink()`, `$this->Form->create($x, ['url' => [...]])`
  and `Controller::redirect()`. A key left out of such an array is filled from the current
  request, so `['action' => 'edit']` in `ArticlesController` targets the same controller, and
  the request's `plugin` and `prefix` are kept unless the array has the key; `false` and
  `null` both leave them. With no `action` key the action is `index`, unless the `controller`
  key is the request's controller name, in which case the request's action is copied, even
  when a `plugin` key names another plugin. `Html->link()`, `Form->postLink()`,
  `Url->build()`, `Form->create()`'s `url` option and `Controller::redirect()` all build the
  array the same way. The `prefix` key is matched against the route case-sensitively, so
  only the version's own spelling builds a URL; on a dashed route the `plugin` and
  `controller` keys match in any case.

CakePHP 2 differs in every naming rule (see "CakePHP 2" below), which is why it is a separate
branch.

## Scope

In the order to implement them, each with cake5 tests first, then cake4 and cake3.

### 1. Assumption tests

Add `RouteTargetTest` to `src/test/php` for 3, 4 and 5, recording:

- `'controller' => 'Widgets'` dispatches to `WidgetsController`; `'controller' => 'widgets'`
  does not (the plugin should offer only the class's spelling).
- `'prefix' => 'Admin'` reaches `App\Controller\Admin\...` on 4 and 5; what `'admin'` does
  there, and what `'Admin'` does on 3 where `'admin'` is the convention.
- A nested prefix target, `'Admin/Reports'` on 4 and 5 and its 3.x spelling, reaches the nested
  namespace.
- `'plugin' => 'Probe'` reaches `Probe\Controller\...`; a `connect()` inside `plugin('Probe', ...)`
  with no `plugin` key reaches the plugin controller; `'plugin' => null` inside it reaches the
  app, and `'plugin' => false` or another plugin's name is rejected.
- A `connect()` inside `prefix('Admin', ...)` with no `prefix` key reaches the prefixed
  controller, and one inside `scope('/x', ['prefix' => 'Admin'], ...)` does too.
- `'action' => 'viewAll'` dispatches to `viewAll()`; whether `'view_all'` or `'view-all'` do,
  on `DashedRoute` and on the default route class. This decides whether to offer anything but
  the method's own spelling.
- `'Widgets::index'` string defaults connect the same target as the array.
- `Router::url(['action' => 'other'])` inside a request to `WidgetsController` builds the URL
  for `Widgets::other`, which lets the plugin default the controller from the enclosing class.
- `resources('Widgets')` under `prefix('Admin', ...)` connects `Admin/Widgets`.

Catalogue rows go in `src/test/php/README.md`. The fixtures need `Admin/WidgetsController` and
`Admin/Reports/...` controllers in the app and a `Things` controller in the Probe plugin, some of
which already exist in `src/test/php/cake5/src/Controller/Admin`.

### 2. Route target model (`cake/CakeRoutes.kt`)

- `RouteTarget(plugin: String?, prefix: String?, controller: String?, action: String?)` parsed
  from an `ArrayCreationExpression` whose keys are string literals. Non-literal values and
  unknown keys are ignored. A `null` value for `plugin` or `prefix`, or `false` for `prefix` on
  3 and 4, records an explicit "none", distinct from absent, so an inner key can cancel an
  outer scope. In a URL-building array `false` means the same as `null`.
- `RouteScope(plugin: String?, prefix: String?)` from the enclosing closures: walk up from the
  array through `Closure` → `ParameterList` → `MethodReference` named `prefix`, `plugin` or
  `scope` whose receiver is a `RouteBuilder` (instance, or the static `Router::` form on 3 and
  4), reading the first argument for `prefix()` and `plugin()` and the `prefix`/`plugin` keys of
  the array argument for `scope()`. Outer prefixes join inner ones with `/`. A closure passed by
  variable is not followed.
- `RouteContext(candidates: List<RouteContextController>)` for a URL-building array, standing
  in for the request the URL is built in. Each `RouteContextController(plugin, prefix,
  controller, action)` is one controller the array may run under: inside a controller class
  there is one, with the plugin from the file's location (`cakeThreePluginName`), the prefix
  from the namespace segments between `Controller` and the class, the class's short name, and
  the enclosing method's name as the action when the array is inside a controller action. In a
  view file there is one per rendering controller from `RenderingControllers`, each with an
  unknown action. Anywhere else the list is empty.
- `resolvedTargets = defaults + target`, one per candidate, where the defaults are the
  `RouteScope` in a route builder call and each `RouteContextController` in a URL-building
  call, and an explicit "none" in the target wins. A missing `controller` key takes the
  candidate's controller. A missing `action` key is, following the runtime rule, the
  candidate's action when the controller key is absent or names the candidate's controller,
  and `index` when it names another controller. The candidate's action may itself be unknown,
  as in a view file, in which case the action stays unknown rather than guessed. Action
  completion and navigation run against every resolved target and merge the results.
- Controller FQNs for a resolved target: `${namespace}\Controller\${prefixPath}\${Name}Controller`
  where the namespace is the app's, or the configured plugin's by name
  (`findPluginConfigByName`, `effectivePluginName`), and `prefixPath` is the prefix with `/`
  turned into `\`, camelized per segment on CakePHP 3 and 4 so an underscored `'admin'` still
  resolves, and used verbatim on 5.
  This extends `controllerTypeFromControllerName` rather than replacing it.
- The reverse, for completion: from a controller `PhpClass` under one of the known namespaces,
  its `(plugin, prefix, name)`, the same way `cellNameFromClassFqn` strips a namespace prefix.
  Classes not under `${namespace}\Controller\` are skipped.
- `PhpIndex.getControllerSubclasses(settings)` in `Classes.kt`, mirroring the other
  `get*Subclasses`, from `\Cake\Controller\Controller`, excluding abstract classes and classes
  whose short name is `AppController` or does not end in `Controller`.

### 3. Where the arrays are (`routing/RouteTargetPatterns.kt`)

One pattern object with the completion form (`LeafPsiElement` first) and the goto form
(`StringLiteralExpression` first), as `AssetMethodPatterns` does. A string literal qualifies
when it is the value of an `ArrayHashElement` whose key literal is `controller`, `action`,
`plugin` or `prefix`, inside an `ArrayCreationExpression` that is:

- the second argument of `connect`, `get`, `post`, `put`, `patch`, `delete`, `head`, `options`
  or `redirect` on a `RouteBuilder` receiver (resolved type contains `Cake\Routing\RouteBuilder`,
  or the receiver is a parameter of a closure passed to a `scope`/`prefix`/`plugin` call, since
  the closure parameter is often untyped), or on static `Router::`;
- the first argument of `Router::url()` or `Router::normalize()`, `$this->Url->build()`,
  `$this->redirect()` in a controller;
- the second argument of `$this->Html->link()` or `$this->Form->postLink()`;
- the `url` option of `$this->Form->create()`.

Separately, the whole string is a target when it is the first argument of `resources()`, or the
second argument of `connect()` (the `Controller::action` form).

Keys are matched structurally, not by name alone, so `['action' => 'delete']` in a form's
attribute array outside these calls is left alone. The method list is a single set so a later
feature can add to it.

### 4. Completion (`routing/RouteTargetCompletionContributor.kt`)

Gated on `settings.cake3Enabled` and `DumbService`.

- `controller`: every controller from `getControllerSubclasses` whose `(plugin, prefix)` matches
  the resolved scope and the sibling keys of the same array; the lookup string is the bare name,
  with the FQN as type text and the class icon. With no `plugin` or `prefix` key, the resolved
  defaults decide: the scope closures in a route builder call, and the enclosing controller's
  plugin and prefix in a URL-building call, so a link in a plugin controller's template offers
  that plugin's controllers. Only a route builder call outside any scope falls back to the
  app's controllers. A `plugin` key limits the list to that plugin.
- `action`: the public non-static methods of the controllers the resolved target names,
  inherited ones included, whose declaring class is not `Cake\Controller\Controller`, which is
  the rule `isAction()` applies. `PhpClass.methods` includes inherited methods and each knows
  its containing class, so the check is on the declaring FQN. `isCustomizableViewMethod` and
  `cakeSkipRenderingMethods` serve the template gutter markers and stay as they are. The method
  icon and the class as type text. When the array has no `controller` key, the controller comes
  from the resolved defaults above.
- `plugin`: configured plugin names (`cakeThreePluginConfigs`) plus directory names under the
  `plugins` directory.
- `prefix`: the distinct prefix paths of the known controllers, in the version's spelling.
- `resources()` first argument and the `Controller::action` string: the controller names, and
  after `::` the actions, as `CellCompletionContributor` does with `withPrefixMatcher`.

### 5. Navigation (`routing/RouteTargetGotoDeclarationHandler.kt`)

On the same string literals: `controller` goes to the class(es), `action` to the method on the
resolved controller(s), `plugin` to the plugin's `Controller` directory is out of scope, and
`prefix` to the prefix namespace directory under `Controller`. The `Controller::action` form
and `resources()` navigate the same way. Register both the contributor and the handler in
`plugin.xml`.

### 6. Tests

`cake5/RouteTargetCompletionTest` and `cake5/RouteTargetGotoDeclarationTest`, then the cake4
and cake3 copies. Fixtures:

- `cake5/config/routes.php` is only ever configured by text, so no file is needed, but a
  `cake5/src5/Controller/Admin/ReportsController.php` and
  `cake5/vendor/test/test_plugin/src/Controller/WidgetsController.php` are, so a prefix and a
  plugin each have one controller to find. The cake4 and cake3 trees get the same two files.
- Each completion test puts at least two controllers in play so the popup appears
  (`lookupElementStrings` is null when a single item is auto-inserted).

Cases, each for completion and for goto: controller at top level; controller inside
`prefix('Admin', ...)` offers only `Reports`; controller with `'prefix' => 'Admin'` in the
array; controller inside `plugin('TestPlugin', ...)` offers only `Widgets`; `'plugin' => null`
inside it offers app controllers; `'plugin' => false` or another plugin's name inside it offers
nothing, since the route is rejected at runtime; action after a controller key; action with no
controller key inside a controller class; `Router::url()` and `$this->Html->link()` in a
template, and in a plugin controller's template where they offer the plugin's controllers;
`resources()`; `'Movie::artist'` string defaults; nothing offered for an unrelated method's
array.

## CakePHP 2

CakePHP 2 is to be done on a follow-up branch, `route-completion-cake2`, after this one lands.
The seam is the model in step 2: the pattern, contributor and handler take a `RouteTarget` and a
list of candidate controllers from a per-version resolver, so the CakePHP 2 branch adds a
resolver and fixtures and changes little else.

What CakePHP 2 does differently (to be pinned by its own `RouteTargetTest` in
`src/test/php/cake2`):

- `Router::connect('/path', array('controller' => 'posts', 'action' => 'view'))` is static and
  lives in `app/Config/routes.php`. There are no scopes, so the enclosing-closure walk is not
  needed.
- `controller` is underscored, `'posts'`; the `Dispatcher` camelizes it to `PostsController`.
  `plugin` is underscored too, `'debug_kit'`.
- There is no `prefix` namespace. A prefix is a configured name in `Routing.prefixes`, set in
  the target as `'admin' => true` or `'prefix' => 'admin'`, and the action method is
  `admin_` + action. Offering `index` for `'action'` when the array has `'admin' => true` means
  listing methods that start with `admin_` and stripping the prefix.
- Controllers are global classes extending `AppController` (and `Controller`), found with the
  existing `\AppController` convention in `Classes.kt`; the `cake2AppDirectory` and
  `cakeTwoPluginName` tell app from plugin.
- `Router::url()`, `$this->Html->link()`, `$this->Form->postLink()`, `$this->redirect()` and
  `Router::redirect()` take the same array, and `Router::mapResources('posts')` takes a bare
  controller name like `resources()`.

Estimated at roughly a third of the CakePHP 3+ work: one resolver file, the `admin_` action
handling, the static-only receiver in the pattern, two fixture controllers, and the cake2 test
classes.

## Out of scope

- Named routes (`'_name' => ...`) and `{controller}`/`:controller` placeholders in route
  templates.
- Inspections flagging an unknown controller or action.
- URL strings (`'/articles/view'`) and `linkFromPath()`'s `Controller::action` path form, which
  could reuse the `Controller::action` parsing later.

## Implementation Progress

### Session #1

Wrote `RouteTargetTest` for CakePHP 3, 4 and 5 (36 tests each, all passing) with `Routed`
controllers in the app, under `Admin` and `Admin/Reports`, and in the Probe plugin, a shared
`RoutedActionsTrait` that reports the dispatched class and method, the request params and the
URL `Router::url()` builds, and a `/rt` route scope in each `config/routes.php` that connects
the targets under test ahead of the fallbacks. The catalogue rows are in `src/test/php/README.md`.

What the tests corrected in the first reading of the sources:

- `DashedRoute` camelizes the `controller` and `action` params of a route's own defaults when
  it parses a request, so lower case and underscored keys dispatch on the skeleton's route class;
  only a plain `Route` refuses them.
- `'plugin' => false` inside a `plugin()` scope is a `BadMethodCallException`, not a way to
  target the app; `null` is.
- `'prefix' => false` is rejected on 5 (`Route` asserts the type); `null` escapes a prefix scope
  on every version.
- Cake 4 still camelizes a lower case prefix on dispatch, with a deprecation; only 5 is strict.
- Cake 3 reports an app request's `plugin` param as `false`, not `null`.
- The route the URL builder picks is the first one whose name bucket matches, which made a
  test of `Router::url(['action' => 'view_all'])` depend on route order; it was dropped as it
  pinned the fixture rather than the framework.

Added a second round, 10 more tests per version, after the first results: the URL-taking
helper calls and `redirect()` build like `Router::url()` (which fixes step 3's method list),
how a missing `action` key defaults (step 4's enclosing-controller default has to use the
request's action, not `index`, when the controller names match), inherited and framework
methods as actions (step 4's action list is every public method not declared by
`Cake\Controller\Controller`), and vendor-namespaced plugin keys and scopes.

Next: step 2, the route target model and `getControllerSubclasses`.
