<?php
declare(strict_types=1);

use Cake\Cache\Cache;
use Cake\Core\Configure;
use Cake\Database\Connection;
use Cake\Database\Driver\Sqlite;
use Cake\Datasource\ConnectionManager;

define('ROOT', dirname(__DIR__));
define('APP_DIR', 'src');
define('APP', ROOT . '/src/');
define('CONFIG', ROOT . '/config/');
define('WWW_ROOT', ROOT . '/webroot/');
define('TESTS', ROOT . '/tests/');
define('TMP', ROOT . '/tmp/');
define('LOGS', ROOT . '/logs/');
define('CACHE', TMP . 'cache/');
define('RESOURCES', ROOT . '/resources/');
define('CAKE_CORE_INCLUDE_PATH', ROOT . '/vendor/cakephp/cakephp');
define('CORE_PATH', CAKE_CORE_INCLUDE_PATH . '/');
define('CAKE', CORE_PATH . 'src/');

require ROOT . '/vendor/autoload.php';
require CORE_PATH . 'config/bootstrap.php';

Configure::write('debug', true);
Configure::write('App', [
    'namespace' => 'App',
    'encoding' => 'UTF-8',
    'defaultLocale' => 'en_US',
    'defaultTimezone' => 'UTC',
    'base' => false,
    'dir' => 'src',
    'webroot' => 'webroot',
    'wwwRoot' => WWW_ROOT,
    'fullBaseUrl' => 'http://localhost',
    'imageBaseUrl' => 'img/',
    'cssBaseUrl' => 'css/',
    'jsBaseUrl' => 'js/',
    'paths' => [
        'plugins' => [ROOT . '/plugins/'],
        'templates' => [ROOT . '/templates/'],
        'locales' => [RESOURCES . 'locales/'],
    ],
]);
Configure::write('Security.salt', 'assumption-tests-only-assumption-tests-only');

foreach (['default', '_cake_core_', '_cake_model_', '_cake_routes_', '_cake_translations_'] as $name) {
    Cache::setConfig($name, ['className' => 'Array']);
}

foreach (['default', 'test'] as $name) {
    ConnectionManager::setConfig($name, [
        'className' => Connection::class,
        'driver' => Sqlite::class,
        'database' => ':memory:',
    ]);
}
