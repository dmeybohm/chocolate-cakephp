<?php

define('DS', DIRECTORY_SEPARATOR);
define('ROOT', dirname(dirname(__DIR__)));
define('APP_DIR', 'app');
define('WEBROOT_DIR', 'webroot');
define('WWW_ROOT', ROOT . DS . APP_DIR . DS . WEBROOT_DIR . DS);
define('CONFIG', ROOT . DS . APP_DIR . DS . 'Config' . DS);
define('CAKE_CORE_INCLUDE_PATH', ROOT . DS . 'vendor' . DS . 'cakephp' . DS . 'cakephp' . DS . 'lib');

foreach (array('cache/persistent', 'cache/models', 'cache/views', 'logs', 'sessions', 'tests') as $dir) {
	if (!is_dir(ROOT . '/app/tmp/' . $dir)) {
		mkdir(ROOT . '/app/tmp/' . $dir, 0777, true);
	}
}

require CAKE_CORE_INCLUDE_PATH . DS . 'Cake' . DS . 'bootstrap.php';
