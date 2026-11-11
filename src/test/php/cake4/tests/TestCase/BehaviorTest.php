<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Acme\Vendored\Model\Behavior\StampedBehavior;
use App\Model\Behavior\AuditBehavior;
use App\Model\Behavior\ChildSluggableBehavior;
use App\Model\Behavior\CounterCacheBehavior as AppCounterCache;
use App\Model\Behavior\SluggableBehavior as AppSluggable;
use App\Model\Entity\Tag;
use App\Model\Table\TagsTable;
use Cake\Core\Plugin;
use Cake\Datasource\ConnectionManager;
use Cake\ORM\Behavior\TimestampBehavior;
use Cake\ORM\Behavior\TreeBehavior;
use Cake\ORM\Entity;
use Cake\ORM\Exception\MissingBehaviorException;
use Cake\ORM\Table;
use Cake\ORM\TableRegistry;
use Cake\TestSuite\TestCase;
use InvalidArgumentException;
use LogicException;
use Probe\Model\Behavior\SluggableBehavior as ProbeSluggable;
use RuntimeException;
use Unloaded\Model\Behavior\MarkedBehavior;

/**
 * How a table attaches behaviors and which of their methods and finders it gains, which
 * behavior completion, types and navigation depend on.
 */
class BehaviorTest extends TestCase
{
    public function setUp(): void
    {
        parent::setUp();
        $this->loadPlugins(['Probe']);
        TableRegistry::getTableLocator()->clear();

        $connection = ConnectionManager::get('default');
        $connection->execute('CREATE TABLE IF NOT EXISTS pages (id INTEGER PRIMARY KEY, title TEXT, slug TEXT)');
        $connection->execute('CREATE TABLE IF NOT EXISTS photos (id INTEGER PRIMARY KEY, album_id INTEGER, title TEXT)');
        $connection->execute('CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY, photo_id INTEGER, name TEXT)');
        $connection->execute('CREATE TABLE IF NOT EXISTS albums (id INTEGER PRIMARY KEY, name TEXT)');
        $connection->execute('DELETE FROM photos');
        $connection->execute('DELETE FROM tags');
        $connection->execute('DELETE FROM albums');
        $connection->insert('albums', ['id' => 1, 'name' => 'Holiday']);
        $connection->insert('photos', ['id' => 1, 'album_id' => 1, 'title' => 'Beach']);
        $connection->insert('tags', ['id' => 1, 'photo_id' => 1, 'name' => 'sea']);
        $connection->insert('tags', ['id' => 2, 'photo_id' => 1, 'name' => 'sand']);
    }

    private function assertNoDeprecation(callable $callable): void
    {
        $deprecations = [];
        set_error_handler(function ($number, $message) use (&$deprecations) {
            $deprecations[] = $message;
            return true;
        }, E_USER_DEPRECATED);
        try {
            $callable();
        } finally {
            restore_error_handler();
        }
        $this->assertSame([], $deprecations);
    }

    private function table(): Table
    {
        return new Table(['alias' => 'Things', 'table' => 'things']);
    }

    private function pages(): Table
    {
        return TableRegistry::getTableLocator()->get('Pages');
    }

    public function testBareNameLoadsAppBehaviorBeforeCore(): void
    {
        $table = $this->table();
        $table->addBehavior('CounterCache');
        $table->addBehavior('Timestamp');
        $this->assertSame(AppCounterCache::class, get_class($table->getBehavior('CounterCache')));
        $this->assertSame(TimestampBehavior::class, get_class($table->getBehavior('Timestamp')));
    }

    public function testBareNameDoesNotReachPluginBehavior(): void
    {
        $this->expectException(MissingBehaviorException::class);
        $this->table()->addBehavior('Hashable');
    }

    public function testPluginNameLoadsPluginBehavior(): void
    {
        $table = $this->table();
        $table->addBehavior('Probe.Sluggable');
        $this->assertSame(ProbeSluggable::class, get_class($table->getBehavior('Sluggable')));
        $this->assertSame(['Sluggable'], $table->behaviors()->loaded());
    }

    public function testBareNameInPluginTableLoadsAppBehavior(): void
    {
        $stamps = TableRegistry::getTableLocator()->get('Probe.Stamps');
        $this->assertSame(AppSluggable::class, get_class($stamps->getBehavior('Sluggable')));
        $this->assertSame(AppCounterCache::class, get_class($stamps->getBehavior('CounterCache')));
    }

