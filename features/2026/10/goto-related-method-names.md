# Go to related method names

## Context

"Go to related" from a template labels a controller action with the file name when the template is
linked by implicit rendering: `Controllers: PostsController.php` instead of
`Controllers: PostsController::viewAll()`. Navigation still reaches the right method.

An implicit render is stored as a `METHOD` reference, so the item's element is the `Method` itself.
`ViewToControllerGotoRelatedProvider.computeCustomName` calls
`PsiTreeUtil.getParentOfType(element, Method::class.java)`, which is strict and starts at the parent,
so it finds no method and falls back to the file name.

## Plan

1. Pass `strict = false` to `getParentOfType` in `computeCustomName`.
2. Test the label of an implicit render on cake5, then mirror the test to the other versions.
3. Once [layout navigation references](layout-navigation-references.md) has merged, tighten the
   template assertion in `BothVersionsLayoutNavigationTest` to expect the method name.

## Implementation Progress
