package com.daveme.chocolateCakePHP.test

import junit.framework.Test
import junit.framework.TestCase
import junit.framework.TestListener
import org.junit.internal.AssumptionViolatedException
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

/**
 * Runs JUnit 3 style test classes, reporting a violated assumption as a skipped test rather
 * than a failure.
 */
class AssumptionAwareJUnit38Runner(klass: Class<*>) : JUnit38ClassRunner(klass) {

    override fun createAdaptingListener(notifier: RunNotifier): TestListener {
        val delegate = super.createAdaptingListener(notifier)
        return object : TestListener by delegate {
            override fun addError(test: Test, e: Throwable) {
                if (e is AssumptionViolatedException) {
                    notifier.fireTestAssumptionFailed(Failure(describe(test), e))
                } else {
                    delegate.addError(test, e)
                }
            }
        }
    }

    private fun describe(test: Test): Description =
        if (test is TestCase) {
            Description.createTestDescription(test.javaClass, test.name)
        } else {
            Description.createSuiteDescription(test.javaClass)
        }
}