    public function testClassNamePlainNameLoadsAppBehaviorBeforeCore(): void
    {
        $table = $this->table();
        $table->addBehavior('Counter', ['className' => 'CounterCache']);
        $this->assertSame(['Counter'], $table->behaviors()->loaded());
        $this->assertSame(AppCounterCache::class, get_class($table->getBehavior('Counter')));
    }

    public function testPluginNameDoesNotFallBackToAppBehavior(): void
    {
        $this->expectException(MissingBehaviorException::class);
        $this->table()->addBehavior('Probe.Audit');
    }

    public function testClassNameOptionAcceptsPlainPluginAndFqnAndKeepsAlias(): void
    {
        $plain = $this->table();
        $plain->addBehavior('Plain', ['className' => 'Sluggable']);
        $this->assertSame(['Plain'], $plain->behaviors()->loaded());
        $this->assertSame(AppSluggable::class, get_class($plain->getBehavior('Plain')));

        $dotted = $this->table();
        $dotted->addBehavior('Dotted', ['className' => 'Probe.Sluggable']);
        $this->assertSame(['Dotted'], $dotted->behaviors()->loaded());
        $this->assertSame(ProbeSluggable::class, get_class($dotted->getBehavior('Dotted')));

        $fqn = $this->table();
        $fqn->addBehavior('Fqn', ['className' => ChildSluggableBehavior::class]);
        $this->assertSame(['Fqn'], $fqn->behaviors()->loaded());
        $this->assertSame(ChildSluggableBehavior::class, get_class($fqn->getBehavior('Fqn')));
    }

    public function testAddBehaviorsAcceptsPlainAndKeyedEntries(): void
    {
        $table = $this->table();
        $table->addBehaviors(['Timestamp', 'Audit' => ['implementedMethods' => []]]);
        $this->assertSame(['Timestamp', 'Audit'], $table->behaviors()->loaded());
        $this->assertSame([], $table->getBehavior('Audit')->getConfig('implementedMethods'));
    }

    public function testReflectionMixesInPublicMethodsAndFinders(): void
    {
        $table = $this->table();
        $table->addBehavior('Sluggable');
        $behavior = $table->getBehavior('Sluggable');
        $this->assertSame(['slug' => 'slug'], $behavior->implementedMethods());
        $this->assertSame(['slugged' => 'findSlugged'], $behavior->implementedFinders());

        $registry = $table->behaviors();
        $this->assertTrue($registry->hasMethod('slug'));
        $this->assertTrue($registry->hasMethod('SLUG'));
        $this->assertTrue($registry->hasFinder('slugged'));
        $this->assertTrue($table->hasFinder('slugged'));
        foreach (['_normalize', 'beforeSave', 'initialize', 'table', 'getConfig', 'implementedEvents', 'findSlugged'] as $name) {
            $this->assertFalse($registry->hasMethod($name), $name);
        }
    }

    public function testReflectionIncludesParentBehaviorMethods(): void
    {
        $table = $this->table();
        $table->addBehavior('ChildSluggable');
        $methods = $table->getBehavior('ChildSluggable')->implementedMethods();
        ksort($methods);
        $this->assertSame(['reslug' => 'reslug', 'slug' => 'slug'], $methods);
        $this->assertSame(['slugged' => 'findSlugged'], $table->getBehavior('ChildSluggable')->implementedFinders());
        $this->assertSame('hello-2', $table->reslug('Hello'));
    }

    public function testDefaultConfigMapsRestrictMixedInMethods(): void
    {
        $table = $this->table();
        $table->addBehavior('Audit');
        $table->addBehavior('Tree');
        $this->assertSame(['audit' => 'writeAudit'], $table->getBehavior('Audit')->implementedMethods());
        $this->assertSame(['audited' => 'findAudited'], $table->getBehavior('Audit')->implementedFinders());

        $registry = $table->behaviors();
        $this->assertTrue($registry->hasMethod('audit'));
        $this->assertFalse($registry->hasMethod('writeAudit'));
        $this->assertFalse($registry->hasMethod('notMixedIn'));
        $this->assertSame('audit:x', $table->audit('x'));

        $this->assertSame(
            ['childCount', 'moveUp', 'moveDown', 'recover', 'removeFromTree', 'getLevel', 'formatTreeList'],
            array_keys($table->getBehavior('Tree')->implementedMethods())
        );
        $this->assertSame(['path', 'children', 'treeList'], array_keys($table->getBehavior('Tree')->implementedFinders()));
        $this->assertFalse($registry->hasMethod('beforeSave'));
    }

