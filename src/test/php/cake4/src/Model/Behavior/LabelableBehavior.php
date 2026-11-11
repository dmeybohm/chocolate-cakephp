<?php
declare(strict_types=1);

namespace App\Model\Behavior;

use Cake\ORM\Behavior;
use Cake\ORM\Query;

/**
 * A behavior with members from a trait, a static method and a finder of more than one word.
 */
class LabelableBehavior extends Behavior
{
    use LabelTrait;

    public static function build(): string
    {
        return 'built';
    }

    public function findTopRated(Query $query): Query
    {
        return $query->applyOptions(['finder' => 'behavior:topRated']);
    }
}
