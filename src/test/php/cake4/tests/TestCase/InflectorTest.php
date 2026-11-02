<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use App\Model\Entity\Person;
use Cake\ORM\TableRegistry;
use Cake\TestSuite\TestCase;
use Cake\Utility\Inflector;

/**
 * The inflections the plugin's Inflector port and string helpers reproduce, for the names
 * entity virtual fields and association properties are derived from.
 */
class InflectorTest extends TestCase
{
    public function testUnderscoreOfCamelCaseNames(): void
    {
        $this->assertSame('full_name', Inflector::underscore('fullName'));
        $this->assertSame('full_name', Inflector::underscore('FullName'));
        $this->assertSame('u_r_l_slug', Inflector::underscore('uRLSlug'));
        $this->assertSame('name2', Inflector::underscore('name2'));
        $this->assertSame('a', Inflector::underscore('a'));
        $this->assertSame('review_authors', Inflector::underscore('ReviewAuthors'));
    }

    public function testSingularizeIrregularAndSpecialPlurals(): void
    {
        $this->assertSame('Author', Inflector::singularize('Authors'));
        $this->assertSame('Person', Inflector::singularize('People'));
        $this->assertSame('Movie', Inflector::singularize('Movies'));
        $this->assertSame('Category', Inflector::singularize('Categories'));
        $this->assertSame('Status', Inflector::singularize('Statuses'));
        $this->assertSame('Sheep', Inflector::singularize('Sheep'));
    }

    public function testPluralizeIrregularAndSpecialSingulars(): void
    {
        $this->assertSame('Authors', Inflector::pluralize('Author'));
        $this->assertSame('People', Inflector::pluralize('Person'));
        $this->assertSame('Movies', Inflector::pluralize('Movie'));
        $this->assertSame('Categories', Inflector::pluralize('Category'));
        $this->assertSame('Statuses', Inflector::pluralize('Status'));
        $this->assertSame('Sheep', Inflector::pluralize('Sheep'));
    }

    public function testTableEntityClassIsTheSingularizedAlias(): void
    {
        TableRegistry::getTableLocator()->clear();
        $this->assertSame(Person::class, TableRegistry::getTableLocator()->get('People')->getEntityClass());
    }
}