    public function testLoadOptionsMergeWithDefaultMapsByMethodName(): void
    {
        $aliased = $this->table();
        $aliased->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
        $this->assertSame(['trail' => 'writeAudit'], $aliased->getBehavior('Audit')->implementedMethods());
        $this->assertSame(['audited' => 'findAudited'], $aliased->getBehavior('Audit')->implementedFinders());
        $this->assertSame('audit:x', $aliased->trail('x'));

        $extended = $this->table();
        $extended->addBehavior('Audit', ['implementedMethods' => ['extra' => 'notMixedIn']]);
        $extendedMethods = $extended->getBehavior('Audit')->implementedMethods();
        ksort($extendedMethods);
        $this->assertSame(['audit' => 'writeAudit', 'extra' => 'notMixedIn'], $extendedMethods);

        $disabled = $this->table();
        $disabled->addBehavior('Audit', ['implementedMethods' => [], 'implementedFinders' => []]);
        $this->assertSame([], $disabled->getBehavior('Audit')->implementedMethods());
        $this->assertFalse($disabled->behaviors()->hasMethod('audit'));
        $this->assertFalse($disabled->behaviors()->hasFinder('audited'));
    }

    public function testLoadOptionsWithoutDefaultsReplaceReflection(): void
    {
        $table = $this->table();
        $table->addBehavior('Sluggable', ['implementedMethods' => ['makeSlug' => 'slug']]);
        $this->assertSame(['makeSlug' => 'slug'], $table->getBehavior('Sluggable')->implementedMethods());
        $this->assertSame(['slugged' => 'findSlugged'], $table->getBehavior('Sluggable')->implementedFinders());
        $this->assertTrue($table->behaviors()->hasMethod('makeSlug'));
        $this->assertFalse($table->behaviors()->hasMethod('slug'));
    }

    public function testTableOwnMethodAndFinderWinOverBehavior(): void
    {
        $pages = $this->pages();
        $this->assertTrue($pages->behaviors()->hasMethod('touch'));
        $this->assertFalse($pages->touch(new Entity()));

        $this->assertTrue($pages->behaviors()->hasFinder('slugged'));
        $this->assertSame('table:slugged:x', $pages->find('slugged', ['slug' => 'x'])->getOptions()['finder']);
    }

    public function testTwoBehaviorsProvidingTheSameMethodThrow(): void
    {
        $table = $this->table();
        $table->addBehavior('Sluggable');
        $this->expectException(LogicException::class);
        $table->addBehavior('ChildSluggable');
    }

    public function testBehaviorFinderIsCallableOnTableAndQuery(): void
    {
        $pages = $this->pages();
        $this->assertSame('behavior:audited', $pages->find('audited')->getOptions()['finder']);
        $this->assertSame('behavior:audited', $pages->find()->find('audited')->getOptions()['finder']);
    }

    public function testBehaviorAttachedOutsideInitializeMixesInOnceCalled(): void
    {
        $pages = $this->pages();
        $this->assertFalse($pages->behaviors()->hasMethod('recover'));
        $pages->enableTree();
        $this->assertTrue($pages->behaviors()->hasMethod('recover'));
        $this->assertTrue($pages->hasFinder('children'));
        $this->assertSame(TreeBehavior::class, get_class($pages->getBehavior('Tree')));
    }

    public function testChildTableInheritsParentBehaviors(): void
    {
        $child = TableRegistry::getTableLocator()->get('ChildPages');
        $this->assertSame(['Timestamp', 'Sluggable', 'Audit'], $child->behaviors()->loaded());
        $this->assertTrue($child->behaviors()->hasMethod('trail'));
    }

    public function testGetBehaviorReturnsLoadedInstanceAndThrowsForUnloaded(): void
    {
        $pages = $this->pages();
        $this->assertSame(AppSluggable::class, get_class($pages->getBehavior('Sluggable')));
        $this->assertSame($pages->getBehavior('Sluggable'), $pages->behaviors()->get('Sluggable'));
        $this->expectException(InvalidArgumentException::class);
        $pages->getBehavior('Tree');
    }

