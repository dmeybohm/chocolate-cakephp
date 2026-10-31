<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Acme\Vendored\Model\Table\ThingsTable as VendoredThings;
use App\Model\Table\Sub\NestedTable as AppNested;
use App\Model\Table\WidgetsTable as AppWidgets;
use Cake\Http\Response;
use Cake\Http\ServerRequest;
use Cake\ORM\Entity;
use Cake\ORM\Table;
use Cake\ORM\TableRegistry;
use Cake\TestSuite\TestCase;
use Other\Model\Table\WidgetsTable as OtherWidgets;
use Probe\Controller\ThingsController;
use Probe\Model\Entity\Widget as ProbeWidget;
use Probe\Model\Table\Sub\NestedTable as ProbeNested;
use Probe\Model\Table\WidgetsTable as ProbeWidgets;
use Probe\View\Cell\GadgetCell;
use Unloaded\Model\Table\ThingsTable as UnloadedThings;

/**
 * Table class resolution from a table locator alias, which table locator types, navigation
 * and completion depend on.
 */
class TableLocatorTest extends TestCase
{
    public function setUp(): void
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

    private function cell()
    {
        return new GadgetCell(new ServerRequest(), new Response());
    }

    public function testPluginAliasLoadsPluginTable(): void
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate('Probe.Widgets')));
    }

    public function testBareAliasLoadsAppTable(): void
    {
        $this->assertSame(AppWidgets::class, get_class($this->locate('Widgets')));
    }

    public function testBareAliasDoesNotReachPluginTable(): void
    {
        $this->assertSame(Table::class, get_class($this->locate('Gadgets')));
    }

    public function testPluginControllerBareFetchTableLoadsAppTable(): void
    {
        $this->assertSame(AppWidgets::class, get_class($this->controller()->loadTable('Widgets')));
    }

    public function testPluginAliasDoesNotFallBackToAppTable(): void
    {
        $this->assertSame(Table::class, get_class($this->locate('Probe.AppOnly')));
    }

    public function testPluginControllerLoadsOtherPluginTable(): void
    {
        $this->assertSame(OtherWidgets::class, get_class($this->controller()->loadTable('Other.Widgets')));
    }

    public function testVendorPrefixedAliasMapsSlashesToNamespace(): void
    {
        $this->assertSame(VendoredThings::class, get_class($this->locate('Acme/Vendored.Things')));
    }

    public function testLastSegmentOfVendorPluginIsGenericTable(): void
    {
        $this->assertSame(Table::class, get_class($this->locate('Vendored.Things')));
    }

    public function testUnloadedPluginTableResolvesByNamespace(): void
    {
        $this->assertSame(UnloadedThings::class, get_class($this->locate('Unloaded.Things')));
    }

    public function testLowercasePluginAliasResolution(): void
    {
        $this->assertSame(Table::class, get_class($this->locate('probe.Cased')));
    }

    public function testSlashInTableNameSelectsSubNamespace(): void
    {
        $this->assertSame(ProbeNested::class, get_class($this->locate('Probe.Sub/Nested')));
        $this->assertSame(AppNested::class, get_class($this->locate('Sub/Nested')));
    }

    public function testAliasSplitsOnFirstDotOnly(): void
    {
        $this->assertSame(Table::class, get_class($this->locate('Probe.Sub.Widgets')));
    }

    public function testPluginAliasSetsShortAlias(): void
    {
        $table = $this->locate('Probe.Widgets');
        $this->assertSame('Widgets', $table->getAlias());
        $this->assertSame('Probe.Widgets', $table->getRegistryAlias());
    }

    public function testBareAndPluginAliasesAreCachedSeparately(): void
    {
        $this->locate('Probe.Widgets');
        $this->assertSame(AppWidgets::class, get_class($this->locate('Widgets')));
    }

    public function testClassNameOptionAcceptsPluginNotation(): void
    {
        $table = $this->locate('Foo', ['className' => 'Probe.Widgets']);
        $this->assertSame(ProbeWidgets::class, get_class($table));
        $this->assertSame('Foo', $table->getAlias());
    }

    public function testClassNameOptionAcceptsFullyQualifiedClass(): void
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate('Foo', ['className' => ProbeWidgets::class])));
    }

    public function testClassNameOptionWinsOverAlias(): void
    {
        $this->assertSame(OtherWidgets::class, get_class($this->locate('Probe.Widgets', ['className' => 'Other.Widgets'])));
    }

    public function testFullyQualifiedAliasLoadsClass(): void
    {
        $this->assertSame(ProbeWidgets::class, get_class($this->locate(ProbeWidgets::class)));
    }

    public function testPluginTableUsesPluginEntity(): void
    {
        $this->assertSame(ProbeWidget::class, $this->locate('Probe.Widgets')->getEntityClass());
    }

    public function testPluginTableDoesNotFallBackToAppEntity(): void
    {
        $this->assertSame(Entity::class, $this->locate('Probe.Gadgets')->getEntityClass());
    }

    public function testEmptyPluginSegmentLoadsAppTable(): void
    {
        $this->assertSame(AppWidgets::class, get_class($this->locate('.Widgets')));
    }

    public function testPluginCellBareFetchTableLoadsAppTable(): void
    {
        $this->assertSame(AppWidgets::class, get_class($this->cell()->loadTable('Widgets')));
    }

    public function testPluginCellLoadsOtherPluginTable(): void
    {
        $this->assertSame(OtherWidgets::class, get_class($this->cell()->loadTable('Other.Widgets')));
    }
}
