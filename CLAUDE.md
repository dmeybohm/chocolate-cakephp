# chocolate-cakephp

This project is a PhpStorm plugin called Chocolate CakePHP. It is a
framework integration plugin for PhpStorm adding supporting for
CakePHP APIs. It supports CakePHP versions 5, 4, 3, and 2.

## Git

Unless otherwise asked, we should do the work on a feature branch. 
Often the branch will already be checked out, but we should check
out a new feature branch before starting to work. If the main
branch is currently checked out, ask which feature branch to switch 
to prior to beginning work.

We should commit after each significant step. You should add a
`Co-Authored-By` annotation for Claude to each commit.

Be way of rebasing to undo a mistake. You can amend the previous
commit, but if you have to go back further in the history, prefer
to make a new commit instead.

## Feature log

When planning how to implement a feature, we should store the plans
in markdown format in the feature logs.

This is to keep track of development and design rationales. The
feature log is located in the `feature/` directory, and it is
organized by year and then month.  All plan documents for each
feature should go in the `feature/$year/$month/{$git_branch_name}.md`.
Each document there should have the plan for implementing the
feature.

If you need to document the implementation status, prefer to add a
"Implementation Progress" section in the feature's corresponding
markdown file, as opposed to adding markdown files in other
locations, to keep the feature development documentation organized.
You can increment include a "Session #1" subsection for the first
implementation progress part, and then subsequently increment the
number for additioanl sections.  Indexes of the feature documents
will be generated automatically for keeping them browsable, so you
only need to update the individual feature documents.

You can also consult the `feature/` directory if you're confused
about how something was implemented in order to try to clarify, if
the code is not clear.

## Testing

You can run the tests with `gradlew` as per usual for a Java
project, and find relevant JDKs based on the platform you're
running on in the typical locations for those.

### Test layout

There are some generic tests under the top level, and then a
breakdown by CakePHP version in `cake2`, `cake3`, etc directories.

### Targeted Tests

When testing features, try to avoid running the whole test suite
until the end, since it is time consuming. Try to run targetted
tests as soon as possible after development to find any problems
sooner.

### Completion tests

Here's an example of a completion test that checks a variable is
completed in a view. This test checks the completion is generated
by looking at `myFixture.lookupElementStrings` and then checks the
type of the completion result by looking at
`myFixture.lookupElements` and using the
`LookupElementPresentation`:

```kotlin
    fun `test array access in set resolves type correctly`() {
        myFixture.configureByFilePathAndText("cake4/templates/Movie/expression_variety_test.php", """
        <?php
        echo ${'$'}<caret>
        """.trimIndent())
        myFixture.completeBasic()

        val result = myFixture.lookupElementStrings
        assertNotNull(result)
        assertTrue(result!!.contains("${'$'}item"))

        val elements = myFixture.lookupElements!!
        val itemElement = elements.find { it.lookupString == "${'$'}item" }
        assertNotNull(itemElement)

        val presentation = LookupElementPresentation()
        itemElement!!.renderElement(presentation)

        assertNotNull(presentation.typeText)
        assertTrue("Type should be string or mixed, but got: ${presentation.typeText}",
                   presentation.typeText == "string" || presentation.typeText == "mixed")
    }

```

One thing to note, is that PhpStorm will automatically add a single
completion to the file, without poppping up a window, if there is
only a single completion.  This isn't good for our tests. In that
case, `lookupElementStrings` will be `null`, so those assertions
looking at it will fail, and there will be no way to get the type
information from the CompletionContributor.

To workaround that, we can make sure there are multiple variables
in the variable completion tests (for example, by making sure the
fixture includes multiple variables) to ensure the popup window
appears and we can check the presentation results with
`LookupElementPresentation`.

Also, if you see a case where `lookupElementStrings` is null, it
might be because of this edge case. So that is something important
to keep in mind when dealing with the tests.

## Debugging

If you get stuck and you would like to run some code in a debugger,
don't be afraid to ask for help. The developers have access to more
sophisticated debug tools than you do.

**Adding Debug Output:**

For quick debugging without a full debugger, you can use:
```kotlin
com.intellij.openapi.diagnostic.thisLogger().warn("Debug: variable = ${value}")
```