    public function testBehaviorMethodCalledOnTableIsNotDeprecated(): void
    {
        $pages = $this->pages();
        $this->assertNoDeprecation(function () use ($pages) {
            $this->assertSame('hello-world', $pages->slug('Hello World'));
        });
    }

    public function testBehaviorMethodCalledOnItsInstanceIsNotDeprecated(): void
    {
        $pages = $this->pages();
        $this->assertNoDeprecation(function () use ($pages) {
            $this->assertSame('hello-world', $pages->getBehavior('Sluggable')->slug('Hello World'));
        });
    }

    public function testAssociationAddedByBehaviorBelongsToTable(): void
    {
        $photos = TableRegistry::getTableLocator()->get('Photos');
        $this->assertTrue($photos->hasAssociation('Tags'));
        $this->assertTrue($photos->hasAssociation('Albums'));
        $this->assertSame(TagsTable::class, get_class($photos->Tags->getTarget()));
        $this->assertSame(Table::class, get_class($photos->Albums->getTarget()));

        $photo = $photos->find()->contain(['Tags', 'Albums'])->where(['Photos.id' => 1])->first();
        $this->assertCount(2, $photo->tags);
        $this->assertInstanceOf(Tag::class, $photo->tags[0]);
        $this->assertInstanceOf(Entity::class, $photo->album);
        $this->assertSame('Holiday', $photo->album->name);
    }

    public function testVendorPluginNameLoadsFromTheNestedNamespace(): void
    {
        $table = $this->table();
        $table->addBehavior('Acme/Vendored.Stamped');
        $this->assertSame(['Stamped'], $table->behaviors()->loaded());
        $this->assertSame(StampedBehavior::class, get_class($table->getBehavior('Stamped')));
    }

    public function testPluginNameOfAPluginThatIsNotLoadedLoadsItsBehavior(): void
    {
        $this->assertFalse(Plugin::isLoaded('Unloaded'));
        $table = $this->table();
        $table->addBehavior('Unloaded.Marked');
        $this->assertSame(MarkedBehavior::class, get_class($table->getBehavior('Marked')));
    }

    public function testHasAndRemoveBehaviorTakeTheAlias(): void
    {
        $table = $this->table();
        $table->addBehavior('Probe.Sluggable');
        $table->addBehavior('Trail', ['className' => 'Audit']);
        $this->assertTrue($table->hasBehavior('Sluggable'));
        $this->assertFalse($table->hasBehavior('Probe.Sluggable'));
        $this->assertTrue($table->hasBehavior('Trail'));
        $this->assertFalse($table->hasBehavior('Audit'));

        $table->removeBehavior('Sluggable');
        $this->assertSame(['Trail'], $table->behaviors()->loaded());
    }

    public function testRemovedBehaviorMethodsAreUnmapped(): void
    {
        $table = $this->table();
        $table->addBehavior('Sluggable');
        $table->removeBehavior('Sluggable');
        $this->assertFalse($table->hasBehavior('Sluggable'));
        $this->assertFalse($table->behaviors()->hasMethod('slug'));
        $this->assertFalse($table->behaviors()->hasFinder('slugged'));
    }

    public function testGetBehaviorDoesNotTakeAPluginPrefixedName(): void
    {
        $table = $this->table();
        $table->addBehavior('Probe.Sluggable');
        $this->expectException(InvalidArgumentException::class);
        $table->getBehavior('Probe.Sluggable');
    }

    public function testReflectionIncludesTraitAndStaticMethods(): void
    {
        $table = $this->table();
        $table->addBehavior('Labelable');
        $behavior = $table->getBehavior('Labelable');
        $methods = $behavior->implementedMethods();
        ksort($methods);
        $this->assertSame(['build' => 'build', 'label' => 'label'], $methods);
        $this->assertFalse($table->behaviors()->hasMethod('hidden'));
        $this->assertSame('label', $table->label());
    }

    public function testReflectedFinderNameKeepsItsCaseAfterTheFirstLetter(): void
    {
        $table = new Table(['alias' => 'Pages', 'table' => 'pages']);
        $table->addBehavior('Labelable');
        $finders = $table->getBehavior('Labelable')->implementedFinders();
        ksort($finders);
        $this->assertSame(['labelled' => 'findLabelled', 'topRated' => 'findTopRated'], $finders);
        $this->assertSame('behavior:topRated', $table->find('topRated')->getOptions()['finder']);
        $this->assertSame('behavior:topRated', $table->find('toprated')->getOptions()['finder']);
    }

