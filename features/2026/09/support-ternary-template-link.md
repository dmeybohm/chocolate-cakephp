# Support Ternary and Match Expressions When Setting Templates

GitHub issue: https://github.com/dmeybohm/chocolate-cakephp/issues/280

## Problem

Controllers frequently choose a template conditionally:

```php
// CakePHP 3, 4, 5
$this->viewBuilder()->setTemplate($condition ? 'view1' : 'view2');
$this->render($condition ? 'view1' : 'view2');

// CakePHP 2
$this->view = $condition ? 'view1' : 'view2';
$this->render($condition ? 'view1' : 'view2');

// PHP 8+
$this->viewBuilder()->setTemplate(match ($type) {
    'a' => 'view1',
    default => 'view2',
});
```

Today only a bare string literal is recognized. Everything that depends on
knowing which template a controller renders silently stops working for these
forms:

- Go-to-declaration on the template name (`TemplateGotoDeclarationHandler`).
- The gutter line markers on `$this->render()`, `$this->viewBuilder()` and
  `$this->view` (`ControllerMethodLineMarker`).
- The method-level gutter marker that lists every view a method renders.
- The view file index (`ViewFileDataIndexer`), which drives navigation from a
  view back to its controller methods and the controller-to-view variable
  completion / type inference.

The AST indexer is also slightly wrong today: `extractStringLiteral` never
returns null, so a non-literal argument such as a ternary is indexed under its
raw source text (for example `Movie/$c ? 'a' : 'b'`).

## Design

The core idea is the same everywhere: a template argument is no longer a single
string, it is a *set of possible strings*. One small function per layer turns an
expression into that set, and every existing consumer is generalized from
"one name" to "a list of names".

Supported expression shapes (recursively, so they nest):

| Expression | Values collected |
|---|---|
| `'literal'` | the literal |
| `$c ? A : B` | values of `A` plus values of `B` (never the condition) |
| `$c ?: B` | values of `B` |
| `match (...) { x => A, default => B }` | values of every arm body (never the arm conditions) |
| `( A )` | values of `A` |
| anything else | nothing |

### AST layer (index)

`ViewFileDataIndexer.extractStringLiteral` becomes `extractTemplateNames`
returning a `List<String>`. It walks `TERNARY_EXPRESSION`,
`MATCH_EXPRESSION` / `MATCH_ARM` and `PARENTHESIZED_EXPRESSION` nodes and only
descends into the branch positions (after the `?` token in a ternary, after the
`=>` token in a match arm). New helpers `isTernaryExpression()`,
`isMatchExpression()`, `isMatchArm()` and `isParenthesizedExpression()` are added
to `ASTNodes.kt`.

The three call parsers (`parseMethodCall`, `parseFieldAssignment`,
`parseViewBuilderCall`) emit one info record *per collected value*, all sharing
the same offset. The downstream indexing loops then need no changes: each value
becomes its own index key pointing at the same PSI element.

`setTemplatePath()` state tracking is generalized to a list of current paths so
`setTemplatePath($c ? 'A' : 'B')` followed by `setTemplate('x')` indexes both
`A/x` and `B/x`.

The index version in `ViewFileIndex` is bumped so existing indexes are rebuilt.

### PSI layer (line markers and method-level marker)

`CakeController.kt` gains `templateNamesFromExpression(PsiElement?): List<String>`
using the PHP PSI classes `TernaryExpression`, `PhpMatchExpression`,
`PhpMatchArm` and `ParenthesizedExpression`.

- `ViewBuilderCall.parameterValue: String` becomes `parameterValues: List<String>`.
- `actionNamesFromViewBuilderCalls` keeps its flat `List<ActionName>` result but
  is built from a new per-call grouping so `actionNamesFromViewBuilderCall` can
  find the names for one specific `setTemplate` call by offset instead of by
  positional index.
- `actionNamesFromRenderCall`, `actionNamesFromViewAssignment` and
  `actionNamesFromViewBuilderCall` return the first value as
  `defaultActionName` and the rest as `otherActionNames`. The gutter popup
  already renders `otherActionNames`, so the marker shows every branch.

### Go-to-declaration

`TemplateGotoDeclarationHandler` finds the clicked `StringLiteralExpression` and
walks up through ternary branches, match arm bodies and parentheses to the
outermost expression (`templateArgumentFromLiteral`). That outermost node is
what must sit directly in the `ParameterList` or be the `AssignmentExpression`
value. A literal in a ternary condition or a match arm condition never
navigates.

