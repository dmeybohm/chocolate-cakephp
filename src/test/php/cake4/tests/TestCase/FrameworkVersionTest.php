<?php
declare(strict_types=1);

namespace App\Test\TestCase;

use Cake\Core\Configure;
use Cake\TestSuite\TestCase;

/**
 * The framework release the suite runs against.
 */
class FrameworkVersionTest extends TestCase
{
    public function testInstalledFrameworkIsThePinnedRelease(): void
    {
        $this->assertSame('4.6.5', Configure::version());
    }
}
