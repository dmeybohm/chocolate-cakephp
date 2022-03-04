<?php

namespace PHPSTORM_META {
    override(\ClassRegistry::init(0), map([
        '' => '@'
    ]));

    expectedArguments(\Model::find(),
        0,
        'first',
        'all',
        'count',
        'list',
        'threaded',
        'neighbors',
    );
}