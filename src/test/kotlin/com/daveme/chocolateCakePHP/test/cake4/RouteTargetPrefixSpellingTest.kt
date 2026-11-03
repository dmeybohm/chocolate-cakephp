package com.daveme.chocolateCakePHP.test.cake4

import com.daveme.chocolateCakePHP.Settings
import com.daveme.chocolateCakePHP.cake.ResolvedRouteTarget
import com.daveme.chocolateCakePHP.cake.RouteKey
import com.daveme.chocolateCakePHP.cake.RouteScope
import com.daveme.chocolateCakePHP.cake.RouteTarget
import com.daveme.chocolateCakePHP.cake.RouteValue
import com.daveme.chocolateCakePHP.cake.controllerClassFqn
import com.daveme.chocolateCakePHP.cake.resolveInScope
import com.daveme.chocolateCakePHP.cake.routeTargetControllers
import com.jetbrains.php.PhpIndex

/**
 * CakePHP 4 camelizes a routing prefix before CakePHP 5 reads it as written.
 */
class RouteTargetPrefixSpellingTest : Cake4BaseTestCase() {

    override fun setUpTestFiles() {
        myFixture.configureByFiles(
            "cake4/src4/Controller/AppController.php",
            "cake4/src4/Controller/Admin/ReportsController.php",
        )
    }

    private val settings: Settings get() = Settings.getInstance(myFixture.project)

    fun `test the detected major version is 4`() {
        assertEquals(4, settings.cakeMajorVersion)
    }

    fun `test a lower case prefix names the camelized namespace`() {
        assertEquals(
            "\\App\\Controller\\Admin\\Reports\\ThingsController",
            controllerClassFqn(settings, ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("admin/reports"), "Things", null))
        )
        val found = routeTargetControllers(
            PhpIndex.getInstance(myFixture.project), settings,
            ResolvedRouteTarget(RouteValue.Known(null), RouteValue.Known("admin"), "Reports", null)
        ).map { it.fqn }
        assertEquals(listOf("\\App\\Controller\\Admin\\ReportsController"), found)
    }

    fun `test a false prefix key is an explicit none`() {
        assertEquals(
            ResolvedRouteTarget(RouteValue.Known("Probe"), RouteValue.Known(null), "Reports", null),
            resolveInScope(settings, RouteTarget(prefix = RouteKey.False, controller = "Reports"), RouteScope("Probe", "Admin"))
        )
        assertNull(resolveInScope(settings, RouteTarget(plugin = RouteKey.False, controller = "Reports"), RouteScope("Probe", "Admin")))
    }
}