    public function testChildBehaviorInheritsDefaultConfigMaps(): void
    {
        $table = $this->table();
        $table->addBehavior('ChildAudit');
        $behavior = $table->getBehavior('ChildAudit');
        $this->assertSame(['audit' => 'writeAudit'], $behavior->implementedMethods());
        $this->assertSame(['audited' => 'findAudited'], $behavior->implementedFinders());
        $this->assertFalse($table->behaviors()->hasMethod('extra'));
    }

    public function testChildBehaviorRedeclaringDefaultConfigIsReflected(): void
    {
        $table = $this->table();
        $table->addBehavior('RedeclaredAudit');
        $behavior = $table->getBehavior('RedeclaredAudit');
        $methods = $behavior->implementedMethods();
        ksort($methods);
        $this->assertSame(['extra' => 'extra', 'notMixedIn' => 'notMixedIn', 'writeAudit' => 'writeAudit'], $methods);
        $this->assertSame(['audited' => 'findAudited'], $behavior->implementedFinders());
    }

    public function testLoadingALoadedAliasAgainKeepsTheFirstInstance(): void
    {
        $table = $this->table();
        $table->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
        $first = $table->getBehavior('Audit');
        $table->addBehavior('Audit');
        $this->assertSame($first, $table->getBehavior('Audit'));
        $this->assertSame(['trail' => 'writeAudit'], $table->getBehavior('Audit')->implementedMethods());
    }

    public function testLoadingALoadedAliasWithOtherOptionsThrows(): void
    {
        $table = $this->table();
        $table->addBehavior('Sluggable');
        $this->expectException(RuntimeException::class);
        $table->addBehavior('Sluggable', ['className' => 'Probe.Sluggable']);
    }

    public function testMethodsAndFindersFallBackToReflectionSeparately(): void
    {
        $table = $this->table();
        $table->addBehavior('FindersMapped');
        $behavior = $table->getBehavior('FindersMapped');
        $methods = $behavior->implementedMethods();
        ksort($methods);
        $this->assertSame(['stampIt' => 'stampIt', 'unstamp' => 'unstamp'], $methods);
        $this->assertSame(['recent' => 'findLatest'], $behavior->implementedFinders());

        $other = $this->table();
        $other->addBehavior('MethodsMapped');
        $behavior = $other->getBehavior('MethodsMapped');
        $finders = $behavior->implementedFinders();
        ksort($finders);
        $this->assertSame(['mark' => 'stampIt'], $behavior->implementedMethods());
        $this->assertSame(['latest' => 'findLatest', 'oldest' => 'findOldest'], $finders);
    }

    public function testLoadingALoadedAliasAgainByAnotherNameKeepsTheFirstClass(): void
    {
        $table = $this->table();
        $table->addBehavior('Probe.Sluggable');
        $table->addBehavior('Sluggable');
        $this->assertSame(['Sluggable'], $table->behaviors()->loaded());
        $this->assertSame(ProbeSluggable::class, get_class($table->getBehavior('Sluggable')));
    }

    public function testLoadingALoadedAliasAgainWithTheSameOptionsKeepsTheFirstInstance(): void
    {
        $table = $this->table();
        $table->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
        $first = $table->getBehavior('Audit');
        $table->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
        $this->assertSame($first, $table->getBehavior('Audit'));
    }

    public function testLoadingALoadedAliasAgainWithSomeOfItsOptionsKeepsTheFirstOptions(): void
    {
        $table = $this->table();
        $table->addBehavior('Audit', [
            'implementedMethods' => ['trail' => 'writeAudit'],
            'implementedFinders' => ['logged' => 'findAudited'],
        ]);
        $first = $table->getBehavior('Audit');
        $table->addBehavior('Audit', ['implementedMethods' => ['trail' => 'writeAudit']]);
        $this->assertSame($first, $table->getBehavior('Audit'));
        $this->assertSame(['logged' => 'findAudited'], $table->getBehavior('Audit')->implementedFinders());
        $this->assertTrue($table->hasFinder('logged'));
        $this->assertFalse($table->hasFinder('audited'));
    }
}
