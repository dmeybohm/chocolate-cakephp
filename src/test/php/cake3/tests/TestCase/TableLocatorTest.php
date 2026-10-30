<?php

namespace App\Test\TestCase;

use Acme\Vendored\Model\Table\ThingsTable as VendoredThings;
use App\Model\Table\Sub\NestedTable as AppNested;
use App\Model\Table\WidgetsTable as AppWidgets;
use Cake\Http\ServerRequest;
use Cake\ORM\Entity;
use Cake\ORM\Table;
use Cake\ORM\TableRegistry;
use Cake\TestSuite\TestCase;
use Other\Model\Table\WidgetsTable as OtherWidgets;
use Probe\Controller\ThingsController;
use Probe\Model\Entity\Widget as ProbeWidget;
use Probe\Model\Table\CasedTable as ProbeCased;
use Probe\Model\Table\Sub\NestedTable as ProbeNested;
use Probe\Model\Table\WidgetsTable as ProbeWidgets;
use Unloaded\Model\Table\ThingsTable as UnloadedThings;

/**
 * Table class resolution from a table locator alias, which table locator types, navigation
 * and completion depend on.
 */
class TableLocatorTest extends TestCase
{
    public function setUp()
    {
        parent::setUp();
        $this->loadPlugins(['Probe', 'Other', 'Acme/Vendored']);
        TableRegistry::getTableLocator()->clear();
    }

    private function locate($alias, array $options = [])
    {
        return TableRegistry::getTableLocator()->get($alias, $options);
    }

    private function controller()
    {
        return new ThingsController(new ServerRequest(['params' => ['plugin' => 'Probe']]));
    }

    public function testPluginAliasLoadsPluginTable()
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate('Probe.Widgets')));
    }

    public function testBareAliasLoadsAppTable()
    {
        $this->assertSame(AppWidgets::class, get_class($this->locate('Widgets')));
    }

    public function testBareAliasDoesNotReachPluginTable()
    {
        $this->assertSame(Table::class, get_class($this->locate('Gadgets')));
    }

    public function testPluginAliasDoesNotFallBackToAppTable()
    {
        $this->assertSame(Table::class, get_class($this->locate('Probe.AppOnly')));
    }

    public function testPluginControllerLoadsOtherPluginTable()
    {
        $this->assertSame(OtherWidgets::class, get_class($this->controller()->loadTable('Other.Widgets')));
    }

    public function testVendorPrefixedAliasMapsSlashesToNamespace()
    {
        $this->assertSame(VendoredThings::class, get_class($this->locate('Acme/Vendored.Things')));
    }

    public function testLastSegmentOfVendorPluginIsGenericTable()
    {
        $this->assertSame(Table::class, get_class($this->locate('Vendored.Things')));
    }

    public function testUnloadedPluginTableResolvesByNamespace()
    {
        $this->assertSame(UnloadedThings::class, get_class($this->locate('Unloaded.Things')));
    }

    public function testLowercasePluginAliasResolution()
    {
        $this->assertSame(ProbeCased::class, get_class($this->locate('probe.Cased')));
    }

    public function testSlashInTableNameSelectsSubNamespace()
    {
        $this->assertSame(ProbeNested::class, get_class($this->locate('Probe.Sub/Nested')));
        $this->assertSame(AppNested::class, get_class($this->locate('Sub/Nested')));
    }

    public function testAliasSplitsOnFirstDotOnly()
    {
        $this->assertSame(Table::class, get_class($this->locate('Probe.Sub.Widgets')));
    }

    public function testPluginAliasSetsShortAlias()
    {
        $table = $this->locate('Probe.Widgets');
        $this->assertSame('Widgets', $table->getAlias());
        $this->assertSame('Probe.Widgets', $table->getRegistryAlias());
    }

    public function testBareAndPluginAliasesAreCachedSeparately()
    {
        $this->locate('Probe.Widgets');
        $this->assertSame(AppWidgets::class, get_class($this->locate('Widgets')));
    }

    public function testClassNameOptionAcceptsPluginNotation()
    {
        $table = $this->locate('Foo', ['className' => 'Probe.Widgets']);
        $this->assertSame(ProbeWidgets::class, get_class($table));
        $this->assertSame('Foo', $table->getAlias());
    }

    public function testClassNameOptionAcceptsFullyQualifiedClass()
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate('Foo', ['className' => ProbeWidgets::class])));
    }

    public function testClassNameOptionWinsOverAlias()
    {
        $this->assertSame(OtherWidgets::class, get_class($this->locate('Probe.Widgets', ['className' => 'Other.Widgets'])));
    }

    public function testFullyQualifiedAliasLoadsClass()
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate(ProbeWidgets::class)));
    }

    public function testPluginTableUsesPluginEntity()
    {
        $this->assertSame(ProbeWidget::class, $this->locate('Probe.Widgets')->getEntityClass());
    }

    public function testPluginTableDoesNotFallBackToAppEntity()
    {
        $this->assertSame(Entity::class, $this->locate('Probe.Gadgets')->getEntityClass());
    }

    public function testStaticRegistryGetLoadsPluginTable()
    {
        $this->deprecated(function () {
            $this->assertSame(ProbeWidgets::class, get_class(TableRegistry::get('Probe.Widgets')));
        });
    }

    public function testEmptyPluginSegmentLoadsAppTable()
    {
        $this->assertSame(AppWidgets::class, get_class($this->locate('.Widgets')));
    }
}
