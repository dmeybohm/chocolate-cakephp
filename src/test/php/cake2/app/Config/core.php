<?php

Configure::write('debug', 2);
Configure::write('Error', array(
	'handler' => 'ErrorHandler::handleError',
	'level' => E_ALL & ~E_DEPRECATED,
	'trace' => true,
));
Configure::write('Exception', array(
	'handler' => 'ErrorHandler::handleException',
	'renderer' => 'ExceptionRenderer',
	'log' => false,
));
Configure::write('Routing.prefixes', array('admin'));
Configure::write('App.encoding', 'UTF-8');
Configure::write('Security.salt', 'assumption-tests-only-assumption-tests-only');
Configure::write('Security.cipherSeed', '7685930965746');
date_default_timezone_set('UTC');

Cache::config('default', array('engine' => 'File'));
Cache::config('_cake_core_', array('engine' => 'File', 'prefix' => 'cake_core_', 'path' => CACHE . 'persistent' . DS, 'duration' => '+1 hour'));
Cache::config('_cake_model_', array('engine' => 'File', 'prefix' => 'cake_model_', 'path' => CACHE . 'models' . DS, 'duration' => '+1 hour'));
