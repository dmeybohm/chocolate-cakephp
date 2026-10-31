<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use App\Model\Entity\Review;
use App\Model\Entity\User;
use Cake\Datasource\ConnectionManager;
use Cake\ORM\Entity;
use Cake\ORM\TableRegistry;
use Cake\TestSuite\TestCase;
use Probe\Model\Entity\Widget as ProbeWidget;

/**
 * How a table's associations become properties of its entity, which entity association
 * property types, completion and navigation depend on.
 */
class EntityAssociationPropertyTest extends TestCase
{
    public function setUp(): void
    {
        parent::setUp();
        $this->loadPlugins(['Probe']);
        TableRegistry::getTableLocator()->clear();

        $connection = ConnectionManager::get('default');
        $connection->execute('CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)');
        $connection->execute(
            'CREATE TABLE IF NOT EXISTS reviews (id INTEGER PRIMARY KEY, user_id INTEGER, author_id INTEGER,'
            . ' editor_id INTEGER, movie_id INTEGER, review_author_id INTEGER, person_id INTEGER, body TEXT)'
        );
        $connection->execute('CREATE TABLE IF NOT EXISTS profiles (id INTEGER PRIMARY KEY, user_id INTEGER)');
        $connection->execute('DELETE FROM reviews');
        $connection->execute('DELETE FROM profiles');
        $connection->execute('DELETE FROM users');
        $connection->insert('profiles', ['id' => 1, 'user_id' => 1]);
        $connection->insert('users', ['id' => 1, 'name' => 'Ada']);
        $connection->insert('users', ['id' => 2, 'name' => 'Grace']);
        $connection->insert('reviews', ['id' => 1, 'user_id' => 1, 'author_id' => 1, 'editor_id' => 1, 'body' => 'first']);
        $connection->insert('reviews', ['id' => 2, 'user_id' => null, 'author_id' => null, 'editor_id' => null, 'body' => 'second']);
    }

    private function reviews()
    {
        return TableRegistry::getTableLocator()->get('Reviews');
    }

    private function users()
    {
        return TableRegistry::getTableLocator()->get('Users');
    }

    private function review($id, $contain)
    {
        return $this->reviews()->find()->contain($contain)->where(['Reviews.id' => $id])->first();
    }

    private function user($id, $contain)
    {
        return $this->users()->find()->contain($contain)->where(['Users.id' => $id])->first();
    }

    public function testBelongsToPropertyIsSingularUnderscored(): void
    {
        $this->assertSame('author', $this->reviews()->Authors->getProperty());
        $this->assertSame('review_author', $this->reviews()->ReviewAuthors->getProperty());
        $this->assertSame('person', $this->reviews()->People->getProperty());
    }

    public function testHasOnePropertyIsSingularUnderscored(): void
    {
        $this->assertSame('profile', $this->users()->Profiles->getProperty());
    }

    public function testHasManyPropertyIsUnderscoredAlias(): void
    {
        $this->assertSame('reviews', $this->users()->Reviews->getProperty());
        $this->assertSame('review_comments', $this->reviews()->ReviewComments->getProperty());
    }

    public function testBelongsToManyPropertyIsUnderscoredAlias(): void
    {
        $this->assertSame('tags', $this->reviews()->Tags->getProperty());
    }

    public function testPluginPrefixIsDroppedFromProperty(): void
    {
        $this->assertSame('widgets', $this->reviews()->Widgets->getProperty());
    }

    public function testPropertyNameOptionOverrides(): void
    {
        $this->assertSame('reviewer', $this->reviews()->Editors->getProperty());
    }

    public function testTargetEntityIsTargetTablesEntityClass(): void
    {
        $this->assertSame(User::class, $this->reviews()->Authors->getTarget()->getEntityClass());
        $this->assertSame(ProbeWidget::class, $this->reviews()->Widgets->getTarget()->getEntityClass());
    }

    public function testTargetWithoutTableClassUsesBaseEntity(): void
    {
        $this->assertSame(Entity::class, $this->reviews()->Movies->getTarget()->getEntityClass());
    }

    public function testBelongsToPropertyHoldsTargetEntityOrNull(): void
    {
        $this->assertInstanceOf(User::class, $this->review(1, ['Authors'])->author);
        $this->assertNull($this->review(2, ['Authors'])->author);
    }

    public function testHasOnePropertyHoldsTargetEntityOrNull(): void
    {
        $this->assertInstanceOf(Entity::class, $this->user(1, ['Profiles'])->profile);
        $this->assertNull($this->user(2, ['Profiles'])->profile);
    }

    public function testHasManyPropertyHoldsArrayOfTargetEntities(): void
    {
        $reviews = $this->user(1, ['Reviews'])->reviews;
        $this->assertTrue(is_array($reviews));
        $this->assertCount(1, $reviews);
        $this->assertInstanceOf(Review::class, $reviews[0]);
        $this->assertSame([], $this->user(2, ['Reviews'])->reviews);
    }

    public function testAccessorReceivesAssociationValue(): void
    {
        $this->assertSame('accessor:' . User::class, $this->review(1, ['Editors'])->reviewer);
        $this->assertSame('accessor:NULL', $this->review(2, ['Editors'])->reviewer);
    }

    private function notes()
    {
        ConnectionManager::get('default')->execute(
            'CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY, label_id INTEGER, comment_id INTEGER)'
        );

        return TableRegistry::getTableLocator()->get('Notes');
    }

    public function testChildInitializeReplacesParentAssociationOfSameAlias(): void
    {
        $this->assertSame('label', $this->notes()->getAssociation('Labels')->getProperty());
    }

    public function testQueryMethodReplacesAssociationOfSameAliasWhenItRuns(): void
    {
        $notes = $this->notes();
        $this->assertSame('comment', $notes->getAssociation('Comments')->getProperty());

        $notes->configureComments();
        $this->assertSame('comments', $notes->getAssociation('Comments')->getProperty());
    }
}
