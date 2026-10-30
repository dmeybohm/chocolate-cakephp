<?php

namespace App\Test\TestCase;

use Cake\Core\Configure;
use Cake\TestSuite\TestCase;

/**
 * The framework release the suite runs against.
 */
class FrameworkVersionTest extends TestCase
{
    public function testInstalledFrameworkIsThePinnedRelease()
    {
        $this->assertSame('3.10.5', Configure::version());
    }
}
