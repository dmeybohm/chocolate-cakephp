<?php
$this->set('shared', 'from outer');
echo $this->element('nested_inner', ['innerLabel' => 'x', 'innerCount' => 2]);