Clicking a branch navigates to that branch's file only. When the *other* half of
a chained `setTemplatePath(...)->setTemplate(...)` is multi-valued, the click
resolves the cross product of paths.

### Out of scope

- Variables, constants, method calls or string concatenation as template names.
- `$this->viewBuilder()->template()` (the pre-3.4 setter).
- `$this->autoRender = false`.

## Test plan

Cake 5 first, then Cake 4, 3 and 2.

- `TemplateGotoDeclarationTest` (all versions): ternary and match in
  `render()`, `setTemplate()` and (Cake 2) `$this->view`; click on each branch;
  nested ternary; parenthesized; literal in the condition does not navigate;
  chained `setTemplatePath` with ternary template.
- `ControllerLineMarkerTest` (Cake 3): render / setTemplate / view-assignment
  markers list both branch files; the method marker lists both too.
- `ViewVariableTest` (Cake 5 and Cake 2): variables set in a method that picks
  its template through a ternary or a match are completed in *both* target
  views. This exercises the index end to end. New fixture methods and templates
  are added for this so existing count-based assertions are untouched.
- `ViewToControllerGotoRelatedTest` (Cake 5): a view referenced only through a
  ternary navigates back to its controller method.

## Implementation Progress

### Session #1 (2026-09-08)

Implemented as designed, in one pass, on branch `support-ternary-template-link`.

**Source changes**

- `ASTNodes.kt`: added `isTernaryExpression()`, `isMatchExpression()`,
  `isMatchArm()`, `isDefaultMatchArm()` and `isParenthesizedExpression()`.
  The PHP plugin uses a separate `DEFAULT_MATCH_ARM` element type for
  `default =>` arms, so it needs its own check on the AST side. On the PSI
  side `PhpDefaultMatchArm` extends `PhpMatchArm`, so one `is` check covers
  both, but `getMatchArms()` and `getDefaultMatchArm()` are both consulted in
  case a plugin version excludes the default arm from the list.
- `ViewFileDataIndexer.kt`: `extractStringLiteral` no longer pretends
  non-literals are literals. New `extractTemplateNames` returns the list of
  possible names. All three info records carry a `List<String>` and the
  indexing loops iterate it. `setTemplatePath` state is a list.
  `ViewFileIndex` version bumped 17 to 18.
- `CakeController.kt`: new `templateNamesFromExpression` and
  `actionNamesFromTemplateNames`. `ViewBuilderCall.parameterValues` is a list.
  New `actionNamesBySetTemplateCall` keeps names grouped per call so
  `actionNamesFromViewBuilderCall` looks up by offset instead of by index.
- `TemplateGotoDeclarationHandler.kt`: new `templateArgumentFromLiteral` walks
  from the clicked literal up to the argument; `navigateToView` became
  `navigateToViews` and takes a list. The old `PlatformPatterns` pattern for
  render() was replaced by the same walk plus `RenderMethodPattern.accepts`.

**Tests** (23 new, all passing, plus the existing suites they live in)

- Cake 5 `TemplateGotoDeclarationTest`: 12 cases covering both ternary
  branches, short ternary, nested parenthesized ternary, match arms (default
  and non-default, multi-condition), condition literals not navigating, and
  ternaries in chained and preceding `setTemplatePath`.
- Cake 4 and Cake 3 `TemplateGotoDeclarationTest`: 4 cases each.
- Cake 2 `TemplateGotoDeclarationTest`: 5 cases for `render()` and `$this->view`.
- Cake 5 and Cake 2 `ViewVariableTest`: completion of variables in both
  target views of a ternary and every arm of a match (exercises the index).
- Cake 5 `ViewToControllerGotoRelatedTest`: related-symbol navigation from a
  view reached only through a ternary or a match.
- Cake 3 `ControllerLineMarkerTest`: render, setTemplate, chained
  setTemplatePath, `$this->view` and method-level markers list every branch.

New fixtures: `ternary_one`, `ternary_two`, `match_one`, `match_two`
templates for Cake 5 and Cake 2, `Nested/other.ctp` for Cake 3, and
`ternaryTemplateTest()` / `matchTemplateTest()` (Cake 5) and
`ternary_view_test()` / `match_view_test()` (Cake 2) controller methods.

**Notes**

- The `ViewToControllerGotoRelatedProvider` returns the indexed PSI element,
  which for `setTemplate()` is the `MethodReference`, not the enclosing
  `Method`. Tests that want the method name must walk up with
  `PsiTreeUtil.getParentOfType`.
- The test project parses `match` without any language level configuration,
  so the Cake 2 fixtures can use it even though real Cake 2 apps rarely run
  on PHP 8.
