<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use App\Model\Entity\Author;
use App\Model\Entity\Standalone;
use App\Model\Entity\StandaloneChild;
use Cake\TestSuite\TestCase;

/**
 * Which entity methods act as virtual field accessors, which entity virtual field types,
 * completion and navigation depend on.
 */
class EntityAccessorTest extends TestCase
{
    private function author(array $fields = [])
    {
        return new Author($fields);
    }

    public function testGetterAnswersSnakeCamelAndTitleNames(): void
    {
        $author = $this->author(['first_name' => 'Ada', 'last_name' => 'Lovelace']);
        $this->assertSame('Ada Lovelace', $author->full_name);
        $this->assertSame('Ada Lovelace', $author->fullName);
        $this->assertSame('Ada Lovelace', $author->FullName);
    }

    public function testGetterReceivesStoredValueOrNull(): void
    {
        $this->assertSame("got:'x'", $this->author(['stored_value' => 'x'])->stored_value);
        $this->assertSame('got:NULL', $this->author()->stored_value);
    }

    public function testPublicGetterIsFound(): void
    {
        $this->assertSame('public-name', $this->author()->public_name);
    }

    public function testPrivateGetterIsNotFound(): void
    {
        $this->assertNull($this->author()->secret);
        $this->assertFalse($this->author()->has('secret'));
    }

    public function testPrivateGetterOnParentIsNotFound(): void
    {
        $this->assertNull($this->author()->parent_secret);
    }

    public function testParentGetterIsInherited(): void
    {
        $this->assertSame('parent-name', $this->author()->parent_name);
    }

    public function testTraitGetterIsFound(): void
    {
        $this->assertSame('trait-name', $this->author()->trait_name);
    }

    public function testChildGetterOverridesParent(): void
    {
        $this->assertSame('child', $this->author()->overridden);
    }

    public function testGettersOnClassUsingEntityTraitDirectly(): void
    {
        $standalone = new Standalone();
        $this->assertNull($standalone->own_name);
        $this->assertNull($standalone->own_secret);
    }

    public function testGettersOnChildOfClassUsingEntityTraitDirectly(): void
    {
        $child = new StandaloneChild();
        $this->assertSame('own-name', $child->own_name);
        $this->assertSame('own-secret', $child->own_secret);
        $this->assertNull($child->child_secret);
    }

    public function testSetterRunsOnAssignment(): void
    {
        $author = $this->author();
        $author->nickname = 'ada';
        $this->assertSame('ADA', $author->nickname);
    }

    public function testGetterNameIsUnderscoredLikeInflector(): void
    {
        $author = $this->author();
        $this->assertSame('url-slug', $author->u_r_l_slug);
        $this->assertSame('url-slug', $author->uRLSlug);
        $this->assertSame('name-2', $author->name2);
    }

    public function testMethodWithoutUnderscoreIsNotAccessor(): void
    {
        $this->assertNull($this->author()->not_a_field);
    }

    public function testVirtualPropertyIsNotNeededForAccess(): void
    {
        $author = $this->author(['first_name' => 'Ada', 'last_name' => 'Lovelace']);
        $this->assertSame([], $author->getVirtual());
        $this->assertSame('Ada Lovelace', $author->full_name);
        $this->assertArrayNotHasKey('full_name', $author->toArray());
        $author->setVirtual(['full_name']);
        $this->assertSame('Ada Lovelace', $author->toArray()['full_name']);
    }

    public function testHasAndIssetOnAccessorReturningValue(): void
    {
        $author = $this->author(['first_name' => 'Ada']);
        $this->assertTrue($author->has('full_name'));
        $this->assertTrue(isset($author->full_name));
    }

    public function testHasAndIssetOnAccessorReturningNull(): void
    {
        $author = $this->author();
        $this->assertTrue($author->has('nullable'));
        $this->assertFalse(isset($author->nullable));
    }
}
